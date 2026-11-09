package fr.roadly.core.data.traffic

import fr.roadly.core.common.Outcome
import fr.roadly.core.data.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/** Événements de circulation officiels ; interface pour les doubles de test. */
interface TrafficSource {
    /** Événements actifs (cache de [TrafficRepository.MAX_AGE_MS]) ; dernière liste connue si le réseau échoue, vide sinon. */
    suspend fun events(): List<TrafficEvent>
}

/**
 * Bison Futé, événements des DIR sur le réseau routier national (DATEX II, données publiques). ~150 Ko compressés,
 * téléchargés au plus toutes les 5 minutes, seulement quand un écran en a besoin (itinéraires, guidage, Android Auto).
 */
@Singleton
class TrafficRepository @Inject constructor(private val api: ApiClient) : TrafficSource {
    private val mutex = Mutex()
    private var cached: List<TrafficEvent> = emptyList()
    private var fetchedAt = 0L

    override suspend fun events(): List<TrafficEvent> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - fetchedAt < MAX_AGE_MS && fetchedAt != 0L) return cached
        when (val r = api.get(URL.toHttpUrl())) {
            is Outcome.Success -> {
                val parsed = withContext(Dispatchers.Default) { DatexParser.parse(r.value, now) }
                if (parsed.isNotEmpty()) { cached = parsed; fetchedAt = now }
            }
            is Outcome.Failure -> Unit
        }
        return cached
    }

    companion object {
        const val URL = "https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/Evenementiel-DIR/grt/RRN/content.xml"
        const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}
