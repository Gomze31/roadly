package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import kotlin.math.cos
import kotlin.math.sqrt

/** Outils de géométrie sur une polyligne : abscisses curvilignes et projection d'un point. */
class RouteGeometry(val points: List<GeoPoint>) {
    /** Distance cumulée (m) au début de chaque point ; `cumulative[i]` = abscisse de `points[i]`. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + points[i - 1].distanceTo(points[i])
    }
    val totalM: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    /** Point à l'abscisse [alongM] (bornée au tracé), par interpolation sur le segment qui la contient. */
    fun pointAt(alongM: Double): GeoPoint {
        if (points.size < 2) return points.first()
        val d = alongM.coerceIn(0.0, totalM)
        var i = java.util.Arrays.binarySearch(cumulative, d).let { if (it >= 0) it else -it - 2 }.coerceIn(0, points.size - 2)
        while (i < points.size - 2 && cumulative[i + 1] < d) i++
        val len = cumulative[i + 1] - cumulative[i]
        val t = if (len <= 0.0) 0.0 else (d - cumulative[i]) / len
        val a = points[i]; val b = points[i + 1]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /** Projection équirectangulaire locale : abscisse curviligne (m) et écart perpendiculaire (m) du point le plus proche. */
    fun project(p: GeoPoint): Projection? {
        if (points.size < 2) return null
        var best: Projection? = null
        val k = cos(Math.toRadians(p.lat))
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val ax = (a.lon - p.lon) * k; val ay = a.lat - p.lat
            val bx = (b.lon - p.lon) * k; val by = b.lat - p.lat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val offM = sqrt(px * px + py * py) * DEG_M
            if (best == null || offM < best.offsetM) best = Projection(cumulative[i] + t * (cumulative[i + 1] - cumulative[i]), offM, i)
        }
        return best
    }

    data class Projection(val alongM: Double, val offsetM: Double, val segmentIndex: Int)

    private companion object { const val DEG_M = 111_320.0 }
}
