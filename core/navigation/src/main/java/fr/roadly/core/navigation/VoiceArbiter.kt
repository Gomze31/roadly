package fr.roadly.core.navigation

/** Effet de l'arrivée d'une annonce sur l'énoncé en cours et sur les files (E1). */
internal data class Arbitration(
    /** L'énoncé en cours est coupé. */
    val interrupt: Boolean,
    /** L'énoncé coupé (de l'assistant) est remis en tête de sa file, pour être redit en entier après le guidage. */
    val requeueInterrupted: Boolean,
    /** Files vidées avant l'ajout. */
    val cleared: Set<VoiceChannel>,
)

/**
 * Arbitrage des canaux de parole (E1, pur, testé sur JVM) : urgence GPS > guidage > assistant.
 * - URGENT du guidage (NOW, arrivée) : coupe l'énoncé en cours, quel qu'il soit, et vide les deux files ; la phrase de l'assistant est coupée, pas mise en pause.
 * - Autre annonce du guidage : coupe un message de l'assistant, redit en entier après le guidage ; `flush` vide en plus la file du guidage.
 * - Assistant : ne coupe jamais le guidage ; `flush` ne vide que sa file et ne coupe qu'un message de l'assistant.
 * LIMITE : `TextToSpeech` ne reprend pas au milieu d'une phrase ; un message préempté est redit depuis le début.
 */
internal object VoiceArbiter {
    /** Silence (ms) entre la fin du dernier énoncé du guidage et la reprise de l'assistant (`RESUME_AFTER_GUIDANCE_MS` de l'assistant vocal). */
    const val ASSISTANT_RESUME_GAP_MS = 300L

    /** Fin de course pendant un message de l'assistant : la libération du moteur attend qu'il se taise, au plus 10 s. */
    const val RELEASE_DEFER_MAX_MS = 10_000L

    fun arbitrate(channel: VoiceChannel, priority: SpeechPriority, flush: Boolean, active: VoiceChannel?): Arbitration = when (channel) {
        VoiceChannel.GUIDANCE -> when {
            priority == SpeechPriority.URGENT -> Arbitration(interrupt = true, requeueInterrupted = false, cleared = setOf(VoiceChannel.GUIDANCE, VoiceChannel.ASSISTANT))
            flush -> Arbitration(interrupt = true, requeueInterrupted = active == VoiceChannel.ASSISTANT, cleared = setOf(VoiceChannel.GUIDANCE))
            else -> (active == VoiceChannel.ASSISTANT).let { overAssistant -> Arbitration(interrupt = overAssistant, requeueInterrupted = overAssistant, cleared = emptySet()) }
        }
        VoiceChannel.ASSISTANT -> Arbitration(
            interrupt = flush && active == VoiceChannel.ASSISTANT,
            requeueInterrupted = false,
            cleared = if (flush) setOf(VoiceChannel.ASSISTANT) else emptySet(),
        )
    }
}
