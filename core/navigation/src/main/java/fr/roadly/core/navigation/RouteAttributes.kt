package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.Route
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

/** Tronçon du tracé dont la limitation (km/h, source OSM `maxspeed` via Valhalla) est connue. */
data class SpeedLimitSpan(val startAlongM: Double, val endAlongM: Double, val kmh: Int)

/** Portique de péage en flux libre (OSM `highway=toll_gantry`) à l'abscisse [alongM] du tracé. */
data class TollGantry(val alongM: Double, val roadName: String?, val countryCode: String?)

/** Prochaine limitation différente de la limitation courante, à [distanceM] devant. */
data class SpeedLimitChange(val kmh: Int, val distanceM: Double)

/**
 * Profil d'un tracé, en abscisses curvilignes de sa géométrie : limitations connues et portiques de péage en flux libre.
 * Obtenu en une requête par tracé (`trace_attributes`, `edge_walk`) : aucune requête n'est faite à chaque position.
 */
data class RouteAttributes(
    val routeId: String,
    val totalM: Double,
    val speedLimits: List<SpeedLimitSpan>,
    val tollGantries: List<TollGantry>,
) {
    /** Part du tracé (0..1) dont la limitation est connue. */
    val coverage: Double get() = if (totalM <= 0.0) 0.0 else (speedLimits.sumOf { it.endAlongM - it.startAlongM } / totalM).coerceIn(0.0, 1.0)

    /** Limitation à l'abscisse [alongM] ; null hors des tronçons connus. */
    fun limitAt(alongM: Double): Int? = spanAt(alongM)?.kmh

    private fun spanAt(alongM: Double): SpeedLimitSpan? {
        var lo = 0
        var hi = speedLimits.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = speedLimits[mid]
            when {
                alongM < s.startAlongM -> hi = mid - 1
                alongM >= s.endAlongM -> lo = mid + 1
                else -> return s
            }
        }
        return null
    }

    /**
     * Première limitation connue différente de celle de [alongM], à moins de [horizonM] devant ; null sinon.
     * Un passage vers une portion à limitation inconnue n'est pas un changement (on n'annonce rien qu'on ne sait pas).
     */
    fun nextChange(alongM: Double, horizonM: Double): SpeedLimitChange? {
        val current = limitAt(alongM)
        for (s in speedLimits) {
            if (s.endAlongM <= alongM) continue
            if (s.startAlongM - alongM > horizonM) return null
            if (s.startAlongM > alongM && s.kmh != current) return SpeedLimitChange(s.kmh, s.startAlongM - alongM)
        }
        return null
    }
}

/** Arête Valhalla réduite à ce qu'utilise le profil. */
data class RouteEdge(val lengthM: Double, val speedLimitKmh: Int?, val endNodeType: String?, val names: List<String>, val countryCode: String?)

object RouteAttributesBuilder {
    /** Une arête sans limitation plus courte que ceci (carrefour, bretelle courte) prolonge la limitation précédente. */
    const val ABSORB_UNKNOWN_M = 40.0
    /** Écart admis entre la longueur Valhalla des arêtes et celle de la géométrie ; au-delà, le profil n'est pas fiable. */
    const val MAX_LENGTH_MISMATCH = 0.05

    /** Null si les arêtes ne correspondent pas à la géométrie (longueurs trop différentes). */
    fun build(routeId: String, geometryTotalM: Double, edges: List<RouteEdge>): RouteAttributes? {
        val edgesTotal = edges.sumOf { it.lengthM }
        if (edgesTotal <= 0.0 || geometryTotalM <= 0.0) return null
        val scale = geometryTotalM / edgesTotal
        if (kotlin.math.abs(scale - 1.0) > MAX_LENGTH_MISMATCH) return null
        val spans = ArrayList<SpeedLimitSpan>()
        val gantries = ArrayList<TollGantry>()
        var at = 0.0
        for (e in edges) {
            val start = at * scale
            val end = (at + e.lengthM) * scale
            at += e.lengthM
            val last = spans.lastOrNull()
            val contiguous = last != null && start - last.endAlongM < 1.0
            when {
                e.speedLimitKmh != null && contiguous && last!!.kmh == e.speedLimitKmh -> spans[spans.lastIndex] = last.copy(endAlongM = end)
                e.speedLimitKmh != null -> spans += SpeedLimitSpan(start, end, e.speedLimitKmh)
                contiguous && e.lengthM < ABSORB_UNKNOWN_M -> spans[spans.lastIndex] = last!!.copy(endAlongM = end)
            }
            if (e.endNodeType == "toll_gantry") gantries += TollGantry(end, e.names.firstOrNull(), e.countryCode)
        }
        return RouteAttributes(routeId, geometryTotalM, spans, gantries)
    }
}

