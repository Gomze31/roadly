package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.SpeechPriority
import fr.roadly.core.navigation.SpeechService

/** Double de test de [SpeechService] : enregistre les annonces, le volume, les bips et les arrêts. */
class FakeSpeechService : SpeechService {
    val spoken = mutableListOf<String>()
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

    /** Priorité de chaque annonce, dans l'ordre de [spoken]. */
    val priorities = mutableListOf<SpeechPriority>()

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL)
    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) { spoken += text; priorities += priority }
    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) }
    override fun beep() { beeps++ }
    override fun setMuted(muted: Boolean) { this.muted = muted }
    override fun stop() { stops++ }
    override fun release() { releases++ }
}
