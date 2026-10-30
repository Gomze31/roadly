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
    private fun fix(p: GeoPoint, accuracyM: Float = 5f) = LocationFix(p, null, null, accuracyM, 0L)

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

    @Test fun `a single imprecise fix beyond 100 m cannot trigger the hard rule`() {
        val t = RouteTracker(route)
        t.update(fix(t.pointAt(300.0)))
        val far = TestRoutes.north(t.pointAt(300.0), 150.0)
        repeat(4) {
            t.update(fix(far, accuracyM = 80f))
            assertFalse("fix imprécis ${it + 1}", t.isOffRoute)
        }
        // il compte tout de même pour la règle des 5 fixes consécutifs à plus de 40 m
        t.update(fix(far, accuracyM = 80f))
        assertTrue(t.isOffRoute)
    }

    @Test fun `a fix of exactly 50 m accuracy still triggers the hard rule`() {
        val t = RouteTracker(route)
        t.update(fix(TestRoutes.north(t.pointAt(300.0), 150.0), accuracyM = 50f))
        assertTrue(t.isOffRoute)
    }

    @Test fun `arrival also when within 25 m along the route end and less than 40 m off it`() {
        val t = RouteTracker(route)
        t.update(fix(t.pointAt(1300.0)))
        val end = t.totalGeometryM
        val aside = TestRoutes.north(t.pointAt(end - 20.0), 30.0)
        assertTrue("garde-fou : hors du rayon de 25 m de la destination", aside.distanceTo(route.geometry.last()) > GuidanceConstants.ARRIVAL_RADIUS_M)
        t.update(fix(aside))
        assertTrue(t.hasArrived)
    }

    @Test fun `no arrival when more than 25 m from the end along the route or more than 40 m off it`() {
        val t1 = RouteTracker(route)
        t1.update(fix(t1.pointAt(1300.0)))
        t1.update(fix(t1.pointAt(t1.totalGeometryM - 30.0)))
        assertFalse(t1.hasArrived)
        val t2 = RouteTracker(route)
        t2.update(fix(t2.pointAt(1300.0)))
        t2.update(fix(TestRoutes.north(t2.pointAt(t2.totalGeometryM - 20.0), 50.0)))
        assertFalse(t2.hasArrived)
    }

    @Test fun `degenerate routes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RouteTracker(route.copy(geometry = route.geometry.take(1))) }
        assertThrows(IllegalArgumentException::class.java) { RouteTracker(route.copy(maneuvers = emptyList())) }
    }
}
