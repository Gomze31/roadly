package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.provider.VoiceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Live par WebSocket brut, clé API en paramètre de requête. Client OkHttp dédié, sans intercepteur
 * de journalisation : l'URL contient la clé.
 */
@Singleton
class GeminiLiveProvider @Inject constructor(private val json: Json) : VoiceAssistantProvider {
    override val id = "gemini-live"
    internal var endpoint = ENDPOINT
    internal var setupTimeoutMs = 8_000L
    private val client by lazy { OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build() }

    override suspend fun connect(config: SessionConfig): Connection {
        val session = GeminiVoiceSession(json)
        val request = Request.Builder().url("$endpoint?key=${config.apiKey}").build()
        session.socket = client.newWebSocket(request, session.listener)
        // OkHttp met en file les messages envoyés avant l'ouverture.
        session.socket.send(GeminiLiveProtocol.setup(config))
        val error = try {
            withTimeout(setupTimeoutMs) { session.ready.await() }
        } catch (e: TimeoutCancellationException) {
            AssistantError.TIMEOUT
        }
        return if (error == null) Connection.Open(session) else { session.close(); Connection.Refused(error) }
    }

    companion object {
        const val ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}

internal class GeminiVoiceSession(private val json: Json) : VoiceSession {
    lateinit var socket: WebSocket
    /** Complété à `null` par `setupComplete`, ou par l'erreur qui a fermé la connexion avant. */
    val ready = CompletableDeferred<AssistantError?>()
    private val channel = Channel<AssistantEvent>(Channel.UNLIMITED)
    override val events: Flow<AssistantEvent> = channel.receiveAsFlow()
    @Volatile private var closed = false

    val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
        // Le serveur peut envoyer le JSON en trames binaires.
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            finish(closeError(code, reason))
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(closeError(code, reason))
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish(failureError(t, response?.code))
    }

    private fun handle(text: String) {
        GeminiLiveProtocol.parse(json, text).forEach { e ->
            if (e == AssistantEvent.Ready) ready.complete(null) else channel.trySend(e)
        }
    }

    @Synchronized private fun finish(error: AssistantError?) {
        if (closed) return
        closed = true
        // Fermé avant setupComplete : la connexion a échoué, même sur une fermeture « normale ».
        ready.complete(error ?: AssistantError.NETWORK)
        error?.let { channel.trySend(AssistantEvent.Failed(it)) }
        channel.trySend(AssistantEvent.Closed)
        channel.close()
    }

    override fun sendAudio(pcm16k: ByteArray) { if (!closed) socket.send(GeminiLiveProtocol.audio(pcm16k)) }
    override fun endAudioStream() { if (!closed) socket.send(GeminiLiveProtocol.audioStreamEnd()) }
    override fun sendText(text: String) { if (!closed) socket.send(GeminiLiveProtocol.text(text)) }
    override fun sendToolResults(results: List<ToolResult>) { if (!closed) socket.send(GeminiLiveProtocol.toolResponse(results)) }
    override fun close() {
        if (closed) return
        socket.close(1000, null)
        finish(null)
    }
}

internal fun closeError(code: Int, reason: String): AssistantError? = when {
    code == 1000 -> null
    listOf("API key", "PERMISSION_DENIED", "UNAUTHENTICATED").any { reason.contains(it, ignoreCase = true) } -> AssistantError.INVALID_KEY
    reason.contains("RESOURCE_EXHAUSTED") || reason.contains("quota", ignoreCase = true) -> AssistantError.QUOTA
    code == 1007 || code == 1008 -> AssistantError.PROTOCOL
    else -> AssistantError.NETWORK
}

internal fun failureError(t: Throwable, httpCode: Int?): AssistantError = when {
    httpCode == 401 || httpCode == 403 -> AssistantError.INVALID_KEY
    httpCode == 429 -> AssistantError.QUOTA
    t is SocketTimeoutException -> AssistantError.TIMEOUT
    else -> AssistantError.NETWORK
}
