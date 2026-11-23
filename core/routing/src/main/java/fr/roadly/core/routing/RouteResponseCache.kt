package fr.roadly.core.routing

/**
 * Petit cache mémoire des réponses `/route` : relancer la même recherche (même URL, même corps) dans la minute ne sollicite
 * pas le serveur une seconde fois. Seules les réponses réussies y entrent (l'appelant n'y met jamais une erreur) ; les
 * recalculs avec cap n'y passent pas ([ValhallaRoutingProvider]).
 *
 * Borné en durée ([ttlMs]), en nombre ([maxEntries]) et en taille par réponse ([maxEntryChars]). [nowMs] : horloge monotone.
 */
internal class RouteResponseCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxEntryChars: Int = DEFAULT_MAX_ENTRY_CHARS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private class Entry(val body: String, val storedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    @Synchronized fun get(key: String): String? {
        val e = entries[key] ?: return null
        if (nowMs() - e.storedAtMs >= ttlMs) { entries.remove(key); return null }
        return e.body
    }

    @Synchronized fun put(key: String, body: String) {
        if (body.length > maxEntryChars) return
        entries[key] = Entry(body, nowMs())
    }

    companion object {
        const val DEFAULT_TTL_MS = 60_000L
        const val DEFAULT_MAX_ENTRIES = 6
        /** Une réponse plus longue (très long trajet) n'est pas gardée en mémoire. */
        const val DEFAULT_MAX_ENTRY_CHARS = 1_000_000

        fun key(url: String, body: String): String = "$url\n$body"
    }
}
