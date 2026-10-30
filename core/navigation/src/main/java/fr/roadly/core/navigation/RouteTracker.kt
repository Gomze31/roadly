package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Suit la position sur un itinéraire (pur Kotlin, testé sur JVM). Formules : plan Phase 2,
 * « Spécification numérique de RouteTracker ». Non thread-safe : un seul appelant à la fois.
 */
class RouteTracker(private val route: Route) {
    init {
        require(route.geometry.size >= 2) { "Itinéraire sans géométrie exploitable" }
        require(route.maneuvers.isNotEmpty()) { "Itinéraire sans manœuvre" }
    }

    private val pts = route.geometry
    private val segCount = pts.size - 1
    private val segLenM = DoubleArray(segCount) { pts[it].distanceTo(pts[it + 1]) }

    /** cumDistM[i] = distance le long du tracé au point i (début du segment i) ; taille n. */
    val cumDistM: DoubleArray = DoubleArray(pts.size).also { c -> for (i in 0 until segCount) c[i + 1] = c[i] + segLenM[i] }
    val totalGeometryM: Double = cumDistM[segCount]

    private val maneuvers = route.maneuvers
    private val lastManeuver = maneuvers.lastIndex
    /** Distance le long du tracé au début de chaque manœuvre. */
    val maneuverStartM: DoubleArray = DoubleArray(maneuvers.size) { cumDistM[maneuvers[it].beginShapeIndex.coerceIn(0, segCount)] }
    private val maneuverEndM = DoubleArray(maneuvers.size) { k -> if (k < lastManeuver) maneuverStartM[k + 1] else totalGeometryM }
    private val suffixDurS = DoubleArray(maneuvers.size + 1).also { s -> for (k in lastManeuver downTo 0) s[k] = s[k + 1] + maneuvers[k].durationSeconds }
    private val distScale = if (totalGeometryM > 0.0 && route.distanceMeters > 0.0) route.distanceMeters / totalGeometryM else 1.0

    private var consecutiveOff = 0

    var isOffRoute: Boolean = false
        private set
    var hasArrived: Boolean = false
        private set
    var lastProgress: RouteProgress? = null
        private set

    fun initialProgress(): RouteProgress = progressAt(0, 0.0, 0.0, pts[0], bearing(pts[0], pts[1]).toFloat())

    fun update(fix: LocationFix): RouteProgress {
        val prev = lastProgress
        var best = if (prev == null) search(fix.point, 0, segCount - 1) else searchWindow(fix.point, prev.distanceAlongM)
        if (prev != null && best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M) {
            val global = search(fix.point, 0, segCount - 1)
            if (global.offM < best.offM) best = global
        }
        when {
            best.offM > GuidanceConstants.OFF_ROUTE_HARD_M -> { consecutiveOff++; isOffRoute = true }
            best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M -> { consecutiveOff++; isOffRoute = consecutiveOff >= GuidanceConstants.OFF_ROUTE_SOFT_FIXES }
            else -> { consecutiveOff = 0; isOffRoute = false }
        }
        val moving = (fix.speedMps ?: 0f) >= 1f
        val heading = fix.bearingDeg?.takeIf { moving } ?: bearing(pts[best.segment], pts[best.segment + 1]).toFloat()
        val p = progressAt(best.segment, best.alongM, best.offM, best.snapped, heading)
        if (!hasArrived && p.nextManeuverIndex == lastManeuver && fix.point.distanceTo(pts[segCount]) < GuidanceConstants.ARRIVAL_RADIUS_M) {
            hasArrived = true
        }
        lastProgress = p
        return p
    }

    /** Avance de [distanceM] le long du tracé depuis la dernière position (perte GPS) ; ne touche pas aux règles hors itinéraire. */
    fun extrapolate(distanceM: Double): RouteProgress {
        val prev = lastProgress
        val along = ((prev?.distanceAlongM ?: 0.0) + distanceM.coerceAtLeast(0.0)).coerceAtMost(totalGeometryM)
        val seg = segmentAt(along)
        val p = progressAt(seg, along, 0.0, pointAt(along), prev?.bearingDeg ?: bearing(pts[seg], pts[seg + 1]).toFloat())
        lastProgress = p
        return p
    }

