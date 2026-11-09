package fr.roadly.core.navigation

import kotlinx.coroutines.flow.StateFlow

/** Priorité d'une annonce (B5) : URGENT (« maintenant ») vide la file ; LOW / NORMAL périment après 8 s d'attente. */
enum class SpeechPriority { URGENT, NORMAL, LOW }

/**
 * Canal d'une annonce (E1) : le guidage (session) ou l'assistant (l'assistant vocal, essai de voix des Réglages). Un seul moteur de parole :
 * urgence GPS > guidage > assistant ; l'arrêt et la sourdine de la session ne touchent que [GUIDANCE].
 */
enum class VoiceChannel { GUIDANCE, ASSISTANT }

/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /**
     * Vrai tant qu'une annonce est en cours ou en file, tous canaux confondus (focus audio tenu) : observé par l'assistant pour lui céder la
     * parole. Même nom et même sens que sur `feat/roadly-ai`.
     */
    val speaking: StateFlow<Boolean>
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
    /**
     * Variante priorisée (B5). Par défaut : URGENT interrompt (flush), sinon mise en file. Une implémentation qui fait déléguer
     * `speak(text, flush)` à cette variante doit la redéfinir (sinon les deux s'appellent mutuellement).
     */
    fun speak(text: String, flush: Boolean = false, priority: SpeechPriority) = speak(text, flush || priority == SpeechPriority.URGENT)
    /**
     * E1 : variante par canal. [flush] sur [VoiceChannel.ASSISTANT] ne vide que la file de l'assistant et ne coupe jamais le guidage.
     * Par défaut le canal est ignoré (variante priorisée) ; une implémentation qui y fait déléguer les précédentes doit la redéfinir.
     */
    fun speak(text: String, flush: Boolean = false, priority: SpeechPriority = SpeechPriority.NORMAL, channel: VoiceChannel = VoiceChannel.GUIDANCE) =
        speak(text, flush, priority)
    /** 0–100, appliqué via `TextToSpeech.Engine.KEY_PARAM_VOLUME`. */
    fun setVolume(percent: Int)
    /** Son court d'alerte de dépassement. */
    fun beep()
    /**
     * Sourdine de tous les canaux : à `true`, coupe la parole en cours et vide les files ([stop]) ; tant qu'elle est active,
     * [speak] ne fait rien (ni file, ni focus audio).
     */
    fun setMuted(muted: Boolean)
    /** E1 : sourdine d'un seul canal (la session : [VoiceChannel.GUIDANCE]) ; à `true`, coupe et vide ce canal seulement. Par défaut : tous. */
    fun setMuted(muted: Boolean, channel: VoiceChannel) = setMuted(muted)
    /** Coupe la parole en cours et vide les files, tous canaux (volume à zéro, sourdine globale). */
    fun stop()
    /** E1 : coupe et vide un seul canal ([channel] null = tous) ; un message de l'autre canal n'est ni coupé ni effacé. Par défaut : [stop]. */
    fun stop(channel: VoiceChannel?) = stop()
    /**
     * Libère le moteur de synthèse (et le focus audio) ; il sera recréé au prochain [speak]. E1 : différée tant qu'un message de l'assistant
     * est en cours ou en file (au plus [VoiceArbiter.RELEASE_DEFER_MAX_MS]) ; le guidage, lui, se tait aussitôt.
     */
    fun release()
}
