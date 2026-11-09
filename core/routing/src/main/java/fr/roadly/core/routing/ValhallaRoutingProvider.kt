package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.StreetSide
import fr.roadly.core.model.VehicleType
import fr.roadly.core.scooter.routing.ScooterRoutingProfile
import fr.roadly.core.scooter.valhalla.TraceAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Valhalla — https://valhalla.github.io/valhalla/api/turn-by-turn/api-reference/ . Instance publique FOSSGIS en dev ; auto-hébergée en prod. */
@Singleton
class ValhallaRoutingProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : RoutingProvider {
    override val id = "valhalla"

    override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
        val motor = request.scooter == null && !request.pedestrian
        val p = request.preferences
        val first = routeOnce(request, excludeTunnels = p.avoidTunnels && motor, excludeUnpaved = p.avoidUnpaved && motor)
        // Tunnels et chemins non revêtus sont des exclusions strictes : sans chemin possible, on relance sans elles (passage inévitable).
        if (first is Outcome.Failure && first.error == AppError.NoResult && motor && (p.avoidTunnels || p.avoidUnpaved)) {
            return routeOnce(request, excludeTunnels = false, excludeUnpaved = false)
        }
        return first
    }

    private suspend fun routeOnce(request: RouteRequest, excludeTunnels: Boolean, excludeUnpaved: Boolean = false): Outcome<List<Route>> {
        // Lecture de la configuration (DataStore) : une IOException/CorruptionException ne doit jamais s'échapper.
        val base = try {
            config.config.first().valhallaBaseUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Outcome.Failure(AppError.ProviderUnavailable(id))
        }
        // URL de base surchargeable par l'utilisateur : ne jamais lever d'exception si elle est invalide.
        val url = "$base/route".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val heading = request.originHeadingDeg
        val body = buildJsonObject {
            putJsonArray("locations") {
                (listOf(request.origin) + request.waypoints + request.destination).forEachIndexed { i, p ->
                    addJsonObject {
                        put("lat", p.lat); put("lon", p.lon)
                        // Recalcul en mouvement : le cap du premier point (tolérance 45°) évite un demi-tour imposé.
                        if (i == 0 && heading != null) {
                            put("heading", Math.floorMod(heading.toInt(), 360))
                            put("heading_tolerance", 45)
                        }
                    }
                }
            }
            if (request.excludePolygons.isNotEmpty()) {
                putJsonArray("exclude_polygons") {
                    request.excludePolygons.forEach { ring -> add(JsonArray(ring.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) })) }
                }
            }
            if (request.pedestrian) {
                // À pied : trottoirs, chemins et passages piétons ; ni péage ni autoroute n'ont de sens.
                put("costing", PEDESTRIAN_COSTING)
                put("units", "kilometers")
                put("language", "fr-FR")
                return@buildJsonObject
            }
            val scooter = request.scooter
            if (scooter != null) {
                // Trottinette : costing vélo paramétré (fr.roadly.core.scooter.routing.ScooterRoutingProfile) + profil d'altitude.
                put("costing", ScooterRoutingProfile.COSTING)
                putJsonObject("costing_options") { put(ScooterRoutingProfile.COSTING, TraceAttributes.mapToJson(ScooterRoutingProfile.costingOptions(scooter))) }
                put("elevation_interval", ELEVATION_INTERVAL_M)
                put("units", "kilometers")
                put("language", "fr-FR")
                return@buildJsonObject
            }
            val costingName = costingFor(request.vehicle?.type)
            put("costing", costingName)
            putJsonObject("costing_options") {
                putJsonObject(costingName) {
                    val prefs = request.preferences
                    if (request.costing == RouteCosting.SHORTEST) put("shortest", true)
                    // 0 = éviter autant que possible (sémantique Valhalla) ; appliqué aux 3 variantes et au recalcul.
                    if (request.costing == RouteCosting.NO_TOLL || prefs.avoidTolls) put("use_tolls", 0)
                    if (prefs.avoidHighways) put("use_highways", 0)
                    if (prefs.avoidFerries) put("use_ferry", 0)
                    if (excludeTunnels) put("exclude_tunnels", true)
                    if (excludeUnpaved) put("exclude_unpaved", true)
                    // Gabarit : seulement en costing poids lourd, et seulement les valeurs saisies (Valhalla garde ses défauts sinon).
                    val d = request.vehicle?.dimensions
                    if (costingName == "truck" && d != null) {
                        d.heightM?.let { put("height", it) }
                        d.widthM?.let { put("width", it) }
                        d.lengthM?.let { put("length", it) }
                        d.weightT?.let { put("weight", it) }
                        d.axles?.let { put("axle_count", it) }
                    }
                }
            }
            put("units", "kilometers")
            put("language", "fr-FR")
        }
        return when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> parse(r.value, request.costing).let { out ->
                if (out is Outcome.Success && request.scooter != null) Outcome.Success(out.value.map { it.copy(scooterGoal = request.scooter!!.goal) }) else out
            }
            is Outcome.Failure -> Outcome.Failure(mapError(r.error))
        }
    }

    companion object {
        /** Pas du profil d'altitude demandé pour les itinéraires trottinette (valeur recommandée par Valhalla). */
        const val ELEVATION_INTERVAL_M = 30
        /** Costing Valhalla du trajet à pied (retour à la voiture garée). */
        const val PEDESTRIAN_COSTING = "pedestrian"
    }

    /** Costing Valhalla selon le type de véhicule : poids lourd (gabarit, interdictions PL), moto, taxi (voies réservées si taguées), sinon auto. */
    internal fun costingFor(type: VehicleType?): String = when (type) {
        VehicleType.TRUCK -> "truck"
        VehicleType.MOTORCYCLE -> "motorcycle"
        VehicleType.TAXI -> "taxi"
        else -> "auto"
    }

    /** `/optimized_route` : l'ordre renvoyé (`original_index`) garde le premier et le dernier point fixes. */
    override suspend fun optimizeOrder(origin: GeoPoint, stops: List<GeoPoint>, destination: GeoPoint, preferences: RoutePreferences): Outcome<List<Int>> {
        if (stops.size < 2) return Outcome.Success(stops.indices.toList())
        val base = try { config.config.first().valhallaBaseUrl } catch (e: CancellationException) { throw e } catch (e: Exception) { return Outcome.Failure(AppError.ProviderUnavailable(id)) }
        val url = "$base/optimized_route".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val body = buildJsonObject {
            putJsonArray("locations") { (listOf(origin) + stops + destination).forEach { p -> addJsonObject { put("lat", p.lat); put("lon", p.lon) } } }
            put("costing", "auto")
            putJsonObject("costing_options") {
                putJsonObject("auto") {
                    if (preferences.avoidTolls) put("use_tolls", 0)
                    if (preferences.avoidHighways) put("use_highways", 0)
                    if (preferences.avoidFerries) put("use_ferry", 0)
                }
            }
            put("units", "kilometers")
        }
        return when (val r = api.postJson(url, body.toString())) {
            is Outcome.Failure -> Outcome.Failure(mapError(r.error))
            is Outcome.Success -> try {
                val order = json.decodeFromString(ValhallaResponse.serializer(), r.value).trip.locations
                    .mapNotNull { it.originalIndex }
                    .filter { it in 1..stops.size }
                    .map { it - 1 }
                if (order.toSet() == stops.indices.toSet()) Outcome.Success(order) else Outcome.Failure(AppError.Parse("optimized_route"))
            } catch (e: Exception) {
                Outcome.Failure(AppError.Parse(e.message ?: "valhalla"))
            }
        }
    }

    private fun mapError(e: AppError): AppError {
        if (e is AppError.Http && e.code == 400) {
            val code = runCatching { json.decodeFromString(ValhallaError.serializer(), e.body).errorCode }.getOrNull()
            // 171 = pas d'arête proche, 442 = pas de chemin : résultat vide, pas une panne.
            if (code == 171 || code == 442 || code == 170) return AppError.NoResult
        }
        return e
    }

    private fun parse(body: String, costing: RouteCosting): Outcome<List<Route>> = try {
        val trip = json.decodeFromString(ValhallaResponse.serializer(), body).trip
        val geometry = ArrayList<GeoPoint>()
        val maneuvers = ArrayList<Maneuver>()
        val elevation = ArrayList<Double>()
        var elevationInterval: Double? = null
        trip.legs.forEachIndexed { legIndex, leg ->
            leg.elevation?.takeIf { it.isNotEmpty() }?.let { e ->
                elevationInterval = leg.elevationInterval
                if (elevation.isNotEmpty()) elevation.addAll(e.drop(1)) else elevation.addAll(e)
            }
            val pts = Polyline6.decode(leg.shape)
            val offset = if (geometry.isEmpty()) 0 else geometry.size - 1
            if (geometry.isNotEmpty() && pts.isNotEmpty()) geometry.addAll(pts.drop(1)) else geometry.addAll(pts)
            leg.maneuvers.forEachIndexed { mi, m ->
                maneuvers += Maneuver(
                    type = mapType(m.type),
                    instruction = m.instruction,
                    streetName = m.streetNames.firstOrNull(),
                    lengthMeters = m.length * 1000.0,
                    durationSeconds = m.time,
                    beginShapeIndex = offset + m.beginShapeIndex,
                    roundaboutExit = m.roundaboutExitCount,
                    verbalPre = m.verbalPre?.takeIf { it.isNotBlank() },
                    verbalAlert = m.verbalAlert?.takeIf { it.isNotBlank() },
                    lanes = ValhallaLanes.parse(m.lanes),
                    exitNumber = m.sign?.exitNumberElements?.firstOrNull { it.text.isNotBlank() }?.text,
                    exitToward = m.sign?.exitTowardElements?.map { it.text.trim() }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(" · ")?.takeIf { it.isNotEmpty() },
                    exitBranch = m.sign?.exitBranchElements?.firstOrNull { it.text.isNotBlank() }?.text?.trim(),
                    bearingBeforeDeg = m.bearingBefore,
                    bearingAfterDeg = m.bearingAfter,
                    endShapeIndex = m.endShapeIndex?.let { offset + it },
                    verbalPost = m.verbalPost?.takeIf { it.isNotBlank() },
                    streetNames = m.streetNames.map { it.trim() }.filter { it.isNotEmpty() },
                    // L'étape `legIndex` se termine au point `locations[legIndex + 1]`.
                    sideOfStreet = if (mi == leg.maneuvers.lastIndex) streetSide(trip.locations.getOrNull(legIndex + 1)?.sideOfStreet) else null,
                )
            }
        }
        if (geometry.isEmpty()) Outcome.Failure(AppError.NoResult) else Outcome.Success(listOf(
            Route(
                id = UUID.randomUUID().toString(),
                costing = costing,
                distanceMeters = trip.summary.length * 1000.0,
                durationSeconds = trip.summary.time,
                geometry = geometry,
                maneuvers = maneuvers,
                hasToll = trip.summary.hasToll || trip.legs.any { l -> l.maneuvers.any { it.toll } },
                providerId = id,
                elevationM = elevation.takeIf { it.size >= 2 && elevationInterval != null },
                elevationIntervalM = elevationInterval.takeIf { elevation.size >= 2 },
            ),
        ))
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parse(e.message ?: "valhalla"))
    }

    internal fun mapType(t: Int): ManeuverType = ValhallaManeuverTypes.of(t)

    private fun streetSide(s: String?): StreetSide? = when (s) {
        "left" -> StreetSide.LEFT
        "right" -> StreetSide.RIGHT
        else -> null
    }
}
