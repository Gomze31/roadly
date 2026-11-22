package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Ralentisseurs d'un tracé (abscisses le long de sa géométrie). */
interface SpeedBumpProvider {
    suspend fun bumps(route: Route, attributes: RouteAttributes): Outcome<List<Double>>
}

object NoSpeedBumpProvider : SpeedBumpProvider {
    override suspend fun bumps(route: Route, attributes: RouteAttributes): Outcome<List<Double>> = Outcome.Success(emptyList())
}

/** Requête et lecture Overpass : nœuds `traffic_calming` des voies parcourues, projetés sur le tracé. */
object SpeedBumpQuery {
    /** Types qui obligent à ralentir (dos-d'âne, ralentisseur long, plateau, coussin berlinois, non précisé). */
    val KINDS = listOf("bump", "hump", "table", "cushion", "yes")
    const val MAX_WAY_IDS = 400
    /** Un nœud plus loin du tracé appartient à une autre voie (rue parallèle, bretelle). */
    const val MAX_OFFSET_M = 15.0
    /** Nœuds d'un même ralentisseur (un par voie de circulation) : fusionnés. */
    const val MERGE_M = 20.0

    fun query(wayIds: Collection<Long>): String =
        "[out:json][timeout:25];way(id:${wayIds.joinToString(",")});node(w)[traffic_calming~\"^(${KINDS.joinToString("|")})$\"];out skel;"

    fun parse(json: Json, body: String): List<GeoPoint>? = runCatching {
        val elements = json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray ?: return null
        elements.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            GeoPoint(lat, lon)
        }
    }.getOrNull()

    /** Abscisses des ralentisseurs proches du tracé, triées et fusionnées. */
    fun along(geometry: List<GeoPoint>, nodes: List<GeoPoint>): List<Double> {
        if (geometry.size < 2) return emptyList()
        val geo = RouteGeometry(geometry)
        val out = ArrayList<Double>()
        nodes.mapNotNull { geo.project(it)?.takeIf { p -> p.offsetM <= MAX_OFFSET_M }?.alongM }.sorted().forEach {
            if (out.isEmpty() || it - out.last() > MERGE_M) out += it
        }
        return out
    }
}

/**
 * Requêtes Overpass par tracé (par paquets de 400 voies, seulement les voies lentes), jamais à chaque position. L'instance
 * publique n'est pas faite pour servir de serveur à une application (voir `docs/API-PROVIDERS.md`) : le volume est donc borné ici.
 * - Une seule requête en vol, et au moins [MIN_REQUEST_INTERVAL_MS] entre la fin d'une requête et le début de la suivante.
 * - Zone déjà lue : les voies déjà interrogées et leurs ralentisseurs sont gardés ; un tracé dont toutes les voies lentes sont
 *   connues (recalcul sur le même couloir) ne coûte aucune requête, un tracé partiellement connu ne demande que les voies
 *   manquantes.
 * - Serveur qui demande de ralentir (HTTP 429, 5xx dont 504, délai dépassé) : plus aucune requête pendant [BACKOFF_MS],
 *   doublé à chaque échec de suite jusqu'à [BACKOFF_MAX_MS] ; le tracé suivant n'est pas une nouvelle tentative.
 * - Un tracé dépassé par un plus récent pendant l'attente n'est pas demandé.
 */
