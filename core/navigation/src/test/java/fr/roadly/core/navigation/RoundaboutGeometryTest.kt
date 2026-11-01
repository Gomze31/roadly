package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.navigation.testing.TestRoutes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** D2 sur données réelles (scénarios 4 et 5) : arc parcouru sur l'anneau, tel que dessiné par `RoundaboutManeuverView`. */
class RoundaboutGeometryTest {
    private val paris = ParisRoute.route()

    /** Anneau parfait de [n] cordes, rayon 15 m, entrée au sud, parcouru dans le sens antihoraire sur [totalDeg]. */
    private fun circle(totalDeg: Double, n: Int = 36): List<GeoPoint> {
        val lat0 = 43.6045; val lon0 = 1.4440; val r = 15.0
        val mLat = 111_320.0; val mLon = 111_320.0 * cos(Math.toRadians(lat0))
        return (0..n).map { k ->
            val phi = Math.toRadians(-90.0 + totalDeg * k / n)
            GeoPoint(lat0 + r * sin(phi) / mLat, lon0 + r * cos(phi) / mLon)
        }
    }

    /** Index de la manœuvre d'entrée (26) suivie d'une sortie (27) dont le rang de sortie vaut [exitCount] (fixture autoroute). */
    private fun motorwayEntry(exitCount: Int): Int {
        val raw = MotorwayRoute.rawManeuvers
        return raw.indices.first { i ->
            raw[i]["type"]!!.jsonPrimitive.int == 26 && (raw[i]["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull == exitCount &&
                raw.getOrNull(i + 1)?.get("type")?.jsonPrimitive?.int == 27
        }
    }

    @Test fun `a perfect ring - the sweep is the heading turned between the first and the last chord`() {
        assertEquals(270.0 * 35 / 36, RoundaboutGeometry.sweepFromRing(circle(270.0))!!, 0.2)
        assertEquals(90.0 * 35 / 36, RoundaboutGeometry.sweepFromRing(circle(90.0))!!, 0.2)
    }

    @Test fun `champs-elysees - second exit straight on, arc of 168 degrees (scenario 4)`() {
        val info = RoundaboutResolver.resolve(paris, 10)
        assertEquals(168.1, RoundaboutGeometry.sweepFromRing(info.ringGeometry)!!, 0.5)
        assertEquals(168.1, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.5)
    }

    @Test fun `etoile - fifth exit, ring up to the arrival, no exit branch`() {
        val info = RoundaboutResolver.resolve(paris, 12)
        assertEquals(167.8, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.5)
        assertNull("pas de manœuvre de sortie : pas de branche dessinée", info.exitBearingDeg)
    }

    @Test fun `motorway third exit - the arc passes the top although the exit bearing reads +140 (scenario 5)`() {
        val info = RoundaboutResolver.resolve(MotorwayRoute.route(), motorwayEntry(3))
        assertEquals(140.0, info.exitAngleDeg!!, 0.0)
        assertEquals(281.9, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.5)
    }

    @Test fun `motorway first exit - a short arc is widened so the exit never sits on the entry`() {
        val info = RoundaboutResolver.resolve(MotorwayRoute.route(), motorwayEntry(1))
        assertEquals(20.3, RoundaboutGeometry.sweepFromRing(info.ringGeometry)!!, 0.5)
        assertEquals(RoundaboutGeometry.MIN_DIAGRAM_SWEEP_DEG, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.0)
    }

    @Test fun `a straight ring gives no sweep and, without bearings, only the ring is drawn`() {
        val info = RoundaboutResolver.resolve(TestRoutes.twoRoundabouts(), 1)
        assertNull(RoundaboutGeometry.sweepFromRing(info.ringGeometry))
        assertNull(RoundaboutGeometry.diagramSweepDeg(info))
    }

    @Test fun `without a curved ring, the bearings give the arc - right, left, u-turn`() {
        val straightRing = TestRoutes.twoRoundabouts().geometry.subList(20, 24)
        fun info(exitBearing: Double) = RoundaboutInfo(null, 2, 0.0, exitBearing, straightRing, null)
        assertEquals(90.0, RoundaboutGeometry.diagramSweepDeg(info(90.0))!!, 1e-9)
        assertEquals(270.0, RoundaboutGeometry.diagramSweepDeg(info(270.0))!!, 1e-9)
        assertEquals("demi-tour : ±180° lu à gauche, arc maximal", RoundaboutGeometry.MAX_DIAGRAM_SWEEP_DEG, RoundaboutGeometry.diagramSweepDeg(info(180.0))!!, 1e-9)
    }

    @Test fun `repeated points are ignored`() {
        val ring = circle(180.0, n = 12)
        assertEquals(RoundaboutGeometry.sweepFromRing(ring)!!, RoundaboutGeometry.sweepFromRing(ring.flatMap { listOf(it, it) })!!, 1e-9)
    }
}
