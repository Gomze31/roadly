package fr.roadly.core.scooter.analysis

import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.scooter.geo.RouteLine
import fr.roadly.core.scooter.regulation.AreaType
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.segment.DataSource
import fr.roadly.core.scooter.segment.ScooterRouteSegment
import fr.roadly.core.scooter.segment.SurfaceQuality
import fr.roadly.core.scooter.segment.WayCategory
import kotlin.math.abs

enum class WarningSeverity { INFO, CAUTION, DANGER }

enum class ScooterWarningKind(val label: String) {
    STEEP_CLIMB("Forte montée"),
    STEEP_DESCENT("Descente prononcée"),
    GRADE_ABOVE_CAPACITY("Pente supérieure à la capacité annoncée"),
    ROUGH_SURFACE("Revêtement dégradé"),
    UNPAVED("Chemin non revêtu"),
    SHARED_ROAD("Passage sur route partagée"),
    FAST_TRAFFIC("Route à circulation rapide"),
    ROADWORKS("Zone de travaux"),
    COMPLEX_INTERSECTION("Intersection complexe"),
    FORBIDDEN("Passage interdit"),
    NOT_RIDABLE("Passage à pied"),
    UNVERIFIED_ACCESS("Accès non vérifiable"),
    PEDESTRIAN_ZONE("Aire piétonne"),
    UNLIT("Portion non éclairée"),
    CONDITIONAL_RESTRICTION("Restriction temporaire ou conditionnelle"),
}

/** Avertissement localisé sur le tracé ; [detail] reprend la donnée qui le justifie (jamais une estimation). */
data class ScooterWarning(
    val kind: ScooterWarningKind,
    val severity: WarningSeverity,
    val startAlongM: Double,
    val endAlongM: Double,
    val detail: String,
)

/** Part du trajet (0–1) pour laquelle chaque donnée est connue : affichée pour situer la fiabilité de l'analyse. */
data class DataCoverage(
    val osmTags: Double,
    val surfaceFromOsm: Double,
    val grade: Double,
    val speedLimit: Double,
    val area: Double,
    val lighting: Double,
)

/** Source du dénivelé. */
enum class ElevationSource { ROUTE_PROFILE, EDGE_GRADES, NONE }

data class ScooterRouteSummary(
    val distanceM: Double,
    val durationS: Double,
    val lengthByCategory: Map<WayCategory, Double>,
    /** Pistes + bandes + voies vertes, rapporté à la longueur analysée. */
    val cycleInfrastructureShare: Double,
    /** Pistes + voies vertes (séparées de la circulation). */
    val dedicatedShare: Double,
    val sharedRoadPortions: Int,
    val sharedRoadLengthM: Double,
    val ascentM: Double?,
    val descentM: Double?,
    val elevationSource: ElevationSource,
    val steepestClimbPct: Double?,
    val steepestDescentPct: Double?,
    val roughLengthM: Double,
    val unpavedLengthM: Double,
    val forbiddenLengthM: Double,
    val notRidableLengthM: Double,
    val unverifiedLengthM: Double,
    val analyzedLengthM: Double,
)

/** Analyse complète d'un itinéraire trottinette. */
data class ScooterRouteAnalysis(
    val routeId: String,
    val segments: List<ScooterRouteSegment>,
    val warnings: List<ScooterWarning>,
    val summary: ScooterRouteSummary,
    val coverage: DataCoverage,
    val regulationName: String,
    /** Tags OSM des voies obtenus (Overpass) : sans eux, agglomération, éclairage et restrictions locales restent inconnus. */
    val osmTagsAvailable: Boolean,
) {
    /** L'itinéraire ne contient aucune portion interdite ni à pied d'après les données. */
    val hasBlockingPortion: Boolean get() = summary.forbiddenLengthM > 0.0 || summary.notRidableLengthM > 0.0
    fun segmentAt(alongM: Double): ScooterRouteSegment? = segments.firstOrNull { it.contains(alongM) } ?: segments.lastOrNull()?.takeIf { alongM >= it.endAlongM }
}

