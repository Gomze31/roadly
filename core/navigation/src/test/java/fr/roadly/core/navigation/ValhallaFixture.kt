package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.StreetSide
import fr.roadly.core.routing.Polyline6
import fr.roadly.core.routing.ValhallaManeuverTypes
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

/** Réponse Valhalla FOSSGIS enregistrée (`src/test/resources/fixtures`, une étape) → [Route], mêmes règles que `ValhallaRoutingProvider`. */
internal class ValhallaFixture(private val resource: String, private val id: String) {
    private val root: JsonObject by lazy {
        Json.parseToJsonElement(ValhallaFixture::class.java.classLoader!!.getResource(resource)!!.readText()).jsonObject
    }
    private val trip: JsonObject get() = root["trip"]!!.jsonObject
    private val leg: JsonObject get() = trip["legs"]!!.jsonArray[0].jsonObject
    val rawManeuvers: List<JsonObject> get() = leg["maneuvers"]!!.jsonArray.map { it.jsonObject }

    fun route(): Route {
        val raw = rawManeuvers
        val destinationSide = ((trip["locations"] as? JsonArray)?.lastOrNull() as? JsonObject)?.str("side_of_street")
        val maneuvers = raw.mapIndexed { i, m ->
            val sign = m["sign"] as? JsonObject
            Maneuver(
                type = ValhallaManeuverTypes.of(m["type"]!!.jsonPrimitive.int),
                instruction = m.str("instruction").orEmpty(),
                streetName = (m["street_names"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content,
                lengthMeters = m["length"]!!.jsonPrimitive.double * 1000.0,
                durationSeconds = m["time"]!!.jsonPrimitive.double,
                beginShapeIndex = m["begin_shape_index"]!!.jsonPrimitive.int,
                roundaboutExit = (m["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull,
                verbalPre = m.str("verbal_pre_transition_instruction")?.takeIf { it.isNotBlank() },
                verbalAlert = m.str("verbal_transition_alert_instruction")?.takeIf { it.isNotBlank() },
                exitNumber = sign.texts("exit_number_elements").firstOrNull(),
                exitToward = sign.texts("exit_toward_elements").take(2).joinToString(" · ").takeIf { it.isNotEmpty() },
                bearingBeforeDeg = (m["bearing_before"] as? JsonPrimitive)?.intOrNull,
                bearingAfterDeg = (m["bearing_after"] as? JsonPrimitive)?.intOrNull,
                endShapeIndex = (m["end_shape_index"] as? JsonPrimitive)?.intOrNull,
                verbalPost = m.str("verbal_post_transition_instruction")?.takeIf { it.isNotBlank() },
                streetNames = (m["street_names"] as? JsonArray)?.map { it.jsonPrimitive.content.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                sideOfStreet = if (i == raw.lastIndex) side(destinationSide) else null,
            )
        }
        val summary = trip["summary"]!!.jsonObject
        return Route(
            id = id, costing = RouteCosting.FASTEST,
            distanceMeters = summary["length"]!!.jsonPrimitive.double * 1000.0,
            durationSeconds = summary["time"]!!.jsonPrimitive.double,
            geometry = Polyline6.decode(leg["shape"]!!.jsonPrimitive.content),
            maneuvers = maneuvers, hasToll = false, providerId = "valhalla",
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.texts(key: String): List<String> =
        (this?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("text")?.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    private fun side(s: String?): StreetSide? = when (s) { "left" -> StreetSide.LEFT; "right" -> StreetSide.RIGHT; else -> null }
}
