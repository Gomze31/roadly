package fr.roadly.core.assistant.provider

import kotlinx.coroutines.flow.Flow

enum class AssistantError { NO_KEY, INVALID_KEY, QUOTA, DAILY_CAP, NETWORK, TIMEOUT, PROTOCOL, MIC_UNAVAILABLE, DISABLED }

/** Événements d'une session vocale, dans l'ordre de réception. Les transcriptions sont incrémentales. */
sealed interface AssistantEvent {
    /** Le serveur a accepté la configuration ; interne au fournisseur, jamais transmis au contrôleur. */
    data object Ready : AssistantEvent
    data class UserTranscript(val text: String) : AssistantEvent
    data class AssistantTranscript(val text: String) : AssistantEvent
    class AudioChunk(val pcm24k: ByteArray) : AssistantEvent
    data class ToolCalls(val calls: List<ToolCall>) : AssistantEvent
    data class ToolCallsCancelled(val ids: List<String>) : AssistantEvent
    data object Interrupted : AssistantEvent
    data object TurnComplete : AssistantEvent
    data class ResumptionHandle(val handle: String) : AssistantEvent
    data class GoAway(val timeLeftMs: Long) : AssistantEvent
    data class Usage(val totalTokens: Int) : AssistantEvent
    data class Failed(val error: AssistantError) : AssistantEvent
    data object Closed : AssistantEvent
}

data class SessionConfig(
    val apiKey: String,
    val systemInstruction: String,
    val tools: List<ToolSchema>,
    val voice: String = "Kore",
    val resumptionHandle: String? = null,
)

interface VoiceSession {
    val events: Flow<AssistantEvent>
    /** PCM 16 bits LE, 16 kHz, mono. */
    fun sendAudio(pcm16k: ByteArray)
    /** Micro suspendu (annonce GPS, pause) : le serveur vide son tampon de détection. */
    fun endAudioStream()
    /** Tour texte (ex. résultat d'une puce Confirmer/Annuler) ; interrompt la génération en cours. */
    fun sendText(text: String)
    fun sendToolResults(results: List<ToolResult>)
    fun close()
}

sealed interface Connection {
    data class Open(val session: VoiceSession) : Connection
    data class Refused(val error: AssistantError) : Connection
}

interface VoiceAssistantProvider {
    val id: String
    suspend fun connect(config: SessionConfig): Connection
}
