package fr.roadly.core.scooter.segment

import fr.roadly.core.scooter.regulation.AreaType
import fr.roadly.core.scooter.regulation.ScooterRegulation
import fr.roadly.core.scooter.regulation.SegmentFacts

/**
 * Transforme les arêtes Valhalla (et, si disponibles, les tags OSM de leurs voies) en portions homogènes
 * catégorisées et évaluées par la [regulation]. Les abscisses sont recalées sur [routeLengthM] : la somme des
 * longueurs d'arêtes et la longueur de la géométrie diffèrent de quelques mètres.
 */
object ScooterSegmentClassifier {

    /** Une arête enrichie avant regroupement. */
    internal data class Classified(
        val lengthM: Double,
        val category: WayCategory,
        val roadClass: String?,
        val surface: SurfaceQuality,
        val surfaceSource: DataSource,
        val grade: Double?,
        val maxUp: Double?,
        val maxDown: Double?,
        val speed: Int?,
        val speedSource: DataSource,
        val lit: Boolean?,
        val area: AreaType,
        val facts: SegmentFacts,
        val roadworks: Boolean,
        val conditional: String?,
        val names: List<String>,
        val wayId: Long?,
        val hasTags: Boolean,
        val legal: fr.roadly.core.scooter.regulation.LegalAssessment? = null,
    )

    fun classify(
        edges: List<EdgeAttributes>,
        wayTags: Map<Long, Map<String, String>>,
        regulation: ScooterRegulation,
        routeLengthM: Double,
    ): List<ScooterRouteSegment> {
        if (edges.isEmpty()) return emptyList()
        val classified = edges.map { e -> classifyEdge(e, e.wayId?.let { wayTags[it] }?.let(::OsmWayTags)).let { c -> c.copy(legal = regulation.assess(c.facts)) } }
        val total = classified.sumOf { it.lengthM }.takeIf { it > 0 } ?: return emptyList()
        val scale = if (routeLengthM > 0) routeLengthM / total else 1.0
        val out = ArrayList<ScooterRouteSegment>()
        var along = 0.0
        var group = ArrayList<Classified>()
        var groupStart = 0.0
        fun flush(end: Double) {
            if (group.isEmpty()) return
            out += merge(group, groupStart, end, regulation)
            group = ArrayList()
        }
        for (c in classified) {
            val prev = group.lastOrNull()
            if (prev != null && !sameGroup(prev, c)) { flush(along); groupStart = along }
            group += c
            along += c.lengthM * scale
        }
        flush(along)
        return out
    }

    private fun sameGroup(a: Classified, b: Classified): Boolean =
        a.category == b.category && a.surface == b.surface && a.area == b.area && a.roadworks == b.roadworks &&
            a.speed == b.speed && a.lit == b.lit && a.conditional == b.conditional && a.legal == b.legal &&
            gradeBand(a.grade) == gradeBand(b.grade)

    private fun merge(group: List<Classified>, start: Double, end: Double, regulation: ScooterRegulation): ScooterRouteSegment {
        val first = group.first()
        val graded = group.filter { it.grade != null }
        val grade = if (graded.isEmpty()) null else graded.sumOf { it.grade!! * it.lengthM } / graded.sumOf { it.lengthM }.coerceAtLeast(1e-9)
        // Statut le plus restrictif du groupe (les faits peuvent différer d'une arête à l'autre : accès, motorroad…).
        val legal = group.map { it.legal ?: regulation.assess(it.facts) }.minBy { severityRank(it.status) }
        return ScooterRouteSegment(
            startAlongM = start,
            endAlongM = end,
            category = first.category,
            roadClass = group.mapNotNull { it.roadClass }.maxByOrNull { roadRank(it) },
            surface = first.surface,
            surfaceSource = if (group.any { it.surfaceSource == DataSource.OSM_TAG }) DataSource.OSM_TAG else first.surfaceSource,
            gradePct = grade,
            maxUpGradePct = group.mapNotNull { it.maxUp }.maxOrNull(),
            maxDownGradePct = group.mapNotNull { it.maxDown }.minOrNull(),
            speedLimitKmh = first.speed,
            speedLimitSource = first.speedSource,
            lit = first.lit,
            area = first.area,
            legal = legal,
            roadworks = first.roadworks,
            conditionalRestriction = first.conditional,
            names = group.flatMap { it.names }.distinct(),
            wayIds = group.mapNotNull { it.wayId }.distinct(),
            hasOsmTags = group.all { it.hasTags },
        )
    }

    /** Tranches de pente : une montée ou une descente marquée n'est jamais fondue dans une portion plate. */
    internal fun gradeBand(g: Double?): Int = when {
        g == null -> Int.MIN_VALUE
        g <= -10.0 -> -2
        g <= -6.0 -> -1
        g < 6.0 -> 0
        g < 10.0 -> 1
        else -> 2
    }