    /** Plus grand i ∈ [0, S) tel que cumDistM[i] ≤ [distanceAlongM]. */
    fun segmentAt(distanceAlongM: Double): Int {
        var lo = 0
        var hi = segCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cumDistM[mid] <= distanceAlongM) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pointAt(distanceAlongM: Double): GeoPoint {
        val a = distanceAlongM.coerceIn(0.0, totalGeometryM)
        val i = segmentAt(a)
        val t = if (segLenM[i] > 0.0) ((a - cumDistM[i]) / segLenM[i]).coerceIn(0.0, 1.0) else 0.0
        val p = pts[i]
        val q = pts[i + 1]
        return GeoPoint(p.lat + t * (q.lat - p.lat), p.lon + t * (q.lon - p.lon))
    }

    private fun progressAt(segment: Int, along: Double, off: Double, snapped: GeoPoint, heading: Float?): RouteProgress {
        var next = lastManeuver
        for (k in maneuvers.indices) if (maneuverStartM[k] > along) { next = k; break }
        val c = (next - 1).coerceAtLeast(0)
        val span = maneuverEndM[c] - maneuverStartM[c]
        val frac = if (span > 0.0) ((maneuverEndM[c] - along) / span).coerceIn(0.0, 1.0) else 0.0
        return RouteProgress(
            routeId = route.id,
            segmentIndex = segment,
            distanceAlongM = along,
            offRouteM = off,
            nextManeuverIndex = next,
            distanceToNextManeuverM = (maneuverStartM[next] - along).coerceAtLeast(0.0),
            remainingDistanceM = (totalGeometryM - along).coerceAtLeast(0.0) * distScale,
            remainingDurationS = maneuvers[c].durationSeconds * frac + suffixDurS[c + 1],
            snapped = snapped,
            bearingDeg = heading,
        )
    }

    private class Hit(val segment: Int, val alongM: Double, val offM: Double, val snapped: GeoPoint)

    private fun search(p: GeoPoint, from: Int, to: Int): Hit {
        var best = project(p, from)
        for (i in from + 1..to) {
            val h = project(p, i)
            if (h.offM < best.offM) best = h
        }
        return best
    }

    private fun searchWindow(p: GeoPoint, lastAlong: Double): Hit {
        val center = segmentAt(lastAlong)
        var from = center
        while (from > 0 && cumDistM[from] > lastAlong - GuidanceConstants.SEARCH_BACK_M) from--
        var to = center
        while (to < segCount - 1 && cumDistM[to + 1] < lastAlong + GuidanceConstants.SEARCH_AHEAD_M) to++
        return search(p, from, to)
    }

    /** Projection orthogonale dans le plan équirectangulaire local centré sur [p]. */
    private fun project(p: GeoPoint, i: Int): Hit {
        val a = pts[i]
        val b = pts[i + 1]
        val kLat = EARTH_RADIUS_M * PI / 180.0
        val kLon = kLat * cos(p.lat * PI / 180.0)
        val ax = (a.lon - p.lon) * kLon
        val ay = (a.lat - p.lat) * kLat
        val dx = (b.lon - a.lon) * kLon
        val dy = (b.lat - a.lat) * kLat
        val dd = dx * dx + dy * dy
        val t = if (dd == 0.0) 0.0 else (-(ax * dx + ay * dy) / dd).coerceIn(0.0, 1.0)
        val qx = ax + t * dx
        val qy = ay + t * dy
        return Hit(i, cumDistM[i] + t * segLenM[i], sqrt(qx * qx + qy * qy), GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)))
    }
}

/** Cap initial (degrés, 0 = nord, sens horaire) de [a] vers [b]. */
internal fun bearing(a: GeoPoint, b: GeoPoint): Double {
    val phi1 = a.lat * PI / 180.0
    val phi2 = b.lat * PI / 180.0
    val dLon = (b.lon - a.lon) * PI / 180.0
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
}
