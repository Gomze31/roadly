package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.SpeechPriority
import fr.roadly.core.navigation.SpeechService
import fr.roadly.core.navigation.VoiceChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Double de test de [SpeechService] : enregistre les annonces (texte, flush, priorité, canal), le volume, les bips, les arrêts et les sourdines. */
class FakeSpeechService : SpeechService {
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking
    /** Simule le début / la fin d'une annonce GPS (même nom que sur `feat/roadly-ai`). */
    fun setSpeaking(value: Boolean) { _speaking.value = value }

    val spoken = mutableListOf<String>()
    /** `flush` de chaque appel à [speak], dans l'ordre. */
    val flushes = mutableListOf<Boolean>()
    /** Priorité de chaque annonce, dans l'ordre de [spoken]. */
    val priorities = mutableListOf<SpeechPriority>()
    /** E1 : canal de chaque annonce, dans l'ordre de [spoken]. */
    val channels = mutableListOf<VoiceChannel>()
    var volume = 100
        private set
    var beeps = 0
        private set
    /** Appels à `stop`, tous canaux confondus. */
    var stops = 0
        private set
    /** E1 : canal de chaque `stop` (null = tous), dans l'ordre. */
    val stoppedChannels = mutableListOf<VoiceChannel?>()
    private val _mutedChannels = mutableSetOf<VoiceChannel>()
    /** E1 : canaux en sourdine. */
    val mutedChannels: Set<VoiceChannel> get() = _mutedChannels.toSet()
    /** Sourdine du guidage. */
    val muted: Boolean get() = VoiceChannel.GUIDANCE in _mutedChannels
    var releases = 0
        private set

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL, VoiceChannel.GUIDANCE)
    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) = speak(text, flush, priority, VoiceChannel.GUIDANCE)
    override fun speak(text: String, flush: Boolean, priority: SpeechPriority, channel: VoiceChannel) {
        spoken += text; flushes += flush; priorities += priority; channels += channel
    }
    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) }
    override fun beep() { beeps++ }
    override fun setMuted(muted: Boolean) { if (muted) _mutedChannels += VoiceChannel.entries else _mutedChannels.clear() }
    override fun setMuted(muted: Boolean, channel: VoiceChannel) { if (muted) _mutedChannels += channel else _mutedChannels -= channel }
    override fun stop() = stop(null)
    override fun stop(channel: VoiceChannel?) { stops++; stoppedChannels += channel; _speaking.value = false }
    override fun release() { releases++ }
}