/** Requête et décodage `trace_attributes` du profil (https://valhalla.github.io/valhalla/api/map-matching/api-reference/). */
object ValhallaRouteAttributes {
    val ATTRIBUTES = listOf(
        "edge.length", "edge.speed_limit", "edge.names", "edge.end_node", "node.type", "node.admin_index", "admin.country_code",
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

    fun parse(json: Json, body: String): List<RouteEdge>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val countries = (root["admins"] as? JsonArray).orEmpty().map { a ->
            ((a as? JsonObject)?.get("country_code") as? JsonPrimitive)?.takeIf { it.isString }?.content?.uppercase()?.takeIf { it.isNotBlank() }
        }
        val edges = root["edges"] as? JsonArray ?: return null
        edges.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val km = (o["length"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return@mapNotNull null
            val limit = (o["speed_limit"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toInt()?.takeIf { it in 1..254 }
            val node = o["end_node"] as? JsonObject
            val type = (node?.get("type") as? JsonPrimitive)?.takeIf { it.isString }?.content
            val country = (node?.get("admin_index") as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.let { countries.getOrNull(it) }
            val names = (o["names"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            RouteEdge(km * 1000.0, limit, type, names, country)
        }
    }.getOrNull()
}

/** Profil d'un tracé (limitations, portiques de péage en flux libre). */
interface RouteAttributesProvider {
    suspend fun attributes(route: Route): Outcome<RouteAttributes>
}

object NoRouteAttributesProvider : RouteAttributesProvider {
    override suspend fun attributes(route: Route): Outcome<RouteAttributes> = Outcome.Failure(AppError.NoResult)
}

/**
 * Une requête `trace_attributes` par tracé motorisé, y compris sur l'instance publique : c'est le même volume qu'un calcul
 * d'itinéraire (fair-use), contrairement au suivi de position fix par fix. Les derniers profils sont gardés en mémoire.
 * Les tracés trottinette ont leur propre analyse (`ScooterRouteAnalyzer`) : non traités ici.
 */
@Singleton
class ValhallaRouteAttributesProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : RouteAttributesProvider {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, RouteAttributes>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RouteAttributes>?) = size > CACHE_SIZE
    }

    override suspend fun attributes(route: Route): Outcome<RouteAttributes> {
        if (route.scooterGoal != null || route.geometry.size < 2) return Outcome.Failure(AppError.NoResult)
        mutex.withLock { cache[route.id] }?.let { return Outcome.Success(it) }
        val base = try { config.config.first().valhallaBaseUrl } catch (e: CancellationException) { throw e } catch (e: Exception) {
            return Outcome.Failure(AppError.ProviderUnavailable("valhalla"))
        }
        val url = "${base.trimEnd('/')}/trace_attributes".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("valhalla"))
        val body = ValhallaRouteAttributes.request(Polyline6Encoder.encode(route.geometry))
        val edges = when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> ValhallaRouteAttributes.parse(json, r.value) ?: return Outcome.Failure(AppError.Parse("trace_attributes"))
            is Outcome.Failure -> return r
        }
        val attrs = RouteAttributesBuilder.build(route.id, RouteGeometry(route.geometry).totalM, edges) ?: return Outcome.Failure(AppError.NoResult)
        mutex.withLock { cache[route.id] = attrs }
        return Outcome.Success(attrs)
    }

    private companion object { const val CACHE_SIZE = 6 }
}
