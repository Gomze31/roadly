package fr.roadly.core.scooter.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysis
import fr.roadly.core.scooter.analysis.WarningSeverity
import fr.roadly.core.scooter.geo.RouteLine
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.segment.WayCategory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.max
import kotlin.math.min

/** Groupe d'affichage du tracé trottinette (une couche de carte par groupe). */
enum class RouteDisplayGroup(val key: String, val label: String, val colorHex: String) {
    CYCLE_TRACK("track", "Piste cyclable", "#22C58B"),
    CYCLE_LANE("lane", "Bande cyclable", "#8BE3A4"),
    GREENWAY("greenway", "Voie verte", "#14B8A6"),
    SHARED("shared", "Route partagée", "#F5A524"),
    PATH("path", "Chemin autorisé / aire piétonne", "#C08A5B"),
    CAUTION("caution", "Vigilance particulière", "#FF4D6D");

    companion object {
        fun of(c: WayCategory): RouteDisplayGroup = when (c) {
            WayCategory.CYCLE_TRACK -> CYCLE_TRACK
            WayCategory.CYCLE_LANE -> CYCLE_LANE
            WayCategory.GREENWAY -> GREENWAY
            WayCategory.ALLOWED_PATH, WayCategory.PATH, WayCategory.PEDESTRIAN_ZONE -> PATH
            WayCategory.STEPS, WayCategory.SIDEWALK, WayCategory.CONSTRUCTION -> CAUTION
            else -> SHARED
        }
    }
}

/** GeoJSON du tracé trottinette : une LineString par segment (propriété `group`), plus les portions de vigilance (`caution`). */
object ScooterRouteFeatures {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun build(geometry: List<GeoPoint>, analysis: ScooterRouteAnalysis?): String {
        if (analysis == null || analysis.segments.isEmpty() || geometry.size < 2) return EMPTY
        val line = RouteLine(geometry)
        val dangers = analysis.warnings.filter { it.severity == WarningSeverity.DANGER }
        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                analysis.segments.forEach { s ->
                    val pts = line.slice(s.startAlongM, s.endAlongM)
                    if (pts.size >= 2) add(feature(pts, RouteDisplayGroup.of(s.category).key, caution = false))
                    val cautious = s.legal.status == LegalStatus.FORBIDDEN || s.legal.status == LegalStatus.NOT_RIDABLE
                    if (cautious && pts.size >= 2) add(feature(pts, RouteDisplayGroup.CAUTION.key, caution = true))
                }
                dangers.forEach { w ->
                    val pts = line.slice(max(0.0, w.startAlongM), min(line.lengthM, w.endAlongM))
                    if (pts.size >= 2) add(feature(pts, RouteDisplayGroup.CAUTION.key, caution = true))
                }
            }
        }.toString()
    }

    private fun feature(pts: List<GeoPoint>, group: String, caution: Boolean) = buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") { put("group", group); put("caution", caution) }
        putJsonObject("geometry") {
            put("type", "LineString")
            put("coordinates", JsonArray(pts.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) }))
        }
    }
}

/** Cible de caméra 3D (zoom, inclinaison en degrés). */
data class CameraTarget(val zoom: Double, val tiltDeg: Double)

/**
 * Caméra de guidage trottinette : perspective inclinée en circulation, vue plus haute et plus proche à l'approche
 * d'une intersection pour donner du contexte. Transition continue (interpolation linéaire entre 150 et 60 m).
 */
object ScooterCamera {
    const val CRUISE_ZOOM = 17.4
    const val CRUISE_TILT = 52.0
    const val APPROACH_ZOOM = 18.1
    const val APPROACH_TILT = 35.0
    const val APPROACH_START_M = 150.0
    const val APPROACH_FULL_M = 60.0
    const val SLOW_KMH = 6.0

    fun target(distanceToManeuverM: Double?, speedKmh: Double?): CameraTarget {
        val d = distanceToManeuverM ?: Double.MAX_VALUE
        val t = when {
            d >= APPROACH_START_M -> 0.0
            d <= APPROACH_FULL_M -> 1.0
            else -> (APPROACH_START_M - d) / (APPROACH_START_M - APPROACH_FULL_M)
        }
        var zoom = CRUISE_ZOOM + (APPROACH_ZOOM - CRUISE_ZOOM) * t
        val tilt = CRUISE_TILT + (APPROACH_TILT - CRUISE_TILT) * t
        if (speedKmh != null && speedKmh < SLOW_KMH) zoom = max(zoom, 17.8)
        return CameraTarget(zoom, tilt)
    }
}
