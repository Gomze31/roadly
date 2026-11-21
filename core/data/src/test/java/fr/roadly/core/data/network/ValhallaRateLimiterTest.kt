package fr.roadly.core.data.network

import fr.roadly.core.data.network.ValhallaRateLimiter.Permit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Limiteur de débit FOSSGIS : espacement, sérialisation, priorité, pauses `Retry-After`, le tout en temps virtuel. */
@OptIn(ExperimentalCoroutinesApi::class)
class ValhallaRateLimiterTest {
    private fun TestScope.limiter() = ValhallaRateLimiter(throttledHost = { true }, nowMs = { testScheduler.currentTime })
    private fun TestScope.now() = testScheduler.currentTime

    @Test fun `a burst is spread one request per second`() = runTest {
        val l = limiter(); val starts = mutableListOf<Long>()
        repeat(3) { launch { l.withPermit { starts += now() } } }
        advanceUntilIdle()
        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }

    @Test fun `only one request is in flight, a slow one holds the next back`() = runTest {
        val l = limiter(); val starts = mutableListOf<Long>()
        var inFlight = 0; var maxInFlight = 0
        repeat(3) {
            launch { l.withPermit { inFlight++; maxInFlight = maxOf(maxInFlight, inFlight); starts += now(); delay(2_500); inFlight-- } }
        }
        advanceUntilIdle()
        assertEquals(listOf(0L, 2_500L, 5_000L), starts)
        assertEquals(1, maxInFlight)
    }

    @Test fun `an idle limiter does not delay a request`() = runTest {
        val l = limiter()
        l.withPermit { }
        delay(5_000)
        var startedAt = -1L
        l.withPermit { startedAt = now() }
        assertEquals(5_000L, startedAt)
    }

    @Test fun `a reroute overtakes queued background requests, then oldest first`() = runTest {
        val l = limiter(); val order = mutableListOf<String>()
        launch { l.withPermit(ApiPriority.BACKGROUND) { order += "a@${now()}" } }
        launch { l.withPermit(ApiPriority.BACKGROUND) { order += "b@${now()}" } }
        launch { l.withPermit(ApiPriority.BACKGROUND) { order += "c@${now()}" } }
        launch { l.withPermit(ApiPriority.INTERACTIVE) { order += "recherche@${now()}" } }
        launch { l.withPermit(ApiPriority.GUIDANCE) { order += "recalcul@${now()}" } }
        advanceUntilIdle()
        assertEquals(listOf("a@0", "recalcul@1000", "recherche@2000", "b@3000", "c@4000"), order)
    }

    @Test fun `a cancelled waiter gives its place away without using a slot`() = runTest {
        val l = limiter(); val starts = mutableListOf<String>()
        launch { l.withPermit { starts += "a@${now()}" } }
        val b = launch { l.withPermit { starts += "b@${now()}" } }
        launch { l.withPermit { starts += "c@${now()}" } }
        runCurrent()
        b.cancel()
        advanceUntilIdle()
        assertEquals(listOf("a@0", "c@1000"), starts)
    }

    @Test fun `a failing request still frees the limiter`() = runTest {
        val l = limiter()
        runCatching { l.withPermit<Unit> { throw IllegalStateException("boom") } }
        var startedAt = -1L
        l.withPermit { startedAt = now() }
        assertEquals(1_000L, startedAt)
    }

    @Test fun `a short Retry-After pause postpones the next slot`() = runTest {
        val l = limiter()
        l.pause(3_000, 429)
        var startedAt = -1L
        val r = l.withPermit { startedAt = now(); "ok" }
        assertEquals(Permit.Ran("ok"), r)
        assertEquals(3_000L, startedAt)
    }

    @Test fun `a long pause refuses at once without running the request, then service resumes`() = runTest {
        val l = limiter()
        l.pause(120_000, 503)
        var ran = false
        val r = l.withPermit { ran = true }
        assertEquals(Permit.Refused(503, 120_000), r)
        assertFalse(ran)
        assertEquals(0L, now())
        // À moins de 10 s de la fin de la pause, la requête attend au lieu d'échouer.
        delay(111_000)
        var startedAt = -1L
        assertTrue(l.withPermit { startedAt = now() } is Permit.Ran)
        assertEquals(120_000L, startedAt)
    }

    @Test fun `queued requests are refused when the request in flight triggers a long pause`() = runTest {
        val l = limiter(); val results = mutableListOf<Permit<String>>()
        launch { l.withPermit { delay(200); l.pause(60_000, 429); "429" } }
        launch { results += l.withPermit { "b" } }
        launch { results += l.withPermit { "c" } }
        advanceTimeBy(1_000)
        assertEquals(2, results.size)
        assertTrue(results.all { it is Permit.Refused && it.httpCode == 429 })
    }

    @Test fun `a shorter pause never shortens the current one and absurd values are capped`() = runTest {
        val l = limiter()
        l.pause(5_000, 503)
        l.pause(1_000, 429)
        var startedAt = -1L
        l.withPermit { startedAt = now() }
        assertEquals(5_000L, startedAt)
        l.pause(Long.MAX_VALUE, 429)
        assertEquals(Permit.Refused(429, ValhallaRateLimiter.MAX_PAUSE_MS), l.withPermit { })
    }

    @Test fun `only public FOSSGIS hosts are throttled`() {
        assertTrue(ValhallaRateLimiter.isPublicFossgisHost("valhalla1.openstreetmap.de"))
        assertTrue(ValhallaRateLimiter.isPublicFossgisHost("routing.openstreetmap.de"))
        assertTrue(ValhallaRateLimiter.isPublicFossgisHost("Valhalla1.OpenStreetMap.de."))
        assertFalse(ValhallaRateLimiter.isPublicFossgisHost("valhalla.exemple.fr"))
        assertFalse(ValhallaRateLimiter.isPublicFossgisHost("openstreetmap.de.exemple.fr"))
        assertFalse(ValhallaRateLimiter.isPublicFossgisHost("monopenstreetmap.de"))
        assertFalse(ValhallaRateLimiter.isPublicFossgisHost("localhost"))
        assertFalse(ValhallaRateLimiter.isPublicFossgisHost("192.168.1.20"))
        val default = ValhallaRateLimiter()
        assertTrue(default.appliesTo("valhalla1.openstreetmap.de"))
        assertFalse(default.appliesTo("valhalla.exemple.fr"))
    }
}
