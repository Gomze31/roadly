package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** VAL-3 : porte des appels Overpass du mode trottinette (temps virtuel, aucun réseau). */
@OptIn(ExperimentalCoroutinesApi::class)
class OverpassGateTest {
    private fun TestScope.gate() = OverpassGate({ testScheduler.currentTime })
    private val ok: Outcome<String> = Outcome.Success("ok")

    @Test fun `the first request leaves at once`() = runTest {
        val gate = gate()
        assertEquals(ok, gate.withTurn { call { ok } })
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `two requests are at least one second apart, counted from the end of the first`() = runTest {
        val gate = gate()
        val starts = mutableListOf<Long>()
        gate.withTurn { call { starts += testScheduler.currentTime; delay(400); ok } }
        gate.withTurn { call { starts += testScheduler.currentTime; ok } }
        assertEquals(listOf(0L, 400L + OverpassGate.MIN_INTERVAL_MS), starts)
    }

    @Test fun `callers arriving together go one after the other, never two requests in flight`() = runTest {
        val gate = gate()
        var inFlight = 0
        var maxInFlight = 0
        val results = (1..3).map {
            async { gate.withTurn { call { inFlight++; maxInFlight = maxOf(maxInFlight, inFlight); delay(300); inFlight--; ok } } }
        }.awaitAll()
        assertEquals(listOf(ok, ok, ok), results)
        assertEquals(1, maxInFlight)
    }

    @Test fun `a caller that waited sees what the previous one stored and asks nothing`() = runTest {
        val gate = gate()
        val cache = HashSet<Int>()
        var requests = 0
        (1..3).map {
            async { gate.withTurn { if (7 !in cache) { call { requests++; delay(200); ok }; cache += 7 } } }
        }.awaitAll()
        assertEquals("trois appelants simultanés de la même donnée : une seule requête", 1, requests)
    }

    @Test fun `429 - nothing is sent during the back-off, which doubles after a second failure and is lifted by a success`() = runTest {
        val gate = gate()
        var sent = 0
        var answer: Outcome<String> = Outcome.Failure(AppError.RateLimited)
        suspend fun ask() = gate.withTurn { call { sent++; answer } }
        assertEquals(Outcome.Failure(AppError.RateLimited), ask())
        assertEquals(1, sent)
        repeat(3) { advanceTimeBy(10_000); assertEquals(Outcome.Failure(AppError.RateLimited), ask()) }
        assertEquals("aucune requête pendant le retrait", 1, sent)
        advanceTimeBy(OverpassGate.BACKOFF_MS)
        ask() // retrait écoulé : une requête repart, nouvel échec, le retrait double
        assertEquals(2, sent)
        advanceTimeBy(OverpassGate.BACKOFF_MS + 30_000)
        ask()
        assertEquals("toujours en retrait (120 s)", 2, sent)
        advanceTimeBy(OverpassGate.BACKOFF_MS)
        answer = ok
        assertEquals(ok, ask())
        assertEquals(3, sent)
        advanceTimeBy(OverpassGate.MIN_INTERVAL_MS)
        answer = Outcome.Failure(AppError.RateLimited)
        ask()
        advanceTimeBy(OverpassGate.BACKOFF_MS + 1)
        answer = ok
        assertEquals("après un succès, le retrait repart de sa valeur de base", ok, ask())
    }

    @Test fun `5xx and timeout back off, other failures do not`() = runTest {
        for ((error, backsOff) in listOf(
            AppError.ProviderUnavailable("overpass", 504) to true,
            AppError.ProviderUnavailable("overpass", 503) to true,
            AppError.Timeout to true,
            AppError.Network to false,
            AppError.Parse("x") to false,
            AppError.Http(400, "") to false,
        )) {
            val gate = gate()
            var sent = 0
            gate.withTurn { call<String> { sent++; Outcome.Failure(error) } }
            advanceTimeBy(OverpassGate.MIN_INTERVAL_MS)
            gate.withTurn { call { sent++; ok } }
            assertEquals("$error", if (backsOff) 1 else 2, sent)
        }
    }

    @Test fun `the back-off never exceeds fifteen minutes`() = runTest {
        val gate = gate()
        var sent = 0
        repeat(12) {
            gate.withTurn { call<String> { sent++; Outcome.Failure(AppError.Timeout) } }
            advanceTimeBy(OverpassGate.BACKOFF_MAX_MS + 1)
        }
        assertEquals("chaque essai repart après 15 min au plus", 12, sent)
    }
}
