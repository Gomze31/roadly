package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.data.network.ApiPriority
import fr.roadly.core.data.network.ValhallaRateLimiter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun route(costing: RouteCosting, dist: Double, dur: Double, geom: List<GeoPoint>) =
    Route(costing.name, costing, dist, dur, geom, emptyList(), hasToll = false, providerId = "fake")

private class FakeRouting(val byCosting: Map<RouteCosting, Outcome<List<Route>>>) : RoutingProvider {
    override val id = "fake"
    override suspend fun route(request: RouteRequest) = byCosting[request.costing] ?: Outcome.Failure(AppError.NoResult)
}

private class ThrowingForShortestRouting(val ok: Map<RouteCosting, Route>) : RoutingProvider {
    override val id = "fake"
    override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
        if (request.costing == RouteCosting.SHORTEST) throw IllegalStateException("boom")
        return Outcome.Success(listOf(ok.getValue(request.costing)))
    }
}

class RouteAlternativesUseCaseTest {
    private val o = GeoPoint(48.0, 2.0); private val d = GeoPoint(49.0, 3.0)
    private val g1 = listOf(o, GeoPoint(48.5, 2.5), d)
    private val g2 = listOf(o, GeoPoint(48.4, 2.6), d)

    @Test fun `three distinct routes labelled and ordered fastest shortest no toll`() = runTest {
        val uc = RouteAlternativesUseCase(FakeRouting(mapOf(
            RouteCosting.FASTEST to Outcome.Success(listOf(route(RouteCosting.FASTEST, 100.0, 60.0, g1))),
            RouteCosting.SHORTEST to Outcome.Success(listOf(route(RouteCosting.SHORTEST, 90.0, 80.0, g2))),
            RouteCosting.NO_TOLL to Outcome.Success(listOf(route(RouteCosting.NO_TOLL, 120.0, 90.0, listOf(o, d)))),
        )))
        val r = (uc(o, d, null, departAtEpochMs = 1_000_000L) as Outcome.Success).value
        assertEquals(listOf("Le plus rapide", "Le plus court", "Sans péage"), r.map { it.label })
        assertEquals(1_000_000L + 60_000L, r[0].arrivalEpochMs)
        assertTrue(r.none { it.sameAsFastest })
    }

    @Test fun `identical geometry flagged sameAsFastest`() = runTest {
        val uc = RouteAlternativesUseCase(FakeRouting(mapOf(
            RouteCosting.FASTEST to Outcome.Success(listOf(route(RouteCosting.FASTEST, 100.0, 60.0, g1))),
            RouteCosting.NO_TOLL to Outcome.Success(listOf(route(RouteCosting.NO_TOLL, 100.0, 60.0, g1))),
        )))
        val r = (uc(o, d, null, 0L) as Outcome.Success).value
        assertTrue(r.first { it.route.costing == RouteCosting.NO_TOLL }.sameAsFastest)
    }

    @Test fun `all failures propagate first error`() = runTest {
        val uc = RouteAlternativesUseCase(FakeRouting(mapOf(RouteCosting.FASTEST to Outcome.Failure(AppError.Timeout))))
        assertEquals(AppError.Timeout, (uc(o, d, null, 0L) as Outcome.Failure).error)
    }

    @Test fun `throwing costing does not cancel the others`() = runTest {
        val uc = RouteAlternativesUseCase(ThrowingForShortestRouting(mapOf(
            RouteCosting.FASTEST to route(RouteCosting.FASTEST, 100.0, 60.0, g1),
            RouteCosting.NO_TOLL to route(RouteCosting.NO_TOLL, 120.0, 90.0, g2),
        )))
        val r = (uc(o, d, null, 0L) as Outcome.Success).value
        assertEquals(listOf(RouteCosting.FASTEST, RouteCosting.NO_TOLL), r.map { it.route.costing })
    }

