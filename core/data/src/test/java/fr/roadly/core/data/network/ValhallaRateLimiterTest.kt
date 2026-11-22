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

    // ---- NAV-5 : un recalcul n'attend pas une requête d'arrière-plan en vol ----

    @Test fun `a reroute interrupts a background request in flight, which is replayed afterwards`() = runTest {
        val l = limiter(); val order = mutableListOf<String>(); var inFlight = 0; var maxInFlight = 0
        suspend fun call(name: String, ms: Long): String {
            inFlight++; maxInFlight = maxOf(maxInFlight, inFlight); order += "$name@${now()}"
            try { delay(ms) } finally { inFlight-- }
            return name
        }
        var background: Permit<String>? = null
        launch { background = l.withPermit(ApiPriority.BACKGROUND) { call("profil", 15_000) } }
        advanceTimeBy(3_000); runCurrent()
        var reroute: Permit<String>? = null
        launch { reroute = l.withPermit(ApiPriority.GUIDANCE) { call("recalcul", 2_000) } }
        advanceUntilIdle()
        // Le recalcul part tout de suite (le créneau de la requête interrompue date de 3 s) ; le profil repart quand il a fini.
        assertEquals(listOf("profil@0", "recalcul@3000", "profil@5000"), order)
        assertEquals(Permit.Ran("recalcul"), reroute)
        assertEquals("l'appelant de la requête interrompue reçoit son résultat", Permit.Ran("profil"), background)
        assertEquals(20_000L, now())
        assertEquals(1, maxInFlight)
    }

    @Test fun `interrupting a background request never shortens the one second between two starts`() = runTest {
        val l = limiter(); val starts = mutableListOf<Long>()
        launch { l.withPermit(ApiPriority.BACKGROUND) { starts += now(); delay(15_000) } }
        advanceTimeBy(200); runCurrent()
        launch { l.withPermit(ApiPriority.GUIDANCE) { starts += now(); delay(300) } }
        advanceUntilIdle()
        // Départs : arrière-plan à 0, recalcul à 1 000 (pas à 200), arrière-plan rejoué à 2 000 (pas à 1 300).
        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }

    @Test fun `a reroute does not interrupt a user search or another reroute in flight`() = runTest {
        for (running in listOf(ApiPriority.INTERACTIVE, ApiPriority.GUIDANCE)) {
            val l = limiter(); val order = mutableListOf<String>(); val t0 = now()
            launch { l.withPermit(running) { order += "en vol@${now() - t0}"; delay(5_000); order += "fini@${now() - t0}" } }
            advanceTimeBy(1_000); runCurrent()
            launch { l.withPermit(ApiPriority.GUIDANCE) { order += "recalcul@${now() - t0}" } }
            advanceUntilIdle()
            assertEquals("$running", listOf("en vol@0", "fini@5000", "recalcul@5000"), order)
        }
    }

    @Test fun `a user search waits for the background request in flight, it does not interrupt it`() = runTest {
        val l = limiter(); val order = mutableListOf<String>()
        launch { l.withPermit(ApiPriority.BACKGROUND) { order += "profil@${now()}"; delay(5_000) } }
        advanceTimeBy(1_000); runCurrent()
        launch { l.withPermit(ApiPriority.INTERACTIVE) { order += "recherche@${now()}" } }
        advanceUntilIdle()
        assertEquals(listOf("profil@0", "recherche@5000"), order)
    }

    @Test fun `an interrupted background request keeps its rank among the queued ones`() = runTest {
        val l = limiter(); val order = mutableListOf<String>()
        launch { l.withPermit(ApiPriority.BACKGROUND) { order += "a@${now()}"; delay(15_000) } }
        launch { l.withPermit(ApiPriority.BACKGROUND) { order += "b@${now()}" } }
        advanceTimeBy(2_000); runCurrent()
        launch { l.withPermit(ApiPriority.GUIDANCE) { order += "recalcul@${now()}" } }
        advanceUntilIdle()
        assertEquals(listOf("a@0", "recalcul@2000", "a@3000", "b@18000"), order)
    }

    @Test fun `cancelling the caller of a background request in flight is still a cancellation, not a replay`() = runTest {
        val l = limiter(); var runs = 0
        val job = launch { l.withPermit(ApiPriority.BACKGROUND) { runs++; delay(15_000) } }
        advanceTimeBy(500); runCurrent()
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertEquals(1, runs)
        var startedAt = -1L
        l.withPermit { startedAt = now() }
        assertEquals("le créneau est rendu", 1_000L, startedAt)
    }

    @Test fun `a background request that fails while a reroute waits reports its own error`() = runTest {
        val l = limiter(); var error: Throwable? = null
        launch { error = runCatching { l.withPermit<Unit>(ApiPriority.BACKGROUND) { throw IllegalStateException("boom") } }.exceptionOrNull() }
        launch { l.withPermit(ApiPriority.GUIDANCE) { } }
        advanceUntilIdle()
        assertTrue("$error", error is IllegalStateException)
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