/**
 * Évalue confort et sécurité à partir des seules données présentes (segments classés, profil d'altitude, manœuvres).
 * Seuils : montée/descente ≥ 6 % sur ≥ 40 m (≥ 10 % = danger) ; route partagée signalée au-delà de 30 m.
 */
object ScooterComfortAnalyzer {
    const val STEEP_GRADE_PCT = 6.0
    const val VERY_STEEP_GRADE_PCT = 10.0
    const val MIN_GRADE_LENGTH_M = 40.0
    const val MIN_SHARED_PORTION_M = 30.0
    private val MAJOR_ROADS = setOf("trunk", "primary", "secondary")

    fun analyze(
        route: Route,
        segments: List<ScooterRouteSegment>,
        profile: ElectricScooterProfile,
        regulationName: String,
        osmTagsAvailable: Boolean,
    ): ScooterRouteAnalysis {
        val warnings = ArrayList<ScooterWarning>()
        var prevCategory: WayCategory? = null
        for (s in segments) {
            val g = s.gradePct
            if (g != null && s.lengthM >= MIN_GRADE_LENGTH_M) {
                if (g >= STEEP_GRADE_PCT) warnings += ScooterWarning(ScooterWarningKind.STEEP_CLIMB, if (g >= VERY_STEEP_GRADE_PCT) WarningSeverity.DANGER else WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "Pente moyenne ${pct(g)} sur ${meters(s.lengthM)}")
                if (g <= -STEEP_GRADE_PCT) warnings += ScooterWarning(ScooterWarningKind.STEEP_DESCENT, if (g <= -VERY_STEEP_GRADE_PCT) WarningSeverity.DANGER else WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "Pente moyenne ${pct(g)} sur ${meters(s.lengthM)}")
            }
            val maxGrade = profile.maxGradePct
            val up = s.maxUpGradePct
            if (maxGrade != null && up != null && up > maxGrade) {
                warnings += ScooterWarning(ScooterWarningKind.GRADE_ABOVE_CAPACITY, WarningSeverity.DANGER, s.startAlongM, s.endAlongM, "Pente max. ${pct(up)} > ${pct(maxGrade)} annoncés")
            }
            when (s.surface) {
                SurfaceQuality.ROUGH -> warnings += ScooterWarning(ScooterWarningKind.ROUGH_SURFACE, WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "${s.surface.label} (${sourceLabel(s.surfaceSource)})")
                SurfaceQuality.UNPAVED, SurfaceQuality.IMPASSABLE -> warnings += ScooterWarning(ScooterWarningKind.UNPAVED, if (s.surface == SurfaceQuality.IMPASSABLE) WarningSeverity.DANGER else WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "${s.surface.label} (${sourceLabel(s.surfaceSource)})")
                else -> Unit
            }
            if (s.category == WayCategory.SHARED_ROAD && s.lengthM >= MIN_SHARED_PORTION_M && prevCategory != WayCategory.SHARED_ROAD) {
                warnings += ScooterWarning(ScooterWarningKind.SHARED_ROAD, WarningSeverity.INFO, s.startAlongM, s.endAlongM, "Route partagée sur ${meters(s.lengthM)}" + (s.speedLimitKmh?.let { ", limitée à $it km/h" } ?: ""))
            }
            val limit = s.speedLimitKmh
            if (s.category == WayCategory.SHARED_ROAD && limit != null && limit > 30) {
                warnings += ScooterWarning(ScooterWarningKind.FAST_TRAFFIC, if (limit > 50) WarningSeverity.DANGER else WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "Circulation limitée à $limit km/h")
            }
            if (s.roadworks) warnings += ScooterWarning(ScooterWarningKind.ROADWORKS, WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "Travaux indiqués dans OSM")
            when (s.legal.status) {
                LegalStatus.FORBIDDEN -> warnings += ScooterWarning(ScooterWarningKind.FORBIDDEN, WarningSeverity.DANGER, s.startAlongM, s.endAlongM, s.legal.reason)
                LegalStatus.NOT_RIDABLE -> warnings += ScooterWarning(ScooterWarningKind.NOT_RIDABLE, WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, s.legal.reason)
                else -> Unit
            }
            if (s.category == WayCategory.PEDESTRIAN_ZONE) warnings += ScooterWarning(ScooterWarningKind.PEDESTRIAN_ZONE, WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, "Allure du pas, priorité aux piétons")
            if (s.lit == false) warnings += ScooterWarning(ScooterWarningKind.UNLIT, WarningSeverity.INFO, s.startAlongM, s.endAlongM, "Non éclairé (OSM lit=no)")
            s.conditionalRestriction?.let { warnings += ScooterWarning(ScooterWarningKind.CONDITIONAL_RESTRICTION, WarningSeverity.CAUTION, s.startAlongM, s.endAlongM, it) }
            prevCategory = s.category
        }
        warnings += complexIntersections(route, segments)
        val unverified = segments.filter { it.legal.status == LegalStatus.UNVERIFIED }
        if (unverified.isNotEmpty()) {
            val len = unverified.sumOf { it.lengthM }
            warnings += ScooterWarning(ScooterWarningKind.UNVERIFIED_ACCESS, WarningSeverity.INFO, unverified.first().startAlongM, unverified.last().endAlongM, "${meters(len)} dont l'accès ne peut pas être vérifié")
        }
        return ScooterRouteAnalysis(
            routeId = route.id,
            segments = segments,
            warnings = warnings.sortedBy { it.startAlongM },
            summary = summarize(route, segments),
            coverage = coverage(segments),
            regulationName = regulationName,
            osmTagsAvailable = osmTagsAvailable,
        )
    }

