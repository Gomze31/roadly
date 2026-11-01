package fr.roadly.core.scooter.routing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.scooter.geo.RouteLine
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.segment.ScooterRouteSegment
import kotlin.math.cos

/**
 * Zones à exclure d'un nouveau calcul (`exclude_polygons` Valhalla) : rectangles serrés autour des portions
 * interdites ou à pied détectées sur un premier itinéraire. Les portions touchant le départ ou l'arrivée sont gardées
 * (les exclure rendrait le trajet impossible).
 */
object ForbiddenAreas {
    const val MARGIN_M = 8.0
    const val END_CLEARANCE_M = 30.0
    const val MAX_POLYGONS = 10

    fun polygons(geometry: List<GeoPoint>, segments: List<ScooterRouteSegment>): List<List<GeoPoint>> {
        val line = RouteLine(geometry)
        val total = line.lengthM
        return segments.asSequence()
            .filter { it.legal.status == LegalStatus.FORBIDDEN || it.legal.status == LegalStatus.NOT_RIDABLE }
            .filter { it.startAlongM > END_CLEARANCE_M && it.endAlongM < total - END_CLEARANCE_M }
            .mapNotNull { s ->
                // Partie centrale du segment : on n'exclut pas les intersections situées à ses extrémités.
                val inset = minOf(10.0, s.lengthM / 4)
                val pts = line.slice(s.startAlongM + inset, s.endAlongM - inset).ifEmpty { return@mapNotNull null }
                box(pts)
            }
            .take(MAX_POLYGONS)
            .toList()
    }

    /** Anneau fermé (sens anti-horaire) autour de [pts], élargi de [MARGIN_M]. */
    internal fun box(pts: List<GeoPoint>): List<GeoPoint> {
        val dLat = MARGIN_M / 111_320.0
        val midLat = pts.sumOf { it.lat } / pts.size
        val dLon = MARGIN_M / (111_320.0 * cos(Math.toRadians(midLat)))
        val s = pts.minOf { it.lat } - dLat; val n = pts.maxOf { it.lat } + dLat
        val w = pts.minOf { it.lon } - dLon; val e = pts.maxOf { it.lon } + dLon
        return listOf(GeoPoint(s, w), GeoPoint(s, e), GeoPoint(n, e), GeoPoint(n, w), GeoPoint(s, w))
    }
}
