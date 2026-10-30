package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerOffRouteTest {
    private val route = TestRoutes.straight()
    private fun fix(p: GeoPoint) = LocationFix(p, null, null, 5f, 0L)

    @Test fun `30 m lateral offset is measured and tolerated`() {
        val t = RouteTracker(route)
        val p = t.update(fix(TestRoutes.north(t.pointAt(300.0), 30.0)))
        assertEquals(30.0, p.offRouteM, 0.5)
        assertEquals(300.0, p.distanceAlongM, 0.5)
        assertFalse(t.isOffRoute)
    }

    @Test fun `five consecutive fixes beyond 40 m trigger off route`() {
        val t = RouteTracker(route)
        repeat(4) {
            t.update(fix(TestRoutes.north(t.pointAt(300.0 + it * 10), 60.0)))
            assertFalse("fix ${it + 1}", t.isOffRoute)
        }
        t.update(fix(TestRoutes.north(t.pointAt(340.0), 60.0)))
        assertTrue(t.isOffRoute)
    }

    @Test fun `coming back on route resets the counter`() {
        val t = RouteTracker(route)
        repeat(4) { t.update(fix(TestRoutes.north(t.pointAt(300.0), 60.0))) }
        t.update(fix(t.pointAt(310.0)))
        assertFalse(t.isOffRoute)
        repeat(4) { t.update(fix(TestRoutes.north(t.pointAt(320.0), 60.0))) }
        assertFalse(t.isOffRoute)
    }

    @Test fun `one fix beyond 100 m triggers off route`() {
        val t = RouteTracker(route)
        t.update(fix(TestRoutes.north(t.pointAt(300.0), 150.0)))
        assertTrue(t.isOffRoute)
    }

    @Test fun `degenerate routes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RouteTracker(route.copy(geometry = route.geometry.take(1))) }
        assertThrows(IllegalArgumentException::class.java) { RouteTracker(route.copy(maneuvers = emptyList())) }
    }
}