    /** Plus petit = plus restrictif. */
    private fun severityRank(s: fr.roadly.core.scooter.regulation.LegalStatus): Int = when (s) {
        fr.roadly.core.scooter.regulation.LegalStatus.FORBIDDEN -> 0
        fr.roadly.core.scooter.regulation.LegalStatus.NOT_RIDABLE -> 1
        fr.roadly.core.scooter.regulation.LegalStatus.UNVERIFIED -> 2
        fr.roadly.core.scooter.regulation.LegalStatus.ALLOWED -> 3
    }

    internal fun roadRank(c: String): Int = when (c) {
        "motorway" -> 7; "trunk" -> 6; "primary" -> 5; "secondary" -> 4; "tertiary" -> 3
        "unclassified" -> 2; "residential" -> 1; else -> 0
    }

    private val ROAD_USES = setOf("road", "ramp", "turn_channel", "driveway", "alley", "parking_aisle", "emergency_access", "drive_through", "culdesac", "service_road", "cycle_street", "bus")
    private val ALLOW = setOf("yes", "designated", "permissive")

    internal fun classifyEdge(e: EdgeAttributes, tags: OsmWayTags?): Classified {
        val highway = tags?.get("highway")
        val category = category(e, tags, highway)
        val (surface, surfaceSource) = surface(e, tags)
        val (speed, speedSource) = speed(e, tags, category)
        val area = area(tags, category)
        val roadworks = category == WayCategory.CONSTRUCTION || tags?.get("construction") != null || e.use == "construction"
        val conditional = tags?.tags?.entries
            ?.firstOrNull { (k, _) -> k.endsWith(":conditional") && (k.startsWith("access") || k.startsWith("bicycle") || k.startsWith("small_electric_vehicle") || k.startsWith("vehicle")) }
            ?.let { "${it.key}=${it.value}" }
        val facts = SegmentFacts(
            category = category,
            roadClass = e.roadClass,
            speedLimitKmh = speed,
            area = area,
            smallElectricVehicleAccess = tags?.get("small_electric_vehicle"),
            bicycleAccess = tags?.get("bicycle"),
            generalAccess = tags?.get("access"),
            motorroad = tags?.get("motorroad") == "yes",
        )
        return Classified(
            lengthM = e.lengthM, category = category, roadClass = e.roadClass, surface = surface, surfaceSource = surfaceSource,
            grade = e.weightedGradePct, maxUp = e.maxUpGradePct, maxDown = e.maxDownGradePct,
            speed = speed, speedSource = speedSource,
            lit = when (tags?.get("lit")) { "yes", "24/7", "automatic", "sunset-sunrise", "limited" -> true; "no" -> false; else -> null },
            area = area, facts = facts, roadworks = roadworks, conditional = conditional,
            names = e.names, wayId = e.wayId, hasTags = tags != null,
        )
    }

    private fun category(e: EdgeAttributes, tags: OsmWayTags?, highway: String?): WayCategory {
        val use = e.use
        if (use == "steps" || highway == "steps") return WayCategory.STEPS
        if (use == "construction" || highway == "construction") return WayCategory.CONSTRUCTION
        if (use == "ferry" || use == "rail-ferry") return WayCategory.FERRY
        if (tags != null && highway != null) {
            val trafficSign = tags["traffic_sign"].orEmpty()
            val bicycle = tags["bicycle"]
            val foot = tags["foot"]
            if (trafficSign.contains("FR:C115")) return WayCategory.GREENWAY
            if (highway in setOf("path", "track", "footway", "bridleway") && bicycle == "designated" && foot == "designated" && tags["segregated"] != "yes") return WayCategory.GREENWAY
            if (highway == "cycleway") {
                if (tags["cycleway"] == "crossing" || tags["footway"] == "crossing") return WayCategory.CROSSING
                return WayCategory.CYCLE_TRACK
            }
            if (highway in setOf("path", "track", "footway", "bridleway", "pedestrian") && bicycle == "designated") return WayCategory.CYCLE_TRACK
            if (highway == "footway" && tags["footway"] == "crossing") return WayCategory.CROSSING
            if (highway in setOf("path", "track", "footway", "bridleway") && bicycle in ALLOW) return WayCategory.ALLOWED_PATH
            if (highway == "pedestrian") return WayCategory.PEDESTRIAN_ZONE
            if (highway == "footway" || highway == "corridor") return WayCategory.SIDEWALK
            if (highway in setOf("path", "track", "bridleway")) return WayCategory.PATH
            if (highway == "living_street") return WayCategory.LIVING_STREET
            if (highway == "motorway" || highway == "motorway_link") return WayCategory.SHARED_ROAD
        }
        return when {
            use == "cycleway" -> WayCategory.CYCLE_TRACK
            use == "pedestrian_crossing" -> WayCategory.CROSSING
            use == "living_street" -> WayCategory.LIVING_STREET
            use == "pedestrian" -> WayCategory.PEDESTRIAN_ZONE
            use == "footway" || use == "sidewalk" -> WayCategory.SIDEWALK
            use == "path" -> WayCategory.ALLOWED_PATH // choisie par le costing vélo : accès cycles présent
            use == "track" || use == "mountain_bike" || use == "bridleway" -> WayCategory.PATH
            use == null || use in ROAD_USES -> when (e.cycleLane) {
                "separated" -> WayCategory.CYCLE_TRACK
                "dedicated" -> WayCategory.CYCLE_LANE
                null -> if (use == null) WayCategory.UNKNOWN else WayCategory.SHARED_ROAD
                else -> WayCategory.SHARED_ROAD
            }
            else -> WayCategory.UNKNOWN
        }
    }

