package fr.roadly.core.navigation

/** Priorité d'une annonce (B5) : URGENT (« maintenant ») vide la file ; LOW / NORMAL périment après 8 s d'attente. */
enum class SpeechPriority { URGENT, NORMAL, LOW }

/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
    /**
     * Variante priorisée (B5). Par défaut : URGENT interrompt (flush), sinon mise en file. Une implémentation qui fait déléguer
     * `speak(text, flush)` à cette variante doit la redéfinir (sinon les deux s'appellent mutuellement).
     */
    fun speak(text: String, flush: Boolean = false, priority: SpeechPriority) = speak(text, flush || priority == SpeechPriority.URGENT)
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
