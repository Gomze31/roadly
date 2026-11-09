package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import kotlinx.coroutines.CancellationException
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
 * Une requête Overpass par tracé (par paquets de 400 voies, seulement les voies lentes) : même volume que l'analyse des voies du
 * mode trottinette, jamais à chaque position. Les derniers résultats sont gardés en mémoire.
 */
@Singleton
class OverpassSpeedBumpProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : SpeedBumpProvider {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, List<Double>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Double>>?) = size > 6
    }

    override suspend fun bumps(route: Route, attributes: RouteAttributes): Outcome<List<Double>> {
        if (attributes.slowWayIds.isEmpty()) return Outcome.Success(emptyList())
        mutex.withLock { cache[route.id] }?.let { return Outcome.Success(it) }
        val base = try { config.config.first().overpassBaseUrl } catch (e: CancellationException) { throw e } catch (e: Exception) {
            return Outcome.Failure(AppError.ProviderUnavailable("overpass"))
        }
        val url = "${base.trimEnd('/')}/interpreter".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("overpass"))
        val nodes = ArrayList<GeoPoint>()
        for (chunk in attributes.slowWayIds.take(MAX_WAYS).chunked(SpeedBumpQuery.MAX_WAY_IDS)) {
            when (val r = api.postForm(url, mapOf("data" to SpeedBumpQuery.query(chunk)))) {
                is Outcome.Success -> nodes += SpeedBumpQuery.parse(json, r.value) ?: return Outcome.Failure(AppError.Parse("overpass"))
                is Outcome.Failure -> return r
            }
        }
        val along = SpeedBumpQuery.along(route.geometry, nodes)
        mutex.withLock { cache[route.id] = along }
        return Outcome.Success(along)
    }

    private companion object { const val MAX_WAYS = 1_200 }
}
