package fr.roadly.core.scooter.osm

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.Locale

/** Lieu utile à la trottinette trouvé dans OSM (Overpass) ; [compatibility] dit ce que les données établissent vraiment. */
data class ScooterPoi(
    val osmType: String,
    val osmId: Long,
    val point: GeoPoint,
    val name: String?,
    val kind: ScooterPoiKind,
    val compatibility: PoiCompatibility,
    val tags: Map<String, String>,
)

enum class ScooterPoiKind { PARKING, CHARGING }

enum class PoiCompatibility(val label: String) {
    /** Tag explicite pour les trottinettes électriques (`small_electric_vehicle`, `amenity=kick-scooter_parking`). */
    EXPLICIT("Trottinettes indiqué dans OSM"),
    /** Tag ambigu (`scooter=yes`) : selon les pays il désigne trottinettes ou scooters. */
    AMBIGUOUS("Tag « scooter » ambigu : à vérifier"),
    /** Borne pour vélos électriques : compatibilité trottinette non établie. */
    E_BIKE_ONLY("Borne vélo électrique : compatibilité non vérifiée"),
}

/**
 * Requêtes Overpass (https://wiki.openstreetmap.org/wiki/Overpass_API) et décodage tolérant.
 * Clés OSM utilisées : `small_electric_vehicle` (trottinettes électriques, statut « in use »),
 * `amenity=kick-scooter_parking`, `amenity=charging_station` + `small_electric_vehicle` / `scooter` / `bicycle`.
 */
object Overpass {
    const val MAX_WAY_IDS = 400

    /** Tags des voies d'un itinéraire, en une seule requête. */
    fun wayTagsQuery(wayIds: Collection<Long>): String {
        val ids = wayIds.distinct().take(MAX_WAY_IDS).joinToString(",")
        return "[out:json][timeout:25];way(id:$ids);out tags;"
    }

    fun parseWayTags(json: Json, body: String): Map<Long, Map<String, String>>? = runCatching {
        val elements = json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray ?: return null
        elements.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            if ((o["type"] as? JsonPrimitive)?.content != "way") return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            id to o.tags()
        }.toMap()
    }.getOrNull()

    fun poiQuery(kind: ScooterPoiKind, center: GeoPoint, radiusM: Int): String {
        val around = "(around:$radiusM,${fmt(center.lat)},${fmt(center.lon)})"
        val body = when (kind) {
            ScooterPoiKind.PARKING -> listOf(
                """nwr["amenity"="kick-scooter_parking"]$around;""",
                """nwr["amenity"~"^(bicycle_parking|parking|motorcycle_parking)$"]["small_electric_vehicle"~"^(yes|designated)$"]$around;""",
            )
            ScooterPoiKind.CHARGING -> listOf(
                """nwr["amenity"="charging_station"]["small_electric_vehicle"~"^(yes|designated)$"]$around;""",
                """nwr["amenity"="charging_station"]["scooter"~"^(yes|designated)$"]$around;""",
                """nwr["amenity"="charging_station"]["bicycle"~"^(yes|designated)$"]$around;""",
            )
        }
        return "[out:json][timeout:25];(${body.joinToString("")});out center tags 60;"
    }

    fun parsePois(json: Json, body: String, kind: ScooterPoiKind): List<ScooterPoi>? = runCatching {
        val elements = json.parseToJsonElement(body).jsonObject["elements"] as? JsonArray ?: return null
        elements.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val type = (o["type"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val center = o["center"] as? JsonObject
            val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull ?: (center?.get("lat") as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull ?: (center?.get("lon") as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val tags = o.tags()
            ScooterPoi(type, id, GeoPoint(lat, lon), tags["name"], kind, compatibilityOf(kind, tags), tags)
        }.distinctBy { it.osmType to it.osmId }
    }.getOrNull()

    internal fun compatibilityOf(kind: ScooterPoiKind, tags: Map<String, String>): PoiCompatibility {
        if (tags["small_electric_vehicle"] in setOf("yes", "designated")) return PoiCompatibility.EXPLICIT
        if (kind == ScooterPoiKind.PARKING && tags["amenity"] == "kick-scooter_parking") return PoiCompatibility.EXPLICIT
        if (tags["scooter"] in setOf("yes", "designated")) return PoiCompatibility.AMBIGUOUS
        return PoiCompatibility.E_BIKE_ONLY
    }

    private fun JsonObject.tags(): Map<String, String> =
        (this["tags"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }?.toMap().orEmpty()

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.6f", v)
}
