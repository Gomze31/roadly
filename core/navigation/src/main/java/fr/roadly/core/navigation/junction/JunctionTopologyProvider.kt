package fr.roadly.core.navigation.junction

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.Route
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.model.junction.NodeBranch
import fr.roadly.core.model.junction.RoadClass
import fr.roadly.core.model.junction.TopologyEdge
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.scooter.geo.Polyline6Encoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Fenêtre de géométrie (indices inclus de `Route.geometry`) autour d'un carrefour. */
data class JunctionWindow(val fromIndex: Int, val toIndex: Int) {
    val size: Int get() = toIndex - fromIndex + 1

    companion object {
        /** Avant le carrefour : de quoi couvrir l'approche affichée ; après : la sortie et ses premières branches. */
        const val BEFORE_M = 900.0
        const val AFTER_M = 400.0

        /** Fenêtre de [startAlongM] − [BEFORE_M] à [endAlongM] + [AFTER_M], bornée au tracé. */
        fun around(geometry: RouteGeometry, startAlongM: Double, endAlongM: Double = startAlongM): JunctionWindow {
            val cum = geometry.cumulative
            if (cum.isEmpty()) return JunctionWindow(0, 0)
            val lo = (startAlongM - BEFORE_M).coerceAtLeast(0.0)
            val hi = endAlongM + AFTER_M
            var from = 0
            while (from < cum.lastIndex && cum[from + 1] <= lo) from++
            var to = from
            while (to < cum.lastIndex && cum[to] < hi) to++
            return JunctionWindow(from, maxOf(to, minOf(from + 1, cum.lastIndex)))
        }
    }
}

/**
 * Requête et décodage `trace_attributes` d'une fenêtre de carrefour (https://valhalla.github.io/valhalla/api/map-matching/api-reference/).
 * `edge_walk` renvoie la forme envoyée (vérifié sur valhalla1.openstreetmap.de) : `begin_shape_index` est un indice de la fenêtre.
 */
object ValhallaJunctionTopology {
    val ATTRIBUTES = listOf(
        "edge.length", "edge.road_class", "edge.use", "edge.lane_count", "edge.roundabout", "edge.bridge", "edge.tunnel",
        "edge.begin_heading", "edge.begin_shape_index", "edge.end_shape_index",
        "node.intersecting_edge.begin_heading", "node.intersecting_edge.driveability",
        "node.intersecting_edge.road_class", "node.intersecting_edge.use",
    )

    fun request(encodedPolyline6: String, costing: String = "auto"): JsonObject = buildJsonObject {
        put("encoded_polyline", encodedPolyline6)
        put("shape_match", "edge_walk")
        put("costing", costing)
        put("units", "kilometers")
        putJsonObject("filters") {
            putJsonArray("attributes") { ATTRIBUTES.forEach { add(it) } }
            put("action", "include")
        }
    }

    private fun isRamp(use: String?) = use == "ramp" || use == "turn_channel"