    @Test fun `preferences are sent with every variant`() = runTest {
        val seen = mutableListOf<RouteRequest>()
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
                synchronized(seen) { seen += request }
                return Outcome.Success(listOf(route(request.costing, 100.0, 60.0, g1)))
            }
        }
        val prefs = RoutePreferences(avoidHighways = true)
        RouteAlternativesUseCase(routing)(o, d, null, 0L, prefs)
        assertEquals(3, seen.size)
        assertTrue(seen.all { it.preferences == prefs })
    }

    /** Fournisseur dont chaque appel passe par le limiteur de débit, comme `ApiClient` sur l'instance publique. */
    private class ThrottledRouting(val limiter: ValhallaRateLimiter, val now: () -> Long, val geometry: List<GeoPoint>) : RoutingProvider {
        override val id = "fake"
        val starts = mutableListOf<Pair<RouteCosting, Long>>()
        var maxInFlight = 0
        private var inFlight = 0
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            val permit = limiter.withPermit(ApiPriority.INTERACTIVE) {
                inFlight++; maxInFlight = maxOf(maxInFlight, inFlight)
                starts += request.costing to now()
                delay(300) // durée de la requête
                inFlight--
                route(request.costing, 100.0, 60.0, geometry)
            }
            return Outcome.Success(listOf((permit as ValhallaRateLimiter.Permit.Ran).value))
        }
    }

    @Test fun `on a throttled host the three variants leave one per second, fastest first, never in parallel`() = runTest {
        val routing = ThrottledRouting(ValhallaRateLimiter(throttledHost = { true }, nowMs = { testScheduler.currentTime }), { testScheduler.currentTime }, g1)
        val r = (RouteAlternativesUseCase(routing)(o, d, null, 0L) as Outcome.Success).value
        assertEquals(listOf(RouteCosting.FASTEST to 0L, RouteCosting.SHORTEST to 1_000L, RouteCosting.NO_TOLL to 2_000L), routing.starts)
        assertEquals(1, routing.maxInFlight)
        assertEquals(listOf("Le plus rapide", "Le plus court", "Sans péage"), r.map { it.label })
        assertEquals(2_300L, testScheduler.currentTime)
    }

    @Test fun `progressive delivers each variant as soon as it is ready, then the full result`() = runTest {
        val routing = ThrottledRouting(ValhallaRateLimiter(throttledHost = { true }, nowMs = { testScheduler.currentTime }), { testScheduler.currentTime }, g1)
        val seen = mutableListOf<Pair<Long, List<RouteCosting>>>()
        RouteAlternativesUseCase(routing).progressive(o, d, null, 0L).collect { out ->
            seen += testScheduler.currentTime to (out as Outcome.Success).value.map { it.route.costing }
        }
        assertEquals(
            listOf(
                300L to listOf(RouteCosting.FASTEST),
                1_300L to listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST),
                2_300L to listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST, RouteCosting.NO_TOLL),
            ),
            seen,
        )
    }

    @Test fun `progressive skips failed variants and ends with the same result as invoke`() = runTest {
        val routing = FakeRouting(mapOf(
            RouteCosting.FASTEST to Outcome.Success(listOf(route(RouteCosting.FASTEST, 100.0, 60.0, g1))),
            RouteCosting.SHORTEST to Outcome.Failure(AppError.Timeout),
            RouteCosting.NO_TOLL to Outcome.Success(listOf(route(RouteCosting.NO_TOLL, 100.0, 60.0, g1))),
        ))
        val uc = RouteAlternativesUseCase(routing)
        val emissions = uc.progressive(o, d, null, 0L).toList()
        assertEquals(listOf(1, 2), emissions.map { (it as Outcome.Success).value.size })
        assertEquals(uc(o, d, null, 0L), emissions.last())
        assertTrue((emissions.last() as Outcome.Success).value.last().sameAsFastest)
    }

    @Test fun `progressive emits a single failure when nothing succeeds`() = runTest {
        val uc = RouteAlternativesUseCase(FakeRouting(mapOf(RouteCosting.SHORTEST to Outcome.Failure(AppError.RateLimited))))
        // Première erreur dans l'ordre des variantes : « rapide » n'a pas de résultat.
        assertEquals(listOf<Outcome<List<RouteOption>>>(Outcome.Failure(AppError.NoResult)), uc.progressive(o, d, null, 0L).toList())
    }
}
