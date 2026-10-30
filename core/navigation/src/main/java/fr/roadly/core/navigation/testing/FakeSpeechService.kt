package fr.roadly.core.navigation.testing

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

    override fun speak(text: String, flush: Boolean) { spoken += text }
    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) }
    override fun beep() { beeps++ }
    override fun stop() { stops++ }
}
