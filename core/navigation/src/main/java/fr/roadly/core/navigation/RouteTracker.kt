package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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

    /** Cap (degrés, 0 = nord) de chaque segment, plan équirectangulaire local ; NaN pour un segment de longueur nulle. */
    private val segBearingDeg = DoubleArray(segCount) { i ->
        if (segLenM[i] == 0.0) {
            Double.NaN
        } else {
            val dx = (pts[i + 1].lon - pts[i].lon) * cos(pts[i].lat * PI / 180.0)
            val dy = pts[i + 1].lat - pts[i].lat
            (atan2(dx, dy) * 180.0 / PI + 360.0) % 360.0
        }
    }

    private var consecutiveOff = 0
    /** `along` du dernier candidat global non confirmé (> 100 m de saut), en attente d'un second fix concordant. */
    private var pendingGlobal: Double? = null
    /** `along` du dernier fix réel (point de départ du plafond d'extrapolation). */
    private var lastRealAlong = 0.0
    /** Première manœuvre visée : le départ (0) est réputé effectué, sauf itinéraire réduit à une manœuvre. */
    private val firstTarget = min(1, lastManeuver)
    /** B1 : indice de la prochaine manœuvre retenu, monotone (voir [advanceIndex]). */
    private var committedNext = firstTarget
    /** Plus grande abscisse atteinte depuis le dernier changement d'indice : référence du recul confirmé. */
    private var peakAlong = 0.0
    /** Fixes réels consécutifs sous `peakAlong` − 30 m : le recul de l'indice est confirmé à 2. */
    private var backtrackFixes = 0
    /** Précision du dernier fix réel : marge de passage reprise par l'extrapolation. */
    private var lastAccuracyM = 0f

    var isOffRoute: Boolean = false
        private set
    var hasArrived: Boolean = false
        private set
    var lastProgress: RouteProgress? = null
        private set

    fun initialProgress(): RouteProgress = progressAt(0, 0.0, 0.0, pts[0], bearing(pts[0], pts[1]).toFloat(), firstTarget)

    fun update(fix: LocationFix): RouteProgress {
        val prev = lastProgress
        val lastAlong = prev?.distanceAlongM
        val gpsHeading = fix.bearingDeg?.takeIf { (fix.speedMps ?: 0f) >= GuidanceConstants.HEADING_FILTER_MIN_SPEED_MPS }
        var best = if (lastAlong == null) {
            search(fix.point, 0, segCount - 1, gpsHeading, null)
        } else {
            searchWindow(fix.point, lastAlong, gpsHeading)
        }
        if (lastAlong != null && best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M) {
            val global = search(fix.point, 0, segCount - 1, gpsHeading, lastAlong)
            val pending = pendingGlobal
            best = when {
                global.offM >= best.offM -> { pendingGlobal = null; best }
                abs(global.alongM - lastAlong) <= GuidanceConstants.GLOBAL_JUMP_M -> { pendingGlobal = null; global }
                pending != null && abs(global.alongM - pending) <= GuidanceConstants.GLOBAL_CONFIRM_RADIUS_M -> { pendingGlobal = null; global }
                else -> { pendingGlobal = global.alongM; hold(fix.point, lastAlong) }
            }
        } else {
            pendingGlobal = null
        }
        when {
            best.offM > GuidanceConstants.OFF_ROUTE_HARD_M && fix.accuracyM <= GuidanceConstants.HARD_RULE_MAX_ACCURACY_M -> {
                consecutiveOff++; isOffRoute = true
            }
            best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M -> {
                consecutiveOff++; isOffRoute = consecutiveOff >= GuidanceConstants.OFF_ROUTE_SOFT_FIXES
            }
            else -> { consecutiveOff = 0; isOffRoute = false }
        }
        val moving = (fix.speedMps ?: 0f) >= 1f
        val heading = fix.bearingDeg?.takeIf { moving } ?: bearing(pts[best.segment], pts[best.segment + 1]).toFloat()
        lastAccuracyM = fix.accuracyM
        val p = progressAt(best.segment, best.alongM, best.offM, best.snapped, heading, advanceIndex(best.alongM, fix.accuracyM, real = true))
        val onLast = p.nextManeuverIndex == lastManeuver
        val nearDestination = fix.point.distanceTo(pts[segCount]) < GuidanceConstants.ARRIVAL_RADIUS_M
        val alongTheEnd = p.distanceAlongM >= totalGeometryM - GuidanceConstants.ARRIVAL_RADIUS_M &&
            p.offRouteM <= GuidanceConstants.OFF_ROUTE_SOFT_M
        if (!hasArrived && onLast && (nearDestination || alongTheEnd)) hasArrived = true
        lastRealAlong = p.distanceAlongM
        lastProgress = p
        return p
    }

    /**
     * Avance de [distanceM] le long du tracé depuis la dernière position (perte GPS) ; ne touche pas aux règles hors itinéraire.
     * Plafonné à [GuidanceConstants.EXTRAPOLATION_MAX_M] après le dernier fix réel, et inopérant au-delà de
     * [GuidanceConstants.EXTRAPOLATION_MAX_MS] ([sinceLastFixMs] = temps écoulé depuis ce fix) : la dernière progression est alors rendue telle quelle.
     */
    fun extrapolate(distanceM: Double, sinceLastFixMs: Long = 0L): RouteProgress {
        val prev = lastProgress
        if (sinceLastFixMs > GuidanceConstants.EXTRAPOLATION_MAX_MS) return prev ?: initialProgress()
        val cap = min(lastRealAlong + GuidanceConstants.EXTRAPOLATION_MAX_M, totalGeometryM)
        val along = ((prev?.distanceAlongM ?: 0.0) + distanceM.coerceAtLeast(0.0)).coerceAtMost(cap)
        if (prev != null && prev.extrapolated && along <= prev.distanceAlongM) return prev
        val seg = segmentAt(along)
        val p = progressAt(seg, along, 0.0, pointAt(along), prev?.bearingDeg ?: bearing(pts[seg], pts[seg + 1]).toFloat(), advanceIndex(along, lastAccuracyM, real = false))
            .copy(extrapolated = true)
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

    /**
     * B1 : prochaine manœuvre, monotone. Elle avance quand `along` dépasse son début de [passingMarginM] ; elle ne recule que sur un recul
     * confirmé (> 30 m sous l'abscisse maximale atteinte, 2 fixes réels consécutifs). Une position extrapolée ([real] faux) ne peut que faire
     * avancer l'indice et ne touche pas au compteur de recul. Un recalcul crée un nouveau suivi (l'indice repart de la première cible).
     */
    private fun advanceIndex(along: Double, accuracyM: Float, real: Boolean): Int {
        var raw = firstTarget
        while (raw < lastManeuver && along > maneuverStartM[raw] + passingMarginM(raw, accuracyM)) raw++
        when {
            raw > committedNext -> { committedNext = raw; peakAlong = along; backtrackFixes = 0 }
            !real -> Unit
            raw < committedNext && along < peakAlong - GuidanceConstants.BACKTRACK_CONFIRM_M -> {
                backtrackFixes++
                if (backtrackFixes >= GuidanceConstants.BACKTRACK_CONFIRM_FIXES) { committedNext = raw; peakAlong = along; backtrackFixes = 0 }
            }
            else -> backtrackFixes = 0
        }
        peakAlong = max(peakAlong, along)
        return committedNext
    }

    /** Marge de passage de la manœuvre [k] : max(10 m, 2 × précision), plafonnée à 60 m et à la moitié de son tronçon (jusqu'à la suivante ou la fin du tracé). */
    private fun passingMarginM(k: Int, accuracyM: Float): Double = minOf(
        max(GuidanceConstants.MANEUVER_PASSED_MIN_M, GuidanceConstants.MANEUVER_PASSED_ACCURACY_FACTOR * accuracyM),
        GuidanceConstants.MANEUVER_PASSED_MAX_M,
        (maneuverEndM[k] - maneuverStartM[k]) / 2.0,
    )

    private fun progressAt(segment: Int, along: Double, off: Double, snapped: GeoPoint, heading: Float?, next: Int): RouteProgress {
        val c = (next - 1).coerceAtLeast(0)
        val span = maneuverEndM[c] - maneuverStartM[c]
        val frac = if (span > 0.0) ((maneuverEndM[c] - along) / span).coerceIn(0.0, 1.0) else 0.0
        return RouteProgress(
            routeId = route.id,
            segmentIndex = segment,
            distanceAlongM = along,
            offRouteM = off,
            nextManeuverIndex = next,
            distanceToNextManeuverM = distanceTo(next, along),
            distancesToManeuversM = (next + 1..min(next + GuidanceConstants.UPCOMING_COUNT, lastManeuver)).map { distanceTo(it, along) },
            remainingDistanceM = (totalGeometryM - along).coerceAtLeast(0.0) * distScale,
            remainingDurationS = maneuvers[c].durationSeconds * frac + suffixDurS[c + 1],
            snapped = snapped,
            bearingDeg = heading,
        )
    }

    /** B2 : distance (m) de [along] au début de la manœuvre [k], le long du tracé mis à l'échelle de la longueur Valhalla (`distScale`). */
    private fun distanceTo(k: Int, along: Double): Double = (maneuverStartM[k] - along).coerceAtLeast(0.0) * distScale

    private class Hit(val segment: Int, val alongM: Double, val offM: Double, val snapped: GeoPoint)

    /** Garde la position [lastAlong] (candidat global non confirmé) ; l'écart est mesuré à ce point. */
    private fun hold(p: GeoPoint, lastAlong: Double): Hit {
        val snapped = pointAt(lastAlong)
        return Hit(segmentAt(lastAlong), lastAlong.coerceIn(0.0, totalGeometryM), p.distanceTo(snapped), snapped)
    }

    /** Vrai si le segment [i] est à au plus 90° du cap [headingDeg] (un segment nul est toujours accepté). */
    private fun headingOk(i: Int, headingDeg: Float): Boolean {
        val b = segBearingDeg[i]
        if (b.isNaN()) return true
        return abs(((b - headingDeg + 540.0) % 360.0) - 180.0) <= GuidanceConstants.HEADING_MAX_DIFF_DEG
    }

    /**
     * Meilleur segment de [from]..[to]. Avec un cap GPS [headingDeg], les segments à plus de 90° sont écartés (tous écartés : pas de filtre).
     * À distance perpendiculaire quasi égale (< 10 m), on préfère, pour un candidat d'un autre tronçon (> 10 m le long du tracé),
     * celui dont `along` est le plus proche de [lastAlong].
     */
    private fun search(p: GeoPoint, from: Int, to: Int, headingDeg: Float?, lastAlong: Double?): Hit {
        val all = ArrayList<Hit>(to - from + 1)
        for (i in from..to) all += project(p, i)
        val pool = if (headingDeg == null) all else all.filter { headingOk(it.segment, headingDeg) }.ifEmpty { all }
        var best = pool.minBy { it.offM }
        if (lastAlong != null) {
            val alt = pool
                .filter { it.offM < best.offM + GuidanceConstants.SEGMENT_TIE_M && abs(it.alongM - best.alongM) > GuidanceConstants.SEGMENT_TIE_M }
                .minByOrNull { abs(it.alongM - lastAlong) }
            if (alt != null && abs(alt.alongM - lastAlong) < abs(best.alongM - lastAlong)) best = alt
        }
        return best
    }

    private fun searchWindow(p: GeoPoint, lastAlong: Double, headingDeg: Float?): Hit {
        val center = segmentAt(lastAlong)
        var from = center
        while (from > 0 && cumDistM[from] > lastAlong - GuidanceConstants.SEARCH_BACK_M) from--
        var to = center
        while (to < segCount - 1 && cumDistM[to + 1] < lastAlong + GuidanceConstants.SEARCH_AHEAD_M) to++
        return search(p, from, to, headingDeg, lastAlong)
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
