package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
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

    @Test fun `two roundabouts 120 m apart are two distinct targets, none skipped`() {
        val r = TestRoutes.twoRoundabouts()
        val t = RouteTracker(r)
        val seen = FixScript(r, seed = 3).drive(0.0, t.totalGeometryM - 30.0, speedKmh = 30.0, noiseM = 3.0)
            .map { t.update(it).nextManeuverIndex }
        assertEquals("indices visités dans l'ordre, sans saut", listOf(1, 2, 3, 4, 5), seen.distinct())
        assertTrue("jamais de retour en arrière", seen.zipWithNext().all { (a, b) -> b >= a })
    }
}
