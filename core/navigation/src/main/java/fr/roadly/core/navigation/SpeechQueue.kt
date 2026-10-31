package fr.roadly.core.navigation

/** File d'annonces du moteur de synthèse (pur, testé sur JVM) : un URGENT vide la file et interrompt ; LOW / NORMAL périment après 8 s. */
internal class SpeechQueue(private val maxAgeMs: Long = GuidanceConstants.SPEECH_STALE_AFTER_MS) {
    data class Item(val text: String, val priority: SpeechPriority, val queuedAtMs: Long)

    private val items = ArrayDeque<Item>()

    val isEmpty: Boolean get() = items.isEmpty()

    /** Ajoute [item] ; rend vrai s'il faut couper l'annonce en cours (URGENT ou [flush]) — la file est alors vidée avant l'ajout. */
    fun offer(item: Item, flush: Boolean = false): Boolean {
        val interrupt = flush || item.priority == SpeechPriority.URGENT
        if (interrupt) items.clear()
        items.addLast(item)
        return interrupt
    }

    /** Prochaine annonce encore valable à [nowMs] ; les LOW / NORMAL en attente depuis plus de [maxAgeMs] sont jetées. */
    fun poll(nowMs: Long): Item? {
        while (true) {
            val head = items.removeFirstOrNull() ?: return null
            if (head.priority == SpeechPriority.URGENT || nowMs - head.queuedAtMs <= maxAgeMs) return head
        }
    }

    fun clear() = items.clear()
}
