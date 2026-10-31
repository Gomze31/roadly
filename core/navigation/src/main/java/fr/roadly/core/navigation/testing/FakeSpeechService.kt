package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.SpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Double de test de [SpeechService] : enregistre les annonces, le volume, les bips et les arrêts. */
class FakeSpeechService : SpeechService {
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking
    /** Simule le début / la fin d'une annonce GPS. */
    fun setSpeaking(value: Boolean) { _speaking.value = value }

    val spoken = mutableListOf<String>()
    /** `flush` de chaque appel à [speak], dans l'ordre. */
    val flushes = mutableListOf<Boolean>()
    var volume = 100
        private set
    var beeps = 0
        private set
    var stops = 0
        private set
    var muted = false
        private set
    var releases = 0
        private set

    override fun speak(text: String, flush: Boolean) { spoken += text; flushes += flush }
    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) }
    override fun beep() { beeps++ }
    override fun setMuted(muted: Boolean) { this.muted = muted }
    override fun stop() { stops++; _speaking.value = false }
    override fun release() { releases++ }
}