    private val ROUGH_SURFACES = setOf("sett", "cobblestone", "unhewn_cobblestone", "cobblestone:flattened", "grass_paver")
    private val UNPAVED_SURFACES = setOf("unpaved", "compacted", "fine_gravel", "gravel", "pebblestone", "dirt", "earth", "ground", "grass", "mud", "sand", "woodchips", "rock")
    private val SMOOTH_SURFACES = setOf("asphalt", "concrete", "concrete:plates", "paved")

    private fun surface(e: EdgeAttributes, tags: OsmWayTags?): Pair<SurfaceQuality, DataSource> {
        val smoothness = tags?.get("smoothness")
        val surfaceTag = tags?.get("surface")
        when (smoothness) {
            "impassable" -> return SurfaceQuality.IMPASSABLE to DataSource.OSM_TAG
            "bad", "very_bad", "horrible", "very_horrible" -> return SurfaceQuality.ROUGH to DataSource.OSM_TAG
        }
        if (surfaceTag != null) {
            when {
                surfaceTag in ROUGH_SURFACES -> return SurfaceQuality.ROUGH to DataSource.OSM_TAG
                surfaceTag in UNPAVED_SURFACES -> return SurfaceQuality.UNPAVED to DataSource.OSM_TAG
                surfaceTag in SMOOTH_SURFACES -> return (if (smoothness in setOf("excellent", "good")) SurfaceQuality.SMOOTH else SurfaceQuality.GOOD) to DataSource.OSM_TAG
                surfaceTag == "paving_stones" -> return SurfaceQuality.GOOD to DataSource.OSM_TAG
            }
        }
        // Valhalla renseigne toujours une surface, déduite du type de voie quand OSM n'en a pas : source « Valhalla ».
        val q = when (e.surface) {
            "paved_smooth" -> SurfaceQuality.SMOOTH
            "paved" -> SurfaceQuality.GOOD
            "paved_rough" -> SurfaceQuality.ROUGH
            "compacted", "dirt", "gravel", "path" -> SurfaceQuality.UNPAVED
            "impassable" -> SurfaceQuality.IMPASSABLE
            else -> return SurfaceQuality.UNKNOWN to DataSource.NONE
        }
        return q to DataSource.VALHALLA
    }

    private fun speed(e: EdgeAttributes, tags: OsmWayTags?, category: WayCategory): Pair<Int?, DataSource> {
        e.speedLimitKmh?.let { return it to DataSource.VALHALLA }
        tags?.get("maxspeed")?.trim()?.toIntOrNull()?.takeIf { it in 1..254 }?.let { return it to DataSource.OSM_TAG }
        when (tags?.get("maxspeed")?.uppercase()) {
            "FR:URBAN" -> return 50 to DataSource.OSM_TAG
            "FR:ZONE30" -> return 30 to DataSource.OSM_TAG
            "FR:WALK" -> return 6 to DataSource.OSM_TAG
        }
        if (category == WayCategory.LIVING_STREET) return 20 to DataSource.LAW
        return null to DataSource.NONE
    }

    /** Agglomération : seulement si les tags le disent (FR:urban / FR:zone30 / FR:rural…). */
    internal fun area(tags: OsmWayTags?, category: WayCategory): AreaType {
        val values = listOfNotNull(tags?.get("maxspeed:type"), tags?.get("source:maxspeed"), tags?.get("zone:maxspeed"), tags?.get("maxspeed")).map { it.uppercase() }
        if (values.any { v -> v == "FR:URBAN" || v.startsWith("FR:ZONE") || v == "FR:30" || v == "FR:20" || v == "FR:WALK" }) return AreaType.URBAN
        if (values.any { v -> v == "FR:RURAL" || v == "FR:MOTORWAY" || v == "FR:TRUNK" }) return AreaType.RURAL
        if (category == WayCategory.LIVING_STREET) return AreaType.URBAN
        return AreaType.UNKNOWN
    }
}
