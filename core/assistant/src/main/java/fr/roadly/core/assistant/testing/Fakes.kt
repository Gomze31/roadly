package fr.roadly.core.assistant.testing

import fr.roadly.core.assistant.audio.AudioFocus
import fr.roadly.core.assistant.audio.AudioInput
import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.audio.FocusChange
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.provider.VoiceSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Double de test de [VoiceSession] : [emit] pousse un événement serveur, les envois sont enregistrés. */
class FakeVoiceSession : VoiceSession {
    private val channel = Channel<AssistantEvent>(Channel.UNLIMITED)
    override val events: Flow<AssistantEvent> = channel.receiveAsFlow()
    val audioFrames = mutableListOf<ByteArray>()
    var streamEnds = 0
    val texts = mutableListOf<String>()
    val toolResults = mutableListOf<ToolResult>()
    var closed = false
    fun emit(event: AssistantEvent) { channel.trySend(event) }
    override fun sendAudio(pcm16k: ByteArray) { audioFrames += pcm16k }
    override fun endAudioStream() { streamEnds++ }
    override fun sendText(text: String) { texts += text }
    override fun sendToolResults(results: List<ToolResult>) { toolResults += results }
    override fun close() {
        if (closed) return
        closed = true
        channel.trySend(AssistantEvent.Closed)
        channel.close()
    }
}

/** Chaque [connect] consomme le prochain résultat de [results] ; à défaut, ouvre une nouvelle [FakeVoiceSession]. */
class FakeVoiceProvider(private val results: ArrayDeque<Connection> = ArrayDeque()) : VoiceAssistantProvider {
    override val id = "fake"
    val configs = mutableListOf<SessionConfig>()
    val sessions = mutableListOf<FakeVoiceSession>()
    override suspend fun connect(config: SessionConfig): Connection {
        configs += config
        return results.removeFirstOrNull() ?: Connection.Open(FakeVoiceSession().also { sessions += it })
    }
}

/** Micro simulé : [push] délivre une trame comme le ferait la capture. */
class FakeAudioInput : AudioInput {
    override val level: StateFlow<Float> = MutableStateFlow(0f)
    var startResult = true
    var running = false
    private var onFrame: ((ByteArray) -> Unit)? = null
    override fun start(onFrame: (ByteArray) -> Unit): Boolean {
        this.onFrame = onFrame
        running = startResult
        return startResult
    }
    override fun stop() { running = false; onFrame = null }
    fun push(frame: ByteArray) { onFrame?.invoke(frame) }
}

/** Haut-parleur simulé : la file reste « en lecture » jusqu'à [drain] ou [flush]. */
class FakeAudioOutput : AudioOutput {
    override val level: StateFlow<Float> = MutableStateFlow(0f)
    override val playing = MutableStateFlow(false)
    val chunks = mutableListOf<ByteArray>()
    var paused = false
    var flushes = 0
    var stopped = false
    override fun start() { stopped = false }
    override fun enqueue(pcm: ByteArray) { chunks += pcm; playing.value = true }
    override fun pause() { paused = true }
    override fun resume() { paused = false }
    override fun flush() { flushes++; playing.value = false }
    override fun stop() { stopped = true; playing.value = false }
    /** Simule la fin de lecture de la file. */
    fun drain() { playing.value = false }
}

/** Focus audio simulé : [trigger] rejoue un changement de focus système. */
class FakeAudioFocus : AudioFocus {
    var held = false
    private var onChange: ((FocusChange) -> Unit)? = null
    override fun acquire(onChange: (FocusChange) -> Unit): Boolean { this.onChange = onChange; held = true; return true }
    override fun release() { held = false; onChange = null }
    fun trigger(change: FocusChange) { onChange?.invoke(change) }
}
