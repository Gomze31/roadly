package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B1 : progression monotone, marge de passage, recul confirmé (scénarios 2 et 6). */
class RouteTrackerProgressTest {
    private val route = TestRoutes.straight()
    private fun fixAt(t: RouteTracker, along: Double, accuracyM: Float = 5f) = LocationFix(t.pointAt(along), null, null, accuracyM, 0L)

    @Test fun `jitter of 8 m around the maneuver point keeps the index for 10 fixes`() {
        val t = RouteTracker(route)
        val script = FixScript(route, seed = 7)
        val turnAt = t.maneuverStartM[1]
        script.drive(turnAt - 200.0, turnAt - 40.0, speedKmh = 30.0).forEach { t.update(it) }
        val indices = script.jitter(turnAt, count = 10, amplitudeM = 8.0).map { t.update(it).nextManeuverIndex }
        assertEquals(List(10) { 1 }, indices)
    }

    @Test fun `a maneuver is passed only 10 m beyond its point`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        assertEquals(1, t.update(fixAt(t, turnAt + 9.0)).nextManeuverIndex)
        assertEquals(2, t.update(fixAt(t, turnAt + 11.0)).nextManeuverIndex)
    }

    @Test fun `the passing margin is twice the accuracy when it exceeds 10 m`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        assertEquals(1, t.update(fixAt(t, turnAt + 30.0, accuracyM = 20f)).nextManeuverIndex)
        assertEquals(2, t.update(fixAt(t, turnAt + 41.0, accuracyM = 20f)).nextManeuverIndex)
    }

    @Test fun `once passed, jitter back into the margin never brings the index back`() {
        val t = RouteTracker(route)
        val script = FixScript(route, seed = 11)
        val turnAt = t.maneuverStartM[1]
        script.drive(turnAt - 100.0, turnAt + 30.0, speedKmh = 30.0).forEach { t.update(it) }
        assertEquals(2, t.lastProgress!!.nextManeuverIndex)
        // ±8 m autour de +15 m : une partie des fixes retombe dans la marge de 10 m, l'indice reste 2.
        val indices = script.jitter(turnAt + 15.0, count = 10, amplitudeM = 8.0).map { t.update(it).nextManeuverIndex }
        assertEquals(List(10) { 2 }, indices)
    }

    @Test fun `a confirmed move back of more than 30 m restores the previous maneuver`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        t.update(fixAt(t, turnAt - 20.0))
        t.update(fixAt(t, turnAt + 20.0))
        assertEquals(2, t.lastProgress!!.nextManeuverIndex)
        assertEquals("un seul fix en arrière ne suffit pas", 2, t.update(fixAt(t, turnAt - 15.0)).nextManeuverIndex)
        assertEquals(1, t.update(fixAt(t, turnAt - 20.0)).nextManeuverIndex)
    }

    @Test fun `an extrapolated position does not count as a backtrack confirmation`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        t.update(fixAt(t, turnAt - 20.0))
        t.update(fixAt(t, turnAt + 20.0))
        assertEquals(2, t.update(fixAt(t, turnAt - 15.0)).nextManeuverIndex)
        assertEquals("l'extrapolation ne confirme pas le recul", 2, t.extrapolate(2.0).nextManeuverIndex)
        assertEquals("le second fix réel confirme le recul", 1, t.update(fixAt(t, turnAt - 15.0)).nextManeuverIndex)
    }

    @Test fun `a final leg shorter than the passing margin still arrives`() {
        val pts = listOf(TestRoutes.xy(0.0, 0.0), TestRoutes.xy(200.0, 0.0), TestRoutes.xy(200.0, -15.0))
        val leg1 = pts[0].distanceTo(pts[1])
        val leg2 = pts[1].distanceTo(pts[2])
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, leg1, 24.0, 0, null),
            Maneuver(ManeuverType.TURN_RIGHT, "Tournez à droite.", null, leg2, 6.0, 1, null),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 2, null),
        )
        val r = Route("shortLastLeg", RouteCosting.FASTEST, leg1 + leg2, 30.0, pts, maneuvers, hasToll = false, providerId = "test")
        val t = RouteTracker(r)
        t.update(fixAt(t, 150.0, accuracyM = 10f))
        val p = t.update(fixAt(t, t.totalGeometryM, accuracyM = 10f))
        assertEquals(r.maneuvers.lastIndex, p.nextManeuverIndex)
        assertTrue("arrivée malgré un dernier tronçon de 15 m", t.hasArrived)
    }

    @Test fun `two roundabouts 120 m apart are two distinct targets, none skipped`() {
        val r = TestRoutes.twoRoundabouts()
        val t = RouteTracker(r)
        val seen = FixScript(r, seed = 3).drive(0.0, t.totalGeometryM - 30.0, speedKmh = 30.0, noiseM = 3.0)
            .map { t.update(it).nextManeuverIndex }
        assertEquals("indices visités dans l'ordre, sans saut", listOf(1, 2, 3, 4, 5), seen.distinct())
        assertTrue("jamais de retour en arrière", seen.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun `distances to the following maneuvers share the banner base`() {
        val paris = ParisRoute.route()
        val t = RouteTracker(paris)
        val scale = paris.distanceMeters / t.totalGeometryM
        val p = t.update(LocationFix(paris.geometry[100], null, null, 5f, 0L))
        assertEquals(7, p.nextManeuverIndex)
        assertEquals((t.maneuverStartM[7] - p.distanceAlongM) * scale, p.distanceToNextManeuverM, 1e-6)
        assertEquals(3, p.distancesToManeuversM.size)
        p.distancesToManeuversM.forEachIndexed { i, d -> assertEquals((t.maneuverStartM[8 + i] - p.distanceAlongM) * scale, d, 1e-6) }
        val nearEnd = RouteTracker(paris).update(LocationFix(paris.geometry[290], null, null, 5f, 0L))
        assertEquals(12, nearEnd.nextManeuverIndex)
        assertEquals("seule l'arrivée suit", 1, nearEnd.distancesToManeuversM.size)
    }

    @Test fun `left turn has a negative angle and a stable index under jitter`() { // scénario 3
        val r = TestRoutes.leftTurn()
        assertEquals(ManeuverType.TURN_LEFT, r.maneuvers[1].type)
        assertEquals(-90.0, r.maneuvers[1].turnAngleDeg!!, 0.0)
        val t = RouteTracker(r)
        val script = FixScript(r, seed = 5)
        val turnAt = t.maneuverStartM[1]
        script.drive(0.0, turnAt - 30.0, speedKmh = 30.0).forEach { t.update(it) }
        assertEquals(List(10) { 1 }, script.jitter(turnAt, count = 10, amplitudeM = 8.0).map { t.update(it).nextManeuverIndex })
    }
}
