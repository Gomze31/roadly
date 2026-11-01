package fr.roadly.core.scooter.valhalla

import fr.roadly.core.scooter.segment.EdgeAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Réponse `/trace_attributes` réduite à ce que le mode trottinette exploite. */
data class TraceAttributesResult(
    val edges: List<EdgeAttributes>,
    /** Codes pays ISO des zones administratives traversées (`admins[].country_code`). */
    val countryCodes: List<String>,
)

/** Construction de la requête et décodage tolérant de la réponse `/trace_attributes` (aucune exception ne sort). */
object TraceAttributes {
    val ATTRIBUTES = listOf(
        "edge.length", "edge.use", "edge.cycle_lane", "edge.road_class", "edge.surface", "edge.unpaved",
        "edge.weighted_grade", "edge.max_upward_grade", "edge.max_downward_grade", "edge.speed_limit",
        "edge.way_id", "edge.sidewalk", "edge.names", "admin.country_code",
    )

    /**
     * `shape_match: edge_walk` : le tracé fourni est exactement celui calculé par Valhalla, on le parcourt arête par arête.
     * [encodedPolyline6] = géométrie de l'itinéraire (précision 6, défaut Valhalla).
     */
    fun request(encodedPolyline6: String, costingOptions: Map<String, Any>, costing: String = "bicycle"): JsonObject = buildJsonObject {
        put("encoded_polyline", encodedPolyline6)
        put("shape_match", "edge_walk")
        put("costing", costing)
        putJsonObject("costing_options") { put(costing, mapToJson(costingOptions)) }
        put("units", "kilometers")
        putJsonObject("filters") {
            putJsonArray("attributes") { ATTRIBUTES.forEach { add(it) } }
            put("action", "include")
        }
    }

    fun parse(json: Json, body: String): TraceAttributesResult? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val edges = (root["edges"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toEdge() }
        val countries = (root["admins"] as? JsonArray).orEmpty().mapNotNull { a ->
            ((a as? JsonObject)?.get("country_code") as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }?.uppercase()
        }.distinct()
        TraceAttributesResult(edges, countries)
    }.getOrNull()

    private fun JsonObject.toEdge(): EdgeAttributes? {
        val lengthKm = num("length") ?: return null
        val speed = this["speed_limit"]?.let { e ->
            (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toInt()?.takeIf { it in 1..254 }
        }
        return EdgeAttributes(
            lengthM = lengthKm * 1000.0,
            use = str("use"),
            cycleLane = str("cycle_lane"),
            roadClass = str("road_class"),
            surface = str("surface"),
            unpaved = (this["unpaved"] as? JsonPrimitive)?.booleanOrNull,
            weightedGradePct = num("weighted_grade"),
            maxUpGradePct = num("max_upward_grade"),
            maxDownGradePct = num("max_downward_grade"),
            speedLimitKmh = speed,
            wayId = (this["way_id"] as? JsonPrimitive)?.longOrNull,
            sidewalk = str("sidewalk"),
            names = (this["names"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() },
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is kotlinx.serialization.json.JsonNull) null else content

    fun mapToJson(map: Map<String, Any>): JsonObject = buildJsonObject {
        map.forEach { (k, v) ->
            when (v) {
                is Boolean -> put(k, v)
                is Number -> put(k, v)
                is String -> put(k, v)
                is JsonElement -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }
}
