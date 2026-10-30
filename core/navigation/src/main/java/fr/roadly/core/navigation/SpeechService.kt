package fr.roadly.core.navigation

/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
    /** 0–100, appliqué via `TextToSpeech.Engine.KEY_PARAM_VOLUME`. */
    fun setVolume(percent: Int)
    /** Son court d'alerte de dépassement. */
    fun beep()
    /**
     * Sourdine : à `true`, coupe la parole en cours et vide la file ([stop]) ; tant qu'elle est active,
     * [speak] ne fait rien (ni file, ni focus audio).
     */
    fun setMuted(muted: Boolean)
    /** Coupe la parole en cours et vide la file. */
    fun stop()
    /** Libère le moteur de synthèse (et le focus audio) ; il sera recréé au prochain [speak]. */
    fun release()
}
