package fr.roadly.core.scooter.segment

/**
 * Attributs d'une arête Valhalla (`/trace_attributes`, https://valhalla.github.io/valhalla/api/map-matching/).
 * Valeurs brutes de l'API ; null = attribut absent de la réponse.
 */
data class EdgeAttributes(
    val lengthM: Double,
    /** `edge.use` : road, cycleway, footway, steps, path, living_street, pedestrian_crossing, construction… */
    val use: String? = null,
    /** `edge.cycle_lane` : none, shared, dedicated (bande), separated (piste). */
    val cycleLane: String? = null,
    /** `edge.road_class` : motorway, trunk, primary, secondary, tertiary, unclassified, residential, service_other. */
    val roadClass: String? = null,
    /** `edge.surface` : paved_smooth, paved, paved_rough, compacted, dirt, gravel, path, impassable. */
    val surface: String? = null,
    val unpaved: Boolean? = null,
    val weightedGradePct: Double? = null,
    val maxUpGradePct: Double? = null,
    val maxDownGradePct: Double? = null,
    /** `edge.speed_limit` (km/h) ; absent quand inconnu, « unlimited » ramené à null. */
    val speedLimitKmh: Int? = null,
    val wayId: Long? = null,
    val sidewalk: String? = null,
    val names: List<String> = emptyList(),
)

/** Tags OSM d'une voie (Overpass), clé → valeur. */
@JvmInline
value class OsmWayTags(val tags: Map<String, String>) {
    operator fun get(key: String): String? = tags[key]
    fun has(key: String, vararg values: String): Boolean = tags[key]?.let { v -> values.isEmpty() || v in values } ?: false
}
