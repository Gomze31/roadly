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
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
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
        val base = config.config.first().valhallaBaseUrl
        // URL de base surchargeable par l'utilisateur : ne jamais lever d'exception si elle est invalide.
        val url = "$base/route".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val body = buildJsonObject {
            putJsonArray("locations") {
                (listOf(request.origin) + request.waypoints + request.destination).forEach { p -> addJsonObject { put("lat", p.lat); put("lon", p.lon) } }
            }
            put("costing", "auto")
            putJsonObject("costing_options") {
                putJsonObject("auto") {
                    when (request.costing) {
                        RouteCosting.FASTEST -> Unit
                        RouteCosting.SHORTEST -> put("shortest", true)
                        RouteCosting.NO_TOLL -> put("use_tolls", 0)
                    }
                }
            }
            put("units", "kilometers")
            put("language", "fr-FR")
        }
        return when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> parse(r.value, request.costing)
            is Outcome.Failure -> Outcome.Failure(mapError(r.error))
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
        trip.legs.forEach { leg ->
            val pts = Polyline6.decode(leg.shape)
            val offset = if (geometry.isEmpty()) 0 else geometry.size - 1
            if (geometry.isNotEmpty() && pts.isNotEmpty()) geometry.addAll(pts.drop(1)) else geometry.addAll(pts)
            leg.maneuvers.forEach { m ->
                maneuvers += Maneuver(
                    type = mapType(m.type),
                    instruction = m.instruction,
                    streetName = m.streetNames.firstOrNull(),
                    lengthMeters = m.length * 1000.0,
                    durationSeconds = m.time,
                    beginShapeIndex = offset + m.beginShapeIndex,
                    roundaboutExit = m.roundaboutExitCount,
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
            ),
        ))
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parse(e.message ?: "valhalla"))
    }

    internal fun mapType(t: Int): ManeuverType = when (t) {
        1, 2, 3 -> ManeuverType.DEPART
        4, 5, 6 -> ManeuverType.ARRIVE
        7, 8 -> ManeuverType.CONTINUE
        9 -> ManeuverType.SLIGHT_RIGHT
        10 -> ManeuverType.TURN_RIGHT
        11 -> ManeuverType.SHARP_RIGHT
        12, 13 -> ManeuverType.UTURN
        14 -> ManeuverType.SHARP_LEFT
        15 -> ManeuverType.TURN_LEFT
        16 -> ManeuverType.SLIGHT_LEFT
        17, 18, 19 -> ManeuverType.RAMP
        20, 21 -> ManeuverType.EXIT
        22 -> ManeuverType.CONTINUE
        23 -> ManeuverType.KEEP_RIGHT
        24 -> ManeuverType.KEEP_LEFT
        25, 37, 38 -> ManeuverType.MERGE
        26 -> ManeuverType.ROUNDABOUT_ENTER
        27 -> ManeuverType.ROUNDABOUT_EXIT
        28, 29 -> ManeuverType.FERRY
        else -> ManeuverType.OTHER
    }
}