    /** Tourne-à-gauche, demi-tour ou rond-point sur un axe principal (classe de route Valhalla) : données de la manœuvre et de la voie. */
    internal fun complexIntersections(route: Route, segments: List<ScooterRouteSegment>): List<ScooterWarning> {
        if (segments.isEmpty() || route.geometry.size < 2) return emptyList()
        val line = RouteLine(route.geometry)
        val scale = segments.last().endAlongM.takeIf { it > 0 }?.let { it / line.lengthM.coerceAtLeast(1e-9) } ?: 1.0
        val risky = setOf(ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.UTURN_LEFT, ManeuverType.UTURN_RIGHT, ManeuverType.ROUNDABOUT_ENTER)
        return route.maneuvers.mapNotNull { m ->
            if (m.type !in risky) return@mapNotNull null
            val along = line.alongAt(m.beginShapeIndex) * scale
            val before = segments.lastOrNull { it.startAlongM < along - 1.0 }
            val after = segments.firstOrNull { it.contains(along + 1.0) }
            val major = listOfNotNull(before, after).firstOrNull { it.category == WayCategory.SHARED_ROAD && it.roadClass in MAJOR_ROADS } ?: return@mapNotNull null
            val what = if (m.type == ManeuverType.ROUNDABOUT_ENTER) "Rond-point" else "Tourne-à-gauche"
            ScooterWarning(ScooterWarningKind.COMPLEX_INTERSECTION, WarningSeverity.CAUTION, (along - 20).coerceAtLeast(0.0), along + 20, "$what sur un axe ${roadClassLabel(major.roadClass)}")
        }
    }

