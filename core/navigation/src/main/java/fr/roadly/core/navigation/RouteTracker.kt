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
private const val K_LAT = EARTH_RADIUS_M * PI / 180.0

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
    /** B6 : fixes consécutifs dont le cap GPS s'écarte de plus de 50° du tracé. */
    private var consecutiveHeadingOff = 0
    /** `along` du dernier candidat global non confirmé (> 100 m de saut), en attente d'un second fix concordant. */
    private var pendingGlobal: Double? = null
    /** `along` du dernier fix réel (point de départ du plafond d'extrapolation). */
    private var lastRealAlong = 0.0
    /** Première manœuvre visée : le départ (0) est réputé effectué, sauf itinéraire réduit à une manœuvre. */
    private val firstTarget = min(1, lastManeuver)
    /** B1 : indice de la prochaine manœuvre retenu, monotone (voir [advanceIndex]). */
    private var committedNext = firstTarget
    /** Indice avancé par l'extrapolation, au-delà de [committedNext] : provisoire, oublié au premier fix réel (voir [advanceIndex]). */
    private var provisionalNext: Int? = null
    /** Plus grande abscisse réelle atteinte depuis le dernier changement d'indice : référence du recul confirmé (l'extrapolation n'y touche pas). */
    private var peakAlong = 0.0
    /** Fixes réels consécutifs sous `peakAlong` − 30 m : le recul de l'indice est confirmé à 2. */
    private var backtrackFixes = 0
    /** Précision du dernier fix réel : marge de passage reprise par l'extrapolation. */
    private var lastAccuracyM = 0f
    /** Horodatage et vitesse du dernier fix réel : bornent l'avance plausible du fix suivant (voir [maxForwardAlong]). */
    private var lastFixTimeMs = 0L
    private var lastSpeedMps = 0f

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
        val maxAlong = if (lastAlong == null) Double.MAX_VALUE else maxForwardAlong(lastAlong, fix)
        var best = if (lastAlong == null) {
            acquire(fix.point, gpsHeading)
        } else {
            searchWindow(fix.point, lastAlong, gpsHeading, maxAlong)
        }
        // Position gardée en attente d'un second fix concordant : elle ne dit rien de l'écart réel au tracé.
        var held = false
        if (lastAlong != null && best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M) {
            val global = search(fix.point, 0, segCount - 1, gpsHeading, lastAlong, maxAlong)
            val pending = pendingGlobal
            best = when {
                global.offM >= best.offM -> { pendingGlobal = null; best }
                abs(global.alongM - lastAlong) <= GuidanceConstants.GLOBAL_JUMP_M -> { pendingGlobal = null; global }
                pending != null && abs(global.alongM - pending) <= GuidanceConstants.GLOBAL_CONFIRM_RADIUS_M -> { pendingGlobal = null; global }
                else -> { pendingGlobal = global.alongM; held = true; hold(fix.point, lastAlong) }
            }
        } else {
            pendingGlobal = null
        }
        // B6 : un fix imprécis (> 30 m) ne change aucune décision « hors itinéraire » ; une position en attente de confirmation non plus
        // (sortie d'un long tunnel : le premier fix est loin du dernier point connu, mais sur le tracé).
        if (fix.accuracyM <= GuidanceConstants.OFF_ROUTE_MAX_ACCURACY_M && !held) updateOffRoute(best, gpsHeading)
        val moving = (fix.speedMps ?: 0f) >= 1f
        val heading = fix.bearingDeg?.takeIf { moving } ?: bearing(pts[best.segment], pts[best.segment + 1]).toFloat()
        lastAccuracyM = fix.accuracyM
        val p = progressAt(best.segment, best.alongM, best.offM, best.snapped, heading, advanceIndex(best.alongM, fix.accuracyM, real = true), fix.accuracyM)
        val onLast = p.nextManeuverIndex == lastManeuver
        val nearDestination = fix.point.distanceTo(pts[segCount]) < GuidanceConstants.ARRIVAL_RADIUS_M
        val alongTheEnd = p.distanceAlongM >= totalGeometryM - GuidanceConstants.ARRIVAL_RADIUS_M &&
            p.offRouteM <= GuidanceConstants.OFF_ROUTE_SOFT_M
        // Un fix imprécis (> 30 m) posé près de la destination ne prouve pas qu'on y est : avec 120 m de précision, l'arrivée était
        // déclarée 290 m trop tôt et le guidage s'arrêtait. L'arrivée attend un fix exploitable, comme la décision « hors itinéraire ».
        val trusted = fix.accuracyM <= GuidanceConstants.OFF_ROUTE_MAX_ACCURACY_M
        if (!hasArrived && trusted && onLast && (nearDestination || alongTheEnd)) hasArrived = true
        lastRealAlong = p.distanceAlongM
        lastFixTimeMs = fix.timestampMs
        lastSpeedMps = fix.speedMps ?: 0f
        lastProgress = p
        return p
    }

    /**
     * Abscisse maximale plausible pour [fix] : la distance que le véhicule a pu parcourir depuis le dernier fix réel (2 × la plus
     * grande des deux vitesses, au moins le pas), plus la précision et une marge. Au-delà, un candidat n'est pas « un peu plus loin » :
     * c'est un autre passage du tracé (retour d'un demi-tour, chaussée d'en face, boucle) qu'une dérive GPS a rapproché. Sans cette
     * borne, un véhicule arrêté en face de sa destination, de l'autre côté d'un terre-plein, était déclaré arrivé sur 18 m de dérive.
     * La borne ne joue qu'au pas ou à l'arrêt : dès que le véhicule roule, le cap GPS écarte déjà les passages en sens inverse, et un
     * vrai saut vers l'avant (raccourci, reprise) doit rester possible. Sans vitesse connue, pas de borne.
     */
    private fun maxForwardAlong(lastAlong: Double, fix: LocationFix): Double {
        val speed = fix.speedMps ?: return Double.MAX_VALUE
        if (max(speed, lastSpeedMps) >= GuidanceConstants.HEADING_FILTER_MIN_SPEED_MPS) return Double.MAX_VALUE
        val dtS = ((fix.timestampMs - lastFixTimeMs) / 1000.0).coerceAtLeast(1.0)
        val v = max(max(speed, lastSpeedMps), GuidanceConstants.HEADING_FILTER_MIN_SPEED_MPS).toDouble()
        return lastAlong + v * dtS * FORWARD_SPEED_FACTOR + fix.accuracyM + FORWARD_MARGIN_M
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
        val p = progressAt(seg, along, 0.0, pointAt(along), prev?.bearingDeg ?: bearing(pts[seg], pts[seg + 1]).toFloat(), advanceIndex(along, lastAccuracyM, real = false), lastAccuracyM)
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
     * confirmé (> 30 m sous l'abscisse réelle maximale atteinte, 2 fixes réels consécutifs). Une position extrapolée ([real] faux) ne fait
     * avancer l'indice qu'à titre provisoire ([provisionalNext]) : la vitesse lissée retarde au freinage et l'extrapolation peut dépasser un
     * virage que le conducteur n'a pas encore pris. Elle ne touche ni à l'abscisse maximale ni au compteur de recul ; le premier fix réel
     * oublie l'avance provisoire et décide seul depuis l'indice confirmé (sans confirmation à 2 fixes), si bien que le NOW du virage reste dû.
     * Un recalcul crée un nouveau suivi (l'indice repart de la première cible).
     */
    private fun advanceIndex(along: Double, accuracyM: Float, real: Boolean): Int {
        var raw = firstTarget
        while (raw < lastManeuver && along > maneuverStartM[raw] + passingMarginM(raw, accuracyM)) raw++
        if (!real) {
            if (raw > (provisionalNext ?: committedNext)) provisionalNext = raw
            return provisionalNext ?: committedNext
        }
        provisionalNext = null
        when {
            raw > committedNext -> { committedNext = raw; peakAlong = along; backtrackFixes = 0 }
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

    /** [accuracyM] : précision du fix réel d'origine, transmise à la politique d'annonce (pas de NOW sur un fix imprécis, B6). */
    private fun progressAt(segment: Int, along: Double, off: Double, snapped: GeoPoint, heading: Float?, next: Int, accuracyM: Float = 0f): RouteProgress {
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
            accuracyM = accuracyM,
        )
    }

    /** B2 : distance (m) de [along] au début de la manœuvre [k], le long du tracé mis à l'échelle de la longueur Valhalla (`distScale`). */
    private fun distanceTo(k: Int, along: Double): Double = (maneuverStartM[k] - along).coerceAtLeast(0.0) * distScale

    /** [interior] = projection strictement à l'intérieur du segment (pas sur une extrémité). */
    private companion object {
        /** Portée (m) de l'origine du tracé à la première position : au-delà, le véhicule n'est pas au départ. */
        const val ACQUIRE_ORIGIN_M = 60.0
        /** Avance plausible entre deux fixes : vitesse × durée × ce facteur + précision + [FORWARD_MARGIN_M]. */
        const val FORWARD_SPEED_FACTOR = 2.0
        const val FORWARD_MARGIN_M = 30.0
    }

    private class Hit(val segment: Int, val alongM: Double, val offM: Double, val snapped: GeoPoint, val interior: Boolean = false)

    /**
     * B6 : hors itinéraire si > 100 m une fois, ou > 40 m sur 5 fixes consécutifs **et** cap écarté de plus de 50° sur 3 fixes
     * consécutifs : une route parallèle suivie dans le même sens n'est pas une sortie d'itinéraire.
     */
    private fun updateOffRoute(best: Hit, gpsHeading: Float?) {
        val deviates = gpsHeading != null && headingDiffDeg(best.segment, gpsHeading) > GuidanceConstants.OFF_ROUTE_HEADING_DEG
        consecutiveHeadingOff = if (deviates) consecutiveHeadingOff + 1 else 0
        when {
            best.offM > GuidanceConstants.OFF_ROUTE_HARD_M -> { consecutiveOff++; isOffRoute = true }
            best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M -> {
                consecutiveOff++
                isOffRoute = consecutiveOff >= GuidanceConstants.OFF_ROUTE_SOFT_FIXES &&
                    consecutiveHeadingOff >= GuidanceConstants.OFF_ROUTE_HEADING_FIXES
            }
            else -> { consecutiveOff = 0; isOffRoute = false }
        }
    }

    /** Garde la position [lastAlong] (candidat global non confirmé) ; l'écart est mesuré à ce point. */
    /**
     * Première position sur le tracé. Un tracé commence là où se trouve le véhicule (départ, recalcul) : si l'**origine** du tracé
     * est à portée, c'est elle qui est retenue, quel que soit le cap. Sans cela, une recherche sur tout le tracé filtrée par le
     * cap accrochait le passage de retour d'un demi-tour ou la fin d'une boucle — des centaines de mètres plus loin, avec la
     * manœuvre suivante annoncée aussitôt, voire l'arrivée déclarée au départ. Le cap n'écarte pas l'origine : après un recalcul,
     * le véhicule roule souvent encore dans l'ancien sens.
     *
     * Reprise en cours de trajet (processus relancé) : l'origine est loin, la recherche globale garde la main — y compris quand
     * le véhicule longe le début du tracé en sens inverse (retour d'un aller-retour).
     */
    private fun acquire(p: GeoPoint, gpsHeading: Float?): Hit {
        val global = search(p, 0, segCount - 1, gpsHeading, null)
        if (global.alongM <= ACQUIRE_ORIGIN_M) return global
        val origin = search(p, 0, segmentAt(min(ACQUIRE_ORIGIN_M, totalGeometryM)), null, null)
        val atOrigin = origin.alongM <= ACQUIRE_ORIGIN_M && origin.offM <= GuidanceConstants.OFF_ROUTE_SOFT_M &&
            origin.offM <= global.offM + GuidanceConstants.CONTINUITY_TIE_M
        return if (atOrigin) origin else global
    }

    private fun hold(p: GeoPoint, lastAlong: Double): Hit {
        val snapped = pointAt(lastAlong)
        return Hit(segmentAt(lastAlong), lastAlong.coerceIn(0.0, totalGeometryM), p.distanceTo(snapped), snapped)
    }

    /** Écart (0..180°) entre le cap du segment [i] et [headingDeg] ; 0 pour un segment nul. */
    private fun headingDiffDeg(i: Int, headingDeg: Float): Double {
        val b = segBearingDeg[i]
        if (b.isNaN()) return 0.0
        return abs(((b - headingDeg + 540.0) % 360.0) - 180.0)
    }

    /** Vrai si le segment [i] est à au plus 90° du cap [headingDeg] (un segment nul est toujours accepté). */
    private fun headingOk(i: Int, headingDeg: Float): Boolean = headingDiffDeg(i, headingDeg) <= GuidanceConstants.HEADING_MAX_DIFF_DEG

    /**
     * Meilleur segment de [from]..[to]. Avec un cap GPS [headingDeg], les segments à plus de 90° sont écartés (tous écartés : pas de filtre).
     * À distance perpendiculaire quasi égale (≤ 15 m), on préfère, pour un candidat d'un autre passage (> 50 m le long du tracé,
     * projection intérieure, recul ≤ 30 m), celui dont `along` est le plus proche de [lastAlong] (B6).
     */
    private fun search(p: GeoPoint, from: Int, to: Int, headingDeg: Float?, lastAlong: Double?, maxAlong: Double = Double.MAX_VALUE): Hit {
        // Sans allocation par segment : hors du tracé de plus de 40 m, cette recherche parcourt tout le tracé à chaque position
        // (mesuré sur 50 000 points : 4,5 Mo alloués par position avec une liste de candidats ; voir RouteTrackerScanCostTest).
        val kLon = K_LAT * cos(p.lat * PI / 180.0)
        val bounded = maxAlong != Double.MAX_VALUE
        // Meilleur écart dans chacun des quatre ensembles possibles : tous, atteignables, bon cap, atteignables et bon cap.
        var iAll = -1; var oAll = Double.MAX_VALUE
        var iReach = -1; var oReach = Double.MAX_VALUE
        var iHead = -1; var oHead = Double.MAX_VALUE
        var iBoth = -1; var oBoth = Double.MAX_VALUE
        for (i in from..to) {
            measure(p, i, kLon)
            val reach = !bounded || mAlong <= maxAlong
            val ok = headingDeg == null || headingOk(i, headingDeg)
            if (mOff < oAll) { oAll = mOff; iAll = i }
            if (reach && mOff < oReach) { oReach = mOff; iReach = i }
            if (ok && mOff < oHead) { oHead = mOff; iHead = i }
            if (reach && ok && mOff < oBoth) { oBoth = mOff; iBoth = i }
        }
        // Chaque filtre est abandonné s'il écarte tout (aucun candidat atteignable, puis aucun au bon cap parmi ceux retenus).
        val useReach = bounded && iReach >= 0
        val useHeading = headingDeg != null && (if (useReach) iBoth >= 0 else iHead >= 0)
        var bestI = when {
            useReach && useHeading -> iBoth
            useReach -> iReach
            useHeading -> iHead
            else -> iAll
        }
        if (lastAlong != null) {
            // B6 : continuité plutôt que distance brute (pont, échangeur, chaussées séparées) ; une extrémité de segment voisin n'est pas
            // un autre passage, et un candidat qui recule de plus de 30 m est écarté.
            measure(p, bestI, kLon)
            val bestAlong = mAlong
            val bestOff = mOff
            var altI = -1
            var altGap = Double.MAX_VALUE
            for (i in from..to) {
                measure(p, i, kLon)
                if (useReach && mAlong > maxAlong) continue
                if (useHeading && !headingOk(i, headingDeg!!)) continue
                if (!mInterior || mOff > bestOff + GuidanceConstants.CONTINUITY_TIE_M) continue
                if (abs(mAlong - bestAlong) <= GuidanceConstants.CONTINUITY_MIN_GAP_M || mAlong < lastAlong - GuidanceConstants.CONTINUITY_BACK_M) continue
                val gap = abs(mAlong - lastAlong)
                if (gap < altGap) { altGap = gap; altI = i }
            }
            if (altI >= 0 && altGap < abs(bestAlong - lastAlong)) bestI = altI
        }
        return project(p, bestI)
    }

    private fun searchWindow(p: GeoPoint, lastAlong: Double, headingDeg: Float?, maxAlong: Double): Hit {
        val center = segmentAt(lastAlong)
        var from = center
        while (from > 0 && cumDistM[from] > lastAlong - GuidanceConstants.SEARCH_BACK_M) from--
        var to = center
        while (to < segCount - 1 && cumDistM[to + 1] < lastAlong + GuidanceConstants.SEARCH_AHEAD_M) to++
        return search(p, from, to, headingDeg, lastAlong, maxAlong)
    }

    /** Résultat de [measure] (le suivi n'a qu'un appelant à la fois) : abscisse, écart, fraction le long du segment, projection intérieure. */
    private var mAlong = 0.0
    private var mOff = 0.0
    private var mT = 0.0
    private var mInterior = false

    /** Projection orthogonale de [p] sur le segment [i], plan équirectangulaire local centré sur [p] ; résultat dans `m*`, sans allocation. */
    private fun measure(p: GeoPoint, i: Int, kLon: Double) {
        val a = pts[i]
        val b = pts[i + 1]
        val ax = (a.lon - p.lon) * kLon
        val ay = (a.lat - p.lat) * K_LAT
        val dx = (b.lon - a.lon) * kLon
        val dy = (b.lat - a.lat) * K_LAT
        val dd = dx * dx + dy * dy
        val t = if (dd == 0.0) 0.0 else (-(ax * dx + ay * dy) / dd).coerceIn(0.0, 1.0)
        val qx = ax + t * dx
        val qy = ay + t * dy
        mT = t
        mAlong = cumDistM[i] + t * segLenM[i]
        mOff = sqrt(qx * qx + qy * qy)
        mInterior = t > 0.0 && t < 1.0
    }

    /** Candidat complet (avec le point projeté) du segment [i]. */
    private fun project(p: GeoPoint, i: Int): Hit {
        measure(p, i, K_LAT * cos(p.lat * PI / 180.0))
        val a = pts[i]
        val b = pts[i + 1]
        return Hit(i, mAlong, mOff, GeoPoint(a.lat + mT * (b.lat - a.lat), a.lon + mT * (b.lon - a.lon)), interior = mInterior)
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
