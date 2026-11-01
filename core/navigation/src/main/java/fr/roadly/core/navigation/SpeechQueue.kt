package fr.roadly.core.navigation

/**
 * File d'annonces du moteur de synthèse (pur, testé sur JVM), un canal par source (E1). Les décisions viennent de [VoiceArbiter] :
 * - le guidage passe d'abord ;
 * - l'assistant ne part que [resumeGapMs] après la fin du dernier énoncé du guidage ;
 * - un URGENT du guidage vide les deux files.
 * Péremption après [maxAgeMs] : LOW / NORMAL du guidage, et tout message de l'assistant.
 */
internal class SpeechQueue(
    private val maxAgeMs: Long = GuidanceConstants.SPEECH_STALE_AFTER_MS,
    private val resumeGapMs: Long = VoiceArbiter.ASSISTANT_RESUME_GAP_MS,
) {
    data class Item(val text: String, val priority: SpeechPriority, val queuedAtMs: Long, val channel: VoiceChannel = VoiceChannel.GUIDANCE)

    private val guidance = ArrayDeque<Item>()
    private val assistant = ArrayDeque<Item>()
    /** Fin du dernier énoncé du guidage : l'assistant repart [resumeGapMs] après. */
    private var guidanceEndedAtMs: Long? = null

    val isEmpty: Boolean get() = guidance.isEmpty() && assistant.isEmpty()

    /**
     * Ajoute [item] alors que [active] est en cours (null si rien). Rend vrai s'il faut couper l'énoncé en cours : URGENT, `flush`, ou guidage
     * sur un message de l'assistant. Les files désignées par l'arbitre sont vidées avant l'ajout, et un message de l'assistant coupé est remis
     * en tête de sa file (âge compté depuis la coupure).
     */
    fun offer(item: Item, flush: Boolean = false, active: Item? = null): Boolean {
        val a = VoiceArbiter.arbitrate(item.channel, item.priority, flush, active?.channel)
        if (VoiceChannel.GUIDANCE in a.cleared) guidance.clear()
        if (VoiceChannel.ASSISTANT in a.cleared) assistant.clear()
        if (a.interrupt && a.requeueInterrupted && active != null) assistant.addFirst(active.copy(queuedAtMs = item.queuedAtMs))
        queueOf(item.channel).addLast(item)
        return a.interrupt
    }

    /** Prochaine annonce à dire à [nowMs] : guidage d'abord ; assistant seulement après le délai de reprise. Les annonces périmées sont jetées. */
    fun poll(nowMs: Long): Item? {
        dropStale(guidance, nowMs)
        guidance.removeFirstOrNull()?.let { return it }
        dropStale(assistant, nowMs)
        if (assistant.isEmpty() || nowMs < assistantReadyAt()) return null
        return assistant.removeFirst()
    }

    /** Instant où le message de l'assistant en tête de file pourra partir (≥ [nowMs]) ; null si rien n'attend ou si du guidage attend. */
    fun readyAtMs(nowMs: Long): Long? {
        if (guidance.isNotEmpty()) return null
        dropStale(assistant, nowMs)
        if (assistant.isEmpty()) return null
        return maxOf(nowMs, assistantReadyAt())
    }

    /** Fin d'un énoncé (terminé ou coupé par un arrêt) : celle du guidage ouvre le délai de reprise de l'assistant. */
    fun onFinished(channel: VoiceChannel, nowMs: Long) {
        if (channel == VoiceChannel.GUIDANCE) guidanceEndedAtMs = nowMs
    }

    /** Un message de l'assistant est en cours ([active]) ou en file : la libération du moteur l'attend (E1). */
    fun hasAssistantWork(active: Item?): Boolean = active?.channel == VoiceChannel.ASSISTANT || assistant.isNotEmpty()

    /** Vide la file de [channel] ; null = les deux. */
    fun clear(channel: VoiceChannel? = null) {
        if (channel != VoiceChannel.ASSISTANT) guidance.clear()
        if (channel != VoiceChannel.GUIDANCE) assistant.clear()
    }

    private fun queueOf(channel: VoiceChannel) = if (channel == VoiceChannel.GUIDANCE) guidance else assistant

    private fun assistantReadyAt(): Long = guidanceEndedAtMs?.let { it + resumeGapMs } ?: Long.MIN_VALUE

    private fun isStale(item: Item, nowMs: Long): Boolean =
        (item.channel == VoiceChannel.ASSISTANT || item.priority != SpeechPriority.URGENT) && nowMs - item.queuedAtMs > maxAgeMs

    private fun dropStale(queue: ArrayDeque<Item>, nowMs: Long) {
        while (true) {
            val head = queue.firstOrNull() ?: return
            if (!isStale(head, nowMs)) return
            queue.removeFirst()
        }
    }
}