    internal fun summarize(route: Route, segments: List<ScooterRouteSegment>): ScooterRouteSummary {
        val analyzed = segments.sumOf { it.lengthM }
        val byCat = segments.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.lengthM } }
        val cycle = segments.filter { it.category.isCycleInfrastructure }.sumOf { it.lengthM }
        val dedicated = segments.filter { it.category.isDedicated }.sumOf { it.lengthM }
        val shared = segments.filter { it.category == WayCategory.SHARED_ROAD }
        var portions = 0
        var prev: WayCategory? = null
        segments.forEach { s ->
            if (s.category == WayCategory.SHARED_ROAD && prev != WayCategory.SHARED_ROAD && s.lengthM >= MIN_SHARED_PORTION_M) portions++
            prev = s.category
        }
        val (ascent, descent, source) = elevation(route, segments)
        return ScooterRouteSummary(
            distanceM = route.distanceMeters,
            durationS = route.durationSeconds,
            lengthByCategory = byCat,
            cycleInfrastructureShare = if (analyzed > 0) cycle / analyzed else 0.0,
            dedicatedShare = if (analyzed > 0) dedicated / analyzed else 0.0,
            sharedRoadPortions = portions,
            sharedRoadLengthM = shared.sumOf { it.lengthM },
            ascentM = ascent,
            descentM = descent,
            elevationSource = source,
            steepestClimbPct = segments.filter { it.lengthM >= MIN_GRADE_LENGTH_M }.mapNotNull { it.gradePct }.filter { it > 0 }.maxOrNull(),
            steepestDescentPct = segments.filter { it.lengthM >= MIN_GRADE_LENGTH_M }.mapNotNull { it.gradePct }.filter { it < 0 }.minOrNull(),
            roughLengthM = segments.filter { it.surface == SurfaceQuality.ROUGH }.sumOf { it.lengthM },
            unpavedLengthM = segments.filter { it.surface == SurfaceQuality.UNPAVED || it.surface == SurfaceQuality.IMPASSABLE }.sumOf { it.lengthM },
            forbiddenLengthM = segments.filter { it.legal.status == LegalStatus.FORBIDDEN }.sumOf { it.lengthM },
            notRidableLengthM = segments.filter { it.legal.status == LegalStatus.NOT_RIDABLE }.sumOf { it.lengthM },
            unverifiedLengthM = segments.filter { it.legal.status == LegalStatus.UNVERIFIED }.sumOf { it.lengthM },
            analyzedLengthM = analyzed,
        )
    }

    /** Dénivelé : profil d'altitude de l'itinéraire (`elevation_interval`) en priorité, sinon pentes des arêtes, sinon inconnu. */
    internal fun elevation(route: Route, segments: List<ScooterRouteSegment>): Triple<Double?, Double?, ElevationSource> {
        val profile = route.elevationM
        if (profile != null && profile.size >= 2) {
            var up = 0.0; var down = 0.0
            for (i in 1 until profile.size) {
                val d = profile[i] - profile[i - 1]
                if (d > 0) up += d else down -= d
            }
            return Triple(up, down, ElevationSource.ROUTE_PROFILE)
        }
        val graded = segments.filter { it.gradePct != null }
        if (graded.isEmpty()) return Triple(null, null, ElevationSource.NONE)
        val up = graded.filter { it.gradePct!! > 0 }.sumOf { it.gradePct!! / 100.0 * it.lengthM }
        val down = graded.filter { it.gradePct!! < 0 }.sumOf { abs(it.gradePct!!) / 100.0 * it.lengthM }
        return Triple(up, down, ElevationSource.EDGE_GRADES)
    }

    internal fun coverage(segments: List<ScooterRouteSegment>): DataCoverage {
        val total = segments.sumOf { it.lengthM }.takeIf { it > 0 } ?: return DataCoverage(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        fun share(p: (ScooterRouteSegment) -> Boolean) = segments.filter(p).sumOf { it.lengthM } / total
        return DataCoverage(
            osmTags = share { it.hasOsmTags },
            surfaceFromOsm = share { it.surfaceSource == DataSource.OSM_TAG },
            grade = share { it.gradePct != null },
            speedLimit = share { it.speedLimitKmh != null },
            area = share { it.area != AreaType.UNKNOWN },
            lighting = share { it.lit != null },
        )
    }

    private fun roadClassLabel(c: String?) = when (c) {
        "trunk" -> "rapide"; "primary" -> "principal"; "secondary" -> "secondaire"; else -> "important"
    }
    private fun sourceLabel(s: DataSource) = when (s) {
        DataSource.OSM_TAG -> "OSM"; DataSource.VALHALLA -> "Valhalla"; DataSource.LAW -> "règle"; DataSource.NONE -> "inconnu"
    }
    internal fun pct(v: Double) = String.format(java.util.Locale.FRANCE, "%.0f %%", v)
    internal fun meters(m: Double) = if (m >= 1000) String.format(java.util.Locale.FRANCE, "%.1f km", m / 1000) else "${(m / 10).toInt() * 10} m"
}
