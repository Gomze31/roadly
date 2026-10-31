package fr.roadly.core.navigation.testing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.bearing
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Générateur déterministe de positions GPS le long d'un itinéraire (tests JVM) : vitesse constante, bruit gaussien,
 * décalage latéral, précision annoncée et trous. Même graine → mêmes fixes.
 */
class FixScript(route: Route, seed: Int = 42) {
    private val tracker = RouteTracker(route)
    private val random = Random(seed)

    /**
     * Fixes de [fromAlongM] à [toAlongM] (inclus) à [speedKmh], un toutes les [periodMs] depuis [startMs].
     * [noiseM] = écart-type du bruit gaussien (m, sur chaque axe) ; [lateralM] = décalage à gauche du sens de marche (route parallèle) ;
     * [headingOffsetDeg] = écart du cap GPS au cap du tracé ; [gaps] = rangs des fixes supprimés (perte GPS).
     */
    fun drive(
        fromAlongM: Double,
        toAlongM: Double,
        speedKmh: Double,
        noiseM: Double = 0.0,
        accuracyM: Float = 5f,
        lateralM: Double = 0.0,
        headingOffsetDeg: Double = 0.0,
        startMs: Long = 0L,
        periodMs: Long = 1_000L,
        gaps: Set<Int> = emptySet(),
    ): List<LocationFix> {
        val step = speedKmh / 3.6 * periodMs / 1000.0
        require(step > 0.0) { "Vitesse nulle : utiliser jitter()" }
        val n = ceil((toAlongM - fromAlongM) / step).toInt().coerceAtLeast(0)
        return (0..n).mapNotNull { i ->
            if (i in gaps) return@mapNotNull null
            val along = (fromAlongM + i * step).coerceAtMost(toAlongM)
            fixAt(along, noiseM, accuracyM, lateralM, headingOffsetDeg, speedKmh, startMs + i * periodMs)
        }
    }

    /** [count] fixes immobiles autour de l'abscisse [alongM] : bruit uniforme borné à ±[amplitudeM] le long du tracé et en travers. */
    fun jitter(alongM: Double, count: Int, amplitudeM: Double, accuracyM: Float = 5f, startMs: Long = 0L, periodMs: Long = 1_000L): List<LocationFix> =
        (0 until count).map { i ->
            val a = alongM + random.nextDouble(-amplitudeM, amplitudeM)
            val side = random.nextDouble(-amplitudeM, amplitudeM)
            LocationFix(offset(tracker.pointAt(a), routeBearing(a) - 90.0, side), null, 0f, accuracyM, startMs + i * periodMs)
        }

    private fun fixAt(along: Double, noiseM: Double, accuracyM: Float, lateralM: Double, headingOffsetDeg: Double, speedKmh: Double, t: Long): LocationFix {
        val b = routeBearing(along)
        var p = offset(tracker.pointAt(along), b - 90.0, lateralM)
        if (noiseM > 0.0) {
            p = offset(p, 0.0, gaussian() * noiseM)
            p = offset(p, 90.0, gaussian() * noiseM)
        }
        val heading = ((b + headingOffsetDeg) % 360.0 + 360.0) % 360.0
        return LocationFix(p, heading.toFloat(), (speedKmh / 3.6).toFloat(), accuracyM, t)
    }

    /** Cap du tracé à l'abscisse [along] (corde d'1 m vers l'avant). */
    private fun routeBearing(along: Double): Double {
        val a = along.coerceIn(0.0, (tracker.totalGeometryM - 1.0).coerceAtLeast(0.0))
        return bearing(tracker.pointAt(a), tracker.pointAt(a + 1.0))
    }

    /** [p] déplacé de [meters] dans la direction [bearingDeg] (plan local). */
    private fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        if (meters == 0.0) return p
        val rad = bearingDeg * PI / 180.0
        val dLat = meters * cos(rad) / TestRoutes.METERS_PER_DEG_LAT
        val dLon = meters * sin(rad) / (TestRoutes.METERS_PER_DEG_LAT * cos(p.lat * PI / 180.0))
        return GeoPoint(p.lat + dLat, p.lon + dLon)
    }

    /** Tirage gaussien centré réduit (Box-Muller). */
    private fun gaussian(): Double {
        val u1 = 1.0 - random.nextDouble()
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}