@Singleton
class OverpassSpeedBumpProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
    private val clock: NavigationClock,
) : SpeedBumpProvider {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, List<Double>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Double>>?) = size > 6
    }
    /** Rang de la dernière demande reçue (sous [mutex]). */
    private var newest = 0L

    /** Une seule requête en vol ; protège aussi tout l'état ci-dessous. */
    private val fetch = Mutex()
    private val knownWays = HashSet<Long>()
    /**
     * Un paquet lu : ses voies et les ralentisseurs rendus pour elles. Un tracé ne reçoit que les ralentisseurs des paquets
     * qui contiennent l'une de ses voies : ceux d'un autre trajet, sur une rue parallèle à moins de [SpeedBumpQuery.MAX_OFFSET_M],
     * ne lui sont pas attribués. Limite : la réponse ne dit pas à quelle voie d'un paquet appartient un nœud, donc deux rues
     * parallèles lues dans le même paquet restent indiscernables (c'était déjà le cas au sein d'un même tracé).
     */
    private class Packet(val ways: Set<Long>, val nodes: List<GeoPoint>)
    private val packets = ArrayList<Packet>()
    private var lastRequestEndMs: Long? = null
    private var pausedUntilMs: Long? = null
    private var failuresInARow = 0

    override suspend fun bumps(route: Route, attributes: RouteAttributes): Outcome<List<Double>> {
        if (attributes.slowWayIds.isEmpty()) return Outcome.Success(emptyList())
        val wanted = attributes.slowWayIds.take(MAX_WAYS)
        val mine = mutex.withLock {
            cache[route.id]?.let { return Outcome.Success(it) }
            ++newest
        }
        return fetch.withLock {
            // Mémoire bornée : au-delà, la zone connue repart du tracé courant.
            if (knownWays.size > MAX_KNOWN_WAYS) { knownWays.clear(); packets.clear() }
            val missing = wanted.filterNot { it in knownWays }
            if (missing.isNotEmpty()) {
                val base = try { config.config.first().overpassBaseUrl } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    return Outcome.Failure(AppError.ProviderUnavailable("overpass"))
                }
                val url = "${base.trimEnd('/')}/interpreter".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("overpass"))
                for (chunk in missing.chunked(SpeedBumpQuery.MAX_WAY_IDS)) {
                    awaitSlot()?.let { return Outcome.Failure(it) }
                    if (mutex.withLock { mine != newest }) return Outcome.Failure(AppError.NoResult)
                    // Pas de nouvelle tentative du client HTTP après un 429 : le retrait ci-dessous commence dès la première réponse.
                    val r = try { api.postForm(url, mapOf("data" to SpeedBumpQuery.query(chunk)), retryRateLimited = false) } finally { lastRequestEndMs = clock.elapsedMs() }
                    when (r) {
                        is Outcome.Success -> {
                            val nodes = SpeedBumpQuery.parse(json, r.value) ?: return Outcome.Failure(AppError.Parse("overpass"))
                            knownWays += chunk; packets += Packet(chunk.toHashSet(), nodes)
                            failuresInARow = 0
                        }
                        is Outcome.Failure -> { backOffAfter(r.error); return r }
                    }
                }
            }
            val wantedWays = wanted.toHashSet()
            val nodes = packets.filter { p -> p.ways.any(wantedWays::contains) }.flatMapTo(LinkedHashSet()) { it.nodes }
            val along = SpeedBumpQuery.along(route.geometry, nodes.toList())
            mutex.withLock { cache[route.id] = along }
            Outcome.Success(along)
        }
    }

    /** Attend le prochain créneau ; pendant un retrait demandé par le serveur, rend l'erreur sans attendre ni rien envoyer. */
    private suspend fun awaitSlot(): AppError? {
        val now = clock.elapsedMs()
        pausedUntilMs?.let { if (now < it) return AppError.RateLimited }
        val last = lastRequestEndMs ?: return null
        val wait = (last + MIN_REQUEST_INTERVAL_MS - now).coerceIn(0L, MIN_REQUEST_INTERVAL_MS)
        if (wait > 0L) delay(wait)
        return null
    }

    private fun backOffAfter(e: AppError) {
        val slowDown = e == AppError.RateLimited || e == AppError.Timeout || (e is AppError.ProviderUnavailable && (e.httpCode ?: 0) in 500..599)
        if (!slowDown) return
        val pause = (BACKOFF_MS shl failuresInARow.coerceAtMost(8)).coerceAtMost(BACKOFF_MAX_MS)
        failuresInARow++
        pausedUntilMs = clock.elapsedMs() + pause
    }

    companion object {
        private const val MAX_WAYS = 1_200
        /** Voies gardées dans la zone déjà lue (≈ 4 tracés complets). */
        private const val MAX_KNOWN_WAYS = 5_000
        /** Écart minimal entre deux requêtes Overpass de ralentisseurs. */
        const val MIN_REQUEST_INTERVAL_MS = 10_000L
        /** Retrait après un 429, un 5xx (504 : serveur surchargé) ou un délai dépassé. */
        const val BACKOFF_MS = 60_000L
        const val BACKOFF_MAX_MS = 900_000L
    }
}