    /** Arêtes de la réponse, indices ramenés sur `Route.geometry` par [indexOffset] ; null si la réponse est illisible. */
    fun parse(json: Json, body: String, indexOffset: Int): List<TopologyEdge>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val edges = root["edges"] as? JsonArray ?: return null
        edges.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val begin = o.int("begin_shape_index") ?: return@mapNotNull null
            val end = o.int("end_shape_index") ?: return@mapNotNull null
            val km = o.num("length") ?: return@mapNotNull null
            val node = o["end_node"] as? JsonObject
            val branches = (node?.get("intersecting_edges") as? JsonArray).orEmpty().mapNotNull { b ->
                val bo = b as? JsonObject ?: return@mapNotNull null
                val heading = bo.int("begin_heading") ?: return@mapNotNull null
                // Sans `driveability`, la branche n'est pas carrossable (trottoir, chemin, passage piéton) : ni sortie ni amorce dessinée.
                val drive = bo.str("driveability") ?: return@mapNotNull null
                NodeBranch(
                    headingDeg = ((heading % 360) + 360) % 360,
                    roadClass = RoadClass.fromValhalla(bo.str("road_class")),
                    isRamp = isRamp(bo.str("use")),
                    outbound = drive == "forward" || drive == "both",
                    inbound = drive == "backward" || drive == "both",
                )
            }
            TopologyEdge(
                beginIndex = begin + indexOffset,
                endIndex = end + indexOffset,
                lengthM = km * 1000.0,
                roadClass = RoadClass.fromValhalla(o.str("road_class")),
                isRamp = isRamp(o.str("use")),
                laneCount = o.int("lane_count")?.takeIf { it in 1..12 },
                roundabout = o.bool("roundabout"),
                bridge = o.bool("bridge"),
                tunnel = o.bool("tunnel"),
                branches = branches,
                beginHeadingDeg = o.int("begin_heading")?.let { ((it % 360) + 360) % 360 },
            )
        }
    }.getOrNull()

    private fun JsonObject.prim(k: String) = this[k] as? JsonPrimitive
    private fun JsonObject.int(k: String) = prim(k)?.takeIf { !it.isString }?.intOrNull
    private fun JsonObject.num(k: String) = prim(k)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonObject.str(k: String) = prim(k)?.takeIf { it.isString }?.content
    private fun JsonObject.bool(k: String) = prim(k)?.takeIf { !it.isString }?.booleanOrNull ?: false
}

/** Topologie réelle d'un carrefour de l'itinéraire. */
interface JunctionTopologyProvider {
    suspend fun topology(route: Route, maneuverIndex: Int, window: JunctionWindow): Outcome<JunctionTopology>
}

object NoJunctionTopologyProvider : JunctionTopologyProvider {
    override suspend fun topology(route: Route, maneuverIndex: Int, window: JunctionWindow): Outcome<JunctionTopology> = Outcome.Failure(AppError.NoResult)
}

/**
 * Une petite requête `trace_attributes` (≈ 1,3 km de forme) par carrefour retenu, faite pendant la préparation (à ≈ 800 m ou
 * 30 s) : quelques requêtes par trajet, du même ordre qu'un recalcul. Hors ligne ou en échec, la vue se construit sans topologie
 * (tracé + tuiles). Les dernières topologies sont gardées en mémoire ; deux requêtes sont espacées d'au moins [MIN_INTERVAL_MS].
 */
@Singleton
class ValhallaJunctionTopologyProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : JunctionTopologyProvider {
    private val mutex = Mutex()
    private var lastCallAtMs = 0L
    private val cache = object : LinkedHashMap<String, JunctionTopology>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JunctionTopology>?) = size > CACHE_SIZE
    }

    override suspend fun topology(route: Route, maneuverIndex: Int, window: JunctionWindow): Outcome<JunctionTopology> {
        if (route.scooterGoal != null || window.size < 2) return Outcome.Failure(AppError.NoResult)
        val key = "${route.id}#$maneuverIndex"
        mutex.withLock {
            cache[key]?.let { return Outcome.Success(it) }
            val now = System.currentTimeMillis()
            if (now - lastCallAtMs < MIN_INTERVAL_MS) return Outcome.Failure(AppError.NoResult)
            lastCallAtMs = now
        }
        val base = try { config.config.first().valhallaBaseUrl } catch (e: CancellationException) { throw e } catch (e: Exception) {
            return Outcome.Failure(AppError.ProviderUnavailable("valhalla"))
        }
        val url = "${base.trimEnd('/')}/trace_attributes".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("valhalla"))
        val shape = route.geometry.subList(window.fromIndex, window.toIndex + 1)
        val body = ValhallaJunctionTopology.request(Polyline6Encoder.encode(shape))
        val edges = when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> ValhallaJunctionTopology.parse(json, r.value, window.fromIndex) ?: return Outcome.Failure(AppError.Parse("trace_attributes"))
            is Outcome.Failure -> return r
        }
        if (edges.isEmpty()) return Outcome.Failure(AppError.NoResult)
        val topology = JunctionTopology(route.id, maneuverIndex, edges)
        mutex.withLock { cache[key] = topology }
        return Outcome.Success(topology)
    }

    private companion object {
        const val CACHE_SIZE = 24
        const val MIN_INTERVAL_MS = 4_000L
    }
}
