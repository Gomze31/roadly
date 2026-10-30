package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {
    private val route = ParisRoute.route()
    private val pts = route.geometry
    /** Distances cumulées recalculées indépendamment (haversine de GeoPoint). */
    private val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
    private fun fixAt(p: GeoPoint, speedMps: Float? = null, bearing: Float? = null) = LocationFix(p, bearing, speedMps, 5f, 0L)
    private fun mid(a: GeoPoint, b: GeoPoint) = GeoPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)

    @Test fun `fixture has a departure, an arrival and a usable geometry`() {
        assertTrue(pts.size > 100)
        assertEquals(fr.roadly.core.model.ManeuverType.DEPART, route.maneuvers.first().type)
        assertEquals(fr.roadly.core.model.ManeuverType.ARRIVE, route.maneuvers.last().type)
        assertEquals(pts.lastIndex, route.maneuvers.last().beginShapeIndex)
    }

    @Test fun `cumulative distances match the fixture geometry`() {
        val t = RouteTracker(route)
        assertEquals(pts.size, t.cumDistM.size)
        assertEquals(cum.last(), t.totalGeometryM, 1e-6)
        // Valhalla annonce la longueur du trajet ; la somme haversine du tracé doit être cohérente (2 %).
        assertEquals(route.distanceMeters, t.totalGeometryM, route.distanceMeters * 0.02)
        route.maneuvers.forEachIndexed { k, m -> assertEquals(cum[m.beginShapeIndex], t.maneuverStartM[k], 1e-6) }
    }

    @Test fun `vertices project onto themselves with the exact along distance`() {
        listOf(10, 50, 100, 150, 200, 250, 300).forEach { k ->
            val p = RouteTracker(route).update(fixAt(pts[k]))
            assertEquals("off au point $k", 0.0, p.offRouteM, 0.01)
            assertEquals("along au point $k", cum[k], p.distanceAlongM, 0.01)
        }
    }

    @Test fun `segment midpoints project at half the segment`() {
        listOf(20, 80, 160, 240, 310).forEach { k ->
            val p = RouteTracker(route).update(fixAt(mid(pts[k], pts[k + 1])))
            assertEquals("along milieu $k", cum[k] + (cum[k + 1] - cum[k]) / 2, p.distanceAlongM, 0.05)
            assertEquals("off milieu $k", 0.0, p.offRouteM, 0.05)
        }
    }

    @Test fun `next maneuver index and distance to it`() {
        for (k in 1 until route.maneuvers.size) {
            val begin = route.maneuvers[k].beginShapeIndex
            if (begin - 1 <= route.maneuvers[k - 1].beginShapeIndex) continue // pas de point intermédiaire
            val p = RouteTracker(route).update(fixAt(pts[begin - 1]))
            assertEquals("manœuvre suivante depuis le point ${begin - 1}", k, p.nextManeuverIndex)
            assertEquals(cum[begin] - cum[begin - 1], p.distanceToNextManeuverM, 0.05)
        }
    }

    @Test fun `remaining values at departure equal the route totals`() {
        val p = RouteTracker(route).update(fixAt(pts[0]))
        assertEquals(route.maneuvers.sumOf { it.durationSeconds }, p.remainingDurationS, 0.5)
        assertEquals(route.distanceMeters, p.remainingDistanceM, 1.0)
        assertEquals(1, p.nextManeuverIndex)
    }

    @Test fun `remaining duration at each maneuver start is the sum of the remaining Valhalla durations`() {
        for (k in 1 until route.maneuvers.lastIndex) {
            val p = RouteTracker(route).update(fixAt(pts[route.maneuvers[k].beginShapeIndex]))
            assertEquals("restant au début de la manœuvre $k", route.maneuvers.drop(k).sumOf { it.durationSeconds }, p.remainingDurationS, 0.5)
        }
    }

    @Test fun `remaining duration is prorated inside a maneuver`() {
        val t = RouteTracker(route)
        val m6 = route.maneuvers[6]
        val start = cum[m6.beginShapeIndex]
        val end = cum[route.maneuvers[7].beginShapeIndex]
        val p = t.update(fixAt(t.pointAt(start + (end - start) * 0.25)))
        val expected = m6.durationSeconds * 0.75 + route.maneuvers.drop(7).sumOf { it.durationSeconds }
        assertEquals(expected, p.remainingDurationS, 0.5)
    }

    @Test fun `remaining distance decreases along the route`() {
        val t = RouteTracker(route)
        var last = Double.MAX_VALUE
        for (k in pts.indices step 7) {
            val p = t.update(fixAt(pts[k]))
            assertTrue("point $k", p.remainingDistanceM <= last + 1e-6)
            last = p.remainingDistanceM
        }
    }

    @Test fun `window falls back to a global search after a confirmed jump`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[20]))
        t.update(fixAt(pts[280])) // saut > 100 m : en attente de confirmation
        assertEquals(cum[280], t.update(fixAt(pts[280])).distanceAlongM, 0.01)
    }

    @Test fun `a point 1 km away is off route at once`() {
        val t = RouteTracker(route)
        val far = GeoPoint(pts.maxOf { it.lat } + 0.01, pts.map { it.lon }.average())
        assertTrue(t.update(fixAt(far)).offRouteM > GuidanceConstants.OFF_ROUTE_HARD_M)
        assertTrue(t.isOffRoute)
    }

    @Test fun `arrival needs the last maneuver and less than 25 m`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[300]))
        val before = t.pointAt(t.totalGeometryM - 60.0)
        assertTrue("garde-fou : point à plus de 25 m de l'arrivée", before.distanceTo(pts.last()) > GuidanceConstants.ARRIVAL_RADIUS_M)
        t.update(fixAt(before))
        assertFalse(t.hasArrived)
        t.update(fixAt(pts.last()))
        assertTrue(t.hasArrived)
    }

    @Test fun `extrapolation advances along the polyline and clamps at the end`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[100]))
        val p = t.extrapolate(50.0)
        assertEquals(cum[100] + 50.0, p.distanceAlongM, 1e-6)
        assertEquals(0.0, p.offRouteM, 0.0)
        assertEquals(t.pointAt(cum[100] + 50.0), p.snapped)
        assertTrue(p.extrapolated)
    }

    @Test fun `real progress is not flagged as extrapolated`() {
        assertFalse(RouteTracker(route).update(fixAt(pts[100])).extrapolated)
    }

    @Test fun `extrapolation stops at 150 m after the last real fix`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[100]))
        val capped = t.extrapolate(1e9)
        assertEquals(cum[100] + GuidanceConstants.EXTRAPOLATION_MAX_M, capped.distanceAlongM, 1e-6)
        assertSame(capped, t.extrapolate(10.0)) // déjà au plafond : progression rendue telle quelle
        // un fix réel repart d'un plafond neuf
        t.update(fixAt(pts[110]))
        assertEquals(cum[110] + GuidanceConstants.EXTRAPOLATION_MAX_M, t.extrapolate(1e9).distanceAlongM, 1e-6)
    }

    @Test fun `extrapolation never goes past the end of the route`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[pts.lastIndex - 2]))
        assertEquals(t.totalGeometryM, t.extrapolate(1e9).distanceAlongM, 1e-6)
    }

    @Test fun `extrapolation stops after 10 s without a real fix`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[100]))
        val first = t.extrapolate(20.0, sinceLastFixMs = 3_000L)
        assertEquals(cum[100] + 20.0, first.distanceAlongM, 1e-6)
        assertEquals(cum[100] + 40.0, t.extrapolate(20.0, sinceLastFixMs = GuidanceConstants.EXTRAPOLATION_MAX_MS).distanceAlongM, 1e-6)
        val stale = t.lastProgress!!
        assertSame(stale, t.extrapolate(20.0, sinceLastFixMs = GuidanceConstants.EXTRAPOLATION_MAX_MS + 1))
        assertEquals(cum[100] + 40.0, t.lastProgress!!.distanceAlongM, 1e-6)
    }

    @Test fun `pointAt returns the vertices at their cumulative distance`() {
        val t = RouteTracker(route)
        listOf(0, 37, 222, pts.lastIndex).forEach { k -> assertEquals("point $k", 0.0, t.pointAt(cum[k]).distanceTo(pts[k]), 0.01) }
    }

    @Test fun `bearing follows the GPS when moving and the segment otherwise`() {
        val t = RouteTracker(route)
        assertEquals(123f, t.update(fixAt(mid(pts[40], pts[41]), speedMps = 10f, bearing = 123f)).bearingDeg!!, 0f)
        val still = t.update(fixAt(mid(pts[40], pts[41]), speedMps = 0.2f, bearing = 123f)).bearingDeg!!
        assertEquals(bearing(pts[40], pts[41]).toFloat(), still, 0.01f)
    }

    @Test fun `initial progress starts at the origin`() {
        val p = RouteTracker(route).initialProgress()
        assertEquals(0.0, p.distanceAlongM, 0.0)
        assertEquals(1, p.nextManeuverIndex)
        assertEquals(pts[0], p.snapped)
    }
}
