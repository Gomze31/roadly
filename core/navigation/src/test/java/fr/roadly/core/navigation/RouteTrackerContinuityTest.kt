package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde de cap et de continuité du choix de segment (demi-tour, chaussées séparées, sauts GPS). */
class RouteTrackerContinuityTest {
    private val route = TestRoutes.outAndBack()
    private val lane = TestRoutes.OUT_AND_BACK_LANE_M
    private fun fix(p: GeoPoint, bearing: Float?, speedMps: Float? = 10f) = LocationFix(p, bearing, speedMps, 5f, 0L)

    /** Point de l'aller à [x] m du départ (sur l'aller, `along` = x). */
    private fun out(t: RouteTracker, x: Double) = t.pointAt(x)

    @Test fun `outbound fix with 10 m error toward the inbound leg stays on the outbound leg`() {
        val t = RouteTracker(route)
        t.update(fix(out(t, 440.0), 90f))
        val p = t.update(fix(TestRoutes.north(out(t, 450.0), lane), 90f))
        assertEquals(450.0, p.distanceAlongM, 0.5)
        assertEquals(lane, p.offRouteM, 0.5)
        assertTrue("segment de l'aller", p.segmentIndex < 12)
    }

    @Test fun `without a GPS heading the same fixes after the U-turn stay on the outbound leg`() {
        val t = RouteTracker(route)
        t.update(fix(out(t, 540.0), null, null))
        t.update(fix(out(t, 596.0), null, null))
        val p = t.update(fix(TestRoutes.north(out(t, 570.0), 3.0), null, null))
        assertTrue("sans cap, l'aller (3 m) l'emporte : segment ${p.segmentIndex}", p.segmentIndex < 12)
    }

    @Test fun `after the U-turn fixes heading west snap to the inbound leg`() {
        val t = RouteTracker(route)
        t.update(fix(out(t, 540.0), 90f))
        t.update(fix(out(t, 596.0), 90f))
        var last = 0.0
        listOf(590.0, 580.0, 570.0).forEach { x ->
            // 3 m au nord de l'aller : plus proche de l'aller (3 m) que du retour (7 m)
            val p = t.update(fix(TestRoutes.north(out(t, x), 3.0), 270f))
            assertTrue("x=$x : segment ${p.segmentIndex} doit être au demi-tour ou au retour", p.segmentIndex >= 12)
            assertTrue("x=$x : along ${p.distanceAlongM} doit progresser", p.distanceAlongM > last)
            last = p.distanceAlongM
        }
        assertEquals(600.0 + lane + 30.0, last, 1.0) // retour : demi-tour (≈ 10 m) puis 30 m vers l'ouest
        assertTrue(t.lastProgress!!.segmentIndex >= 13)
    }

    @Test fun `all candidates filtered out falls back to the unfiltered choice`() {
        val t = RouteTracker(TestRoutes.straight())
        // cap plein ouest sur un tracé plein est : tous les segments sont écartés, on garde le plus proche
        val p = t.update(fix(t.pointAt(300.0), 270f))
        assertEquals(300.0, p.distanceAlongM, 0.5)
        assertEquals(0.0, p.offRouteM, 0.5)
    }

    @Test fun `a single far fix does not move along by more than 100 m until confirmed`() {
        val paris = ParisRoute.route()
        val pts = paris.geometry
        val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
        val t = RouteTracker(paris)
        t.update(fix(pts[20], null, null))
        val held = t.update(fix(pts[280], null, null))
        assertEquals(cum[20], held.distanceAlongM, 100.0)
        assertTrue("le fix lointain compte comme hors itinéraire", held.offRouteM > GuidanceConstants.OFF_ROUTE_HARD_M)
        // un second fix concordant (à 50 m près du candidat) confirme le saut
        val confirmed = t.update(fix(pts[281], null, null))
        assertEquals(cum[281], confirmed.distanceAlongM, 0.01)
        assertEquals(0.0, confirmed.offRouteM, 0.01)
        assertFalse(t.isOffRoute)
    }

    @Test fun `an unconfirmed jump is dropped when the next fix is back near the previous position`() {
        val paris = ParisRoute.route()
        val pts = paris.geometry
        val t = RouteTracker(paris)
        t.update(fix(pts[20], null, null))
        t.update(fix(pts[280], null, null))
        val back = t.update(fix(pts[21], null, null))
        assertEquals(0.0, back.offRouteM, 0.01)
        // le candidat 280 n'est plus en attente : un nouveau saut doit être reconfirmé
        val again = t.update(fix(pts[200], null, null))
        assertTrue(again.distanceAlongM < ParisRoute.route().distanceMeters / 2)
        assertTrue(again.offRouteM > GuidanceConstants.OFF_ROUTE_HARD_M)
    }
}
