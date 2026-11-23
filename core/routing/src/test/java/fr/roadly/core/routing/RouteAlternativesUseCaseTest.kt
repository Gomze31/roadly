package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.sync.withLock
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

    @Test fun `variant repeating an earlier geometry is dropped`() = runTest {
        val uc = RouteAlternativesUseCase(FakeRouting(mapOf(
            RouteCosting.FASTEST to Outcome.Success(listOf(route(RouteCosting.FASTEST, 100.0, 60.0, g1))),
            RouteCosting.SHORTEST to Outcome.Success(listOf(route(RouteCosting.SHORTEST, 90.0, 80.0, g2))),
            RouteCosting.NO_TOLL to Outcome.Success(listOf(route(RouteCosting.NO_TOLL, 100.0, 60.0, g1))),
        )))
        val r = (uc(o, d, null, 0L) as Outcome.Success).value
        assertEquals(listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST), r.map { it.route.costing })
    }

    @Test fun `no toll variant is not computed when tolls are already avoided`() = runTest {
        val seen = mutableListOf<RouteCosting>()
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
                synchronized(seen) { seen += request.costing }
                return Outcome.Success(listOf(route(request.costing, 100.0, 60.0, if (request.costing == RouteCosting.FASTEST) g1 else g2)))
            }
        }
        val r = (RouteAlternativesUseCase(routing)(o, d, null, 0L, RoutePreferences(avoidTolls = true)) as Outcome.Success).value
        assertEquals(setOf(RouteCosting.FASTEST, RouteCosting.SHORTEST), seen.toSet())
        assertEquals(listOf("Le plus rapide", "Le plus court"), r.map { it.label })
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

    @Test fun `fastest only - one request where the comparison sends three, same option as the comparison`() = runTest {
        val seen = mutableListOf<RouteRequest>()
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
                synchronized(seen) { seen += request }
                return Outcome.Success(listOf(route(request.costing, 100.0, 60.0, if (request.costing == RouteCosting.FASTEST) g1 else g2)))
            }
        }
        val uc = RouteAlternativesUseCase(routing)
        val prefs = RoutePreferences(avoidHighways = true)
        val stop = GeoPoint(48.2, 2.2)
        val compared = (uc(o, d, null, 5_000L, prefs, listOf(stop)) as Outcome.Success).value
        assertEquals("comparaison : trois requêtes /route", 3, seen.size)
        seen.clear()
        val fastest = (uc.fastest(o, d, null, 5_000L, prefs, listOf(stop)) as Outcome.Success).value
        assertEquals("démarrage direct : une seule requête /route", 1, seen.size)
        assertEquals(RouteCosting.FASTEST, seen.single().costing)
        assertEquals(prefs, seen.single().preferences)
        assertEquals(listOf(stop), seen.single().waypoints)
        assertEquals(compared.first { it.route.costing == RouteCosting.FASTEST }, fastest)
    }

    @Test fun `fastest only - a failure is returned as is, a thrown exception becomes a failure`() = runTest {
        val down = RouteAlternativesUseCase(FakeRouting(mapOf(RouteCosting.FASTEST to Outcome.Failure(AppError.RateLimited))))
        assertEquals(AppError.RateLimited, (down.fastest(o, d, null, 0L) as Outcome.Failure).error)
        val none = RouteAlternativesUseCase(FakeRouting(mapOf(RouteCosting.FASTEST to Outcome.Success(emptyList()))))
        assertEquals(AppError.NoResult, (none.fastest(o, d, null, 0L) as Outcome.Failure).error)
        val throwing = RouteAlternativesUseCase(object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> = throw IllegalStateException("boom")
        })
        assertTrue((throwing.fastest(o, d, null, 0L) as Outcome.Failure).error is AppError.Unknown)
    }

    /** Instance publique : une requête à la fois (limiteur), chacune bornée à 15 s. [answer] : résultat de la n-ième requête partie. */
    private class OneAtATimeRouting(val answer: (Int) -> Outcome<List<Route>>) : RoutingProvider {
        override val id = "fake"
        private val slot = kotlinx.coroutines.sync.Mutex()
        val started = ArrayList<RouteCosting>()
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = slot.withLock {
            started += request.costing
            val r = answer(started.size)
            if (r is Outcome.Failure) kotlinx.coroutines.delay(15_000)
            r
        }
    }

    @Test fun `server that never answers - the error comes after one timeout, not three`() = runTest {
        val routing = OneAtATimeRouting { Outcome.Failure(AppError.Timeout) }
        val r = RouteAlternativesUseCase(routing)(o, d, null, 0L)
        assertEquals(AppError.Timeout, (r as Outcome.Failure).error)
        assertEquals("attente : un seul délai de 15 s (45 s avant correction)", 15_000L, testScheduler.currentTime)
        // La variante suivante a pu prendre le créneau à l'instant où il se libérait : elle est annulée aussitôt, la troisième ne part jamais.
        assertTrue("parties : ${routing.started}", routing.started.size <= 2)
    }

    @Test fun `fastest found then the server stops answering - the route is shown after one more timeout`() = runTest {
        val routing = OneAtATimeRouting { n -> if (n == 1) Outcome.Success(listOf(route(RouteCosting.FASTEST, 100.0, 60.0, g1))) else Outcome.Failure(AppError.Timeout) }
        val r = (RouteAlternativesUseCase(routing)(o, d, null, 0L) as Outcome.Success).value
        assertEquals(listOf("Le plus rapide"), r.map { it.label })
        assertEquals(15_000L, testScheduler.currentTime)
        assertEquals(listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST), routing.started.take(2))
    }

    @Test fun `a variant without result is not a server failure - the others are still computed`() = runTest {
        val routing = OneAtATimeRouting { n -> if (n == 2) Outcome.Failure(AppError.NoResult) else Outcome.Success(listOf(route(RouteCosting.FASTEST, 100.0 + n, 60.0, if (n == 1) g1 else g2))) }
        val r = (RouteAlternativesUseCase(routing)(o, d, null, 0L) as Outcome.Success).value
        assertEquals(2, r.size)
        assertEquals(3, routing.started.size)
    }
}
