package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Virages serrés déduits de la géométrie du tracé : changement de cap cumulé ≥ [MIN_ANGLE_DEG] sur une fenêtre
 * ≤ [WINDOW_M], hors des manœuvres (un virage à une intersection est déjà annoncé par le guidage).
 */
object CurveDetector {
    const val MIN_ANGLE_DEG = 45.0
    const val WINDOW_M = 90.0
    /** Un virage à moins de cette distance du point d'une manœuvre est ignoré. */
    const val MANEUVER_EXCLUSION_M = 40.0
    /** Deux virages plus proches que ceci sont fusionnés en un seul. */
    const val MERGE_M = 60.0

    fun detect(route: Route): List<RouteHazard> = detect(route.geometry, route.maneuvers.map { it.beginShapeIndex })

    fun detect(points: List<GeoPoint>, maneuverShapeIndexes: List<Int> = emptyList()): List<RouteHazard> {
        if (points.size < 3) return emptyList()
        val geo = RouteGeometry(points)
        val headings = DoubleArray(points.size - 1) { i -> heading(points[i], points[i + 1]) }
        val maneuverAlong = maneuverShapeIndexes.mapNotNull { geo.cumulative.getOrNull(it) }
        val found = mutableListOf<RouteHazard>()
        var i = 0
        while (i < headings.size - 1) {
            // Cap cumulé signé depuis le segment i, tant que la fenêtre reste courte.
            var j = i + 1
            var turn = 0.0
            var best: Triple<Int, Double, Double>? = null
            while (j < headings.size && geo.cumulative[j] - geo.cumulative[i + 1] <= WINDOW_M) {
                turn += delta(headings[j - 1], headings[j])
                if (abs(turn) >= MIN_ANGLE_DEG && (best == null || abs(turn) > abs(best.second))) best = Triple(j, turn, geo.cumulative[j])
                j++
            }
            if (best != null) {
                val start = geo.cumulative[i + 1]
                val end = best.third
                val mid = (start + end) / 2
                val nearManeuver = maneuverAlong.any { abs(it - mid) <= MANEUVER_EXCLUSION_M }
                if (!nearManeuver) {
                    val last = found.lastOrNull()
                    if (last != null && start - last.endAlongM <= MERGE_M) {
                        found[found.lastIndex] = last.copy(endAlongM = end, angleDeg = maxOf(last.angleDeg ?: 0.0, abs(best.second)))
                    } else {
                        // Caps croissants = sens horaire = virage à droite.
                        found += RouteHazard(HazardKind.CURVE, start, end, angleDeg = abs(best.second), rightHand = best.second > 0)
                    }
                }
                i = best.first
            } else {
                i++
            }
        }
        return found
    }

    private fun heading(a: GeoPoint, b: GeoPoint): Double {
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(Math.toRadians(b.lat))
        val x = cos(Math.toRadians(a.lat)) * sin(Math.toRadians(b.lat)) - sin(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Écart signé entre deux caps, dans ]-180, 180]. */
    private fun delta(from: Double, to: Double): Double {
        var d = to - from
        while (d > 180.0) d -= 360.0
        while (d <= -180.0) d += 360.0
        return d
    }
}
