package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.routing.Polyline6
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Itinéraire réel Paris (Hôtel de Ville → Étoile) issu de la fixture Valhalla FOSSGIS. */
internal object ParisRoute {
    private val trip: JsonObject by lazy {
        val text = ParisRoute::class.java.classLoader!!.getResource("fixtures/valhalla_route.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject["trip"]!!.jsonObject
    }
    private val leg: JsonObject get() = trip["legs"]!!.jsonArray[0].jsonObject
    val rawManeuvers: List<JsonObject> get() = leg["maneuvers"]!!.jsonArray.map { it.jsonObject }

    fun route(): Route {
        val maneuvers = rawManeuvers.map { m ->
            fun str(key: String): String? = (m[key] as? JsonPrimitive)?.contentOrNull
            Maneuver(
                type = type(m["type"]!!.jsonPrimitive.int),
                instruction = str("instruction").orEmpty(),
                streetName = (m["street_names"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content,
                lengthMeters = m["length"]!!.jsonPrimitive.double * 1000.0,
                durationSeconds = m["time"]!!.jsonPrimitive.double,
                beginShapeIndex = m["begin_shape_index"]!!.jsonPrimitive.int,
                roundaboutExit = (m["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull,
                verbalPre = str("verbal_pre_transition_instruction"),
                verbalAlert = str("verbal_transition_alert_instruction"),
            )
        }
        val summary = trip["summary"]!!.jsonObject
        return Route(
            id = "paris", costing = RouteCosting.FASTEST,
            distanceMeters = summary["length"]!!.jsonPrimitive.double * 1000.0,
            durationSeconds = summary["time"]!!.jsonPrimitive.double,
            geometry = Polyline6.decode(leg["shape"]!!.jsonPrimitive.content),
            maneuvers = maneuvers, hasToll = false, providerId = "valhalla",
        )
    }

    private fun type(t: Int): ManeuverType = when (t) {
        1, 2, 3 -> ManeuverType.DEPART
        4, 5, 6 -> ManeuverType.ARRIVE
        9 -> ManeuverType.SLIGHT_RIGHT
        10 -> ManeuverType.TURN_RIGHT
        15 -> ManeuverType.TURN_LEFT
        26 -> ManeuverType.ROUNDABOUT_ENTER
        27 -> ManeuverType.ROUNDABOUT_EXIT
        else -> ManeuverType.CONTINUE
    }
}
