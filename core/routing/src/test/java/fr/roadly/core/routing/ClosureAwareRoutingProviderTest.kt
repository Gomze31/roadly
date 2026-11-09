package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.traffic.TrafficEvent
import fr.roadly.core.data.traffic.TrafficKind
import fr.roadly.core.data.traffic.TrafficSource
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosureAwareRoutingProviderTest {
    private val o = GeoPoint(43.60, 1.40)
    private val d = GeoPoint(43.60, 1.50)
    /** Tracé direct (ouest → est) et détour par le nord. */
    private val direct = (0..10).map { GeoPoint(43.60, 1.40 + it * 0.01) }
    private val detour = listOf(o, GeoPoint(43.65, 1.40), GeoPoint(43.65, 1.50), d)
    private val now = 1_000_000L

    private fun closure(at: GeoPoint, start: Long? = null, end: Long? = null, kind: TrafficKind = TrafficKind.CLOSURE) =
        TrafficEvent("c1", kind, listOf(at), bothWays = true, road = "D2", direction = null, description = null,
            startEpochMs = start, endEpochMs = end, queueLengthM = null, severity = null)

    private class Recording(val answer: (RouteRequest) -> Outcome<List<Route>>) : RoutingProvider {
        val requests = mutableListOf<RouteRequest>()
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return answer(request) }
    }

    private fun route(g: List<GeoPoint>) = Route("r${g.size}", RouteCosting.FASTEST, 1.0, 1.0, g, emptyList(), false, "fake")
    private fun inner() = Recording { r -> Outcome.Success(listOf(route(if (r.excludePolygons.isEmpty()) direct else detour))) }
    private fun source(vararg e: TrafficEvent) = object : TrafficSource { override suspend fun events() = e.toList() }
    private val request = RouteRequest(o, d, costing = RouteCosting.FASTEST)

    @Test fun `an active closure on the route is avoided and flagged`() = runTest {
        val inner = inner()
        val out = ClosureAwareRoutingProvider(inner, source(closure(GeoPoint(43.6002, 1.45), start = now - 1)), { now }).route(request)
        val r = (out as Outcome.Success).value.first()
        assertEquals(detour, r.geometry)
        assertEquals(1, r.avoidedClosures)
        assertEquals(2, inner.requests.size)
        assertTrue(inner.requests[1].excludePolygons.isNotEmpty())
        // Chaque zone exclue est un carré fermé autour d'un point de la fermeture.
        assertTrue(inner.requests[1].excludePolygons.all { it.size == 5 && it.first() == it.last() })
    }

    @Test fun `no closure on the route costs a single request`() = runTest {
        val inner = inner()
        val far = closure(GeoPoint(44.5, 3.0))
        val out = ClosureAwareRoutingProvider(inner, source(far, closure(GeoPoint(43.6, 1.45), kind = TrafficKind.ROADWORK)), { now }).route(request)
        assertEquals(direct, (out as Outcome.Success).value.first().geometry)
        assertEquals(0, out.value.first().avoidedClosures)
        assertEquals(1, inner.requests.size)
    }

    @Test fun `a closure planned later or already ended is not avoided`() = runTest {
        val inner = inner()
        val p = ClosureAwareRoutingProvider(inner, source(closure(GeoPoint(43.6, 1.45), start = now + 60_000)), { now })
        assertEquals(direct, (p.route(request) as Outcome.Success).value.first().geometry)
        val ended = ClosureAwareRoutingProvider(inner, source(closure(GeoPoint(43.6, 1.45), end = now - 1)), { now })
        assertEquals(direct, (ended.route(request) as Outcome.Success).value.first().geometry)
        assertEquals(2, inner.requests.size)
    }

    @Test fun `without another path the original route is kept`() = runTest {
        val inner = Recording { r -> if (r.excludePolygons.isEmpty()) Outcome.Success(listOf(route(direct))) else Outcome.Failure(AppError.NoResult) }
        val out = ClosureAwareRoutingProvider(inner, source(closure(GeoPoint(43.6, 1.45))), { now }).route(request)
        assertEquals(direct, (out as Outcome.Success).value.first().geometry)
        assertEquals(0, out.value.first().avoidedClosures)
    }

    @Test fun `walking and scooter requests are left untouched, and a traffic failure changes nothing`() = runTest {
        val inner = inner()
        val p = ClosureAwareRoutingProvider(inner, source(closure(GeoPoint(43.6, 1.45))), { now })
        p.route(request.copy(pedestrian = true))
        assertEquals(1, inner.requests.size)
        val failing = object : TrafficSource { override suspend fun events(): List<TrafficEvent> = throw java.io.IOException("x") }
        val out = ClosureAwareRoutingProvider(inner, failing, { now }).route(request)
        assertEquals(direct, (out as Outcome.Success).value.first().geometry)
    }

    @Test fun `box is a closed square of about 80 m`() {
        val b = ClosureAwareRoutingProvider.box(GeoPoint(43.6, 1.45))
        assertEquals(5, b.size)
        assertEquals(80.0, b[0].distanceTo(b[1]), 2.0)
        assertEquals(80.0, b[1].distanceTo(b[2]), 2.0)
    }
}
