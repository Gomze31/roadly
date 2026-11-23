package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** [ApiClient] et limiteur de débit : hôte limité (simulé), hôte auto-hébergé, `Retry-After`, User-Agent. */
class ApiClientRateLimitTest {
    private val server = MockWebServer()
    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    /** Client dont le serveur de test est traité comme une instance publique limitée, horloge = temps virtuel du test. */
    private fun TestScope.throttledClient() =
        ApiClient(OkHttpClient(), dispatchers, ValhallaRateLimiter(throttledHost = { true }, nowMs = { testScheduler.currentTime }))

    @Test fun `a self-hosted server is not throttled`() = runTest {
        val client = ApiClient(OkHttpClient(), dispatchers)
        repeat(3) { server.enqueue(MockResponse().setBody("ok")) }
        val results = (1..3).map { async { client.postJson(server.url("/route"), "{}") } }.awaitAll()
        assertTrue(results.all { it is Outcome.Success })
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `a throttled host gets its requests one second apart`() = runTest {
        val client = throttledClient()
        repeat(3) { server.enqueue(MockResponse().setBody("ok")) }
        val results = (1..3).map { async { client.postJson(server.url("/route"), "{}") } }.awaitAll()
        assertTrue(results.all { it is Outcome.Success })
        assertEquals(3, server.requestCount)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test fun `429 with Retry-After pauses the limiter before the retry`() = runTest {
        val client = throttledClient().apply { retryDelaysMs = listOf(0L) }
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", (client.get(server.url("/x")) as Outcome.Success).value)
        assertEquals(2, server.requestCount)
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test fun `429 without Retry-After backs off by default and stays RateLimited`() = runTest {
        val client = throttledClient().apply { retryDelaysMs = emptyList() }
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals(AppError.RateLimited, (client.get(server.url("/x")) as Outcome.Failure).error)
        assertEquals("ok", (client.get(server.url("/x")) as Outcome.Success).value)
        assertEquals(ValhallaRateLimiter.DEFAULT_BACKOFF_MS, testScheduler.currentTime)
    }

    @Test fun `503 with a long Retry-After fails fast afterwards without calling the server`() = runTest {
        val client = throttledClient()
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "120"))
        val first = (client.postJson(server.url("/route"), "{}") as Outcome.Failure).error
        assertTrue(first is AppError.ProviderUnavailable && first.httpCode == 503)
        val second = (client.postJson(server.url("/route"), "{}", priority = ApiPriority.GUIDANCE) as Outcome.Failure).error
        assertTrue(second is AppError.ProviderUnavailable && second.httpCode == 503)
        assertEquals(1, server.requestCount)
        assertEquals(0L, testScheduler.currentTime)
    }

    /** NAV-5 : l'appel d'arrière-plan lent est réellement annulé (connexion fermée), le recalcul part, puis l'appel est rejoué. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `a reroute cancels a slow background call in flight, which is replayed after it`() = runTest {
        val client = throttledClient()
        server.enqueue(MockResponse().setBody("lent").setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody("recalcul"))
        server.enqueue(MockResponse().setBody("profil"))
        val background = async { client.postJson(server.url("/trace_attributes"), "{}") }
        testScheduler.runCurrent()
        assertEquals("/trace_attributes", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)?.path)
        val reroute = client.postJson(server.url("/route"), "{}", priority = ApiPriority.GUIDANCE)
        assertEquals("recalcul", (reroute as Outcome.Success).value)
        assertEquals("le recalcul part au créneau suivant, 1 s après la requête interrompue", 1_000L, testScheduler.currentTime)
        assertEquals("profil", (background.await() as Outcome.Success).value)
        assertEquals("/route", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)?.path)
        assertEquals("/trace_attributes", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)?.path)
        assertEquals(3, server.requestCount)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test fun `a long 429 pause is reported as RateLimited without retry loop`() = runTest {
        val client = throttledClient() // délais de nouvelle tentative par défaut (2 s, 8 s)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "600"))
        assertEquals(AppError.RateLimited, (client.get(server.url("/x")) as Outcome.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test fun `Retry-After is read as seconds or as an HTTP date`() {
        assertEquals(7_000L, retryAfterMs(headersOf("Retry-After", "7"), 0L))
        assertEquals(0L, retryAfterMs(headersOf("Retry-After", "0"), 0L))
        val now = 1_795_276_800_000L // samedi 21 novembre 2026, 16:00:00 GMT
        assertEquals(30_000L, retryAfterMs(headersOf("Retry-After", "Sat, 21 Nov 2026 16:00:30 GMT"), now))
        assertEquals(0L, retryAfterMs(headersOf("Retry-After", "Sat, 21 Nov 2026 15:00:00 GMT"), now))
        assertNull(retryAfterMs(headersOf("Retry-After", "plus tard"), now))
        assertNull(retryAfterMs(headersOf("Retry-After", "-5"), now))
        assertNull(retryAfterMs(headersOf("X-Autre", "1"), now))
    }

    @Test fun `user agent names the app, its version and a real contact`() {
        assertEquals("Roadly/0.1.183 (+https://exemple.fr/contact)", UserAgentInterceptor.userAgent("0.1.183", "https://exemple.fr/contact"))
        assertTrue(BuildConfig.CONTACT.isNotBlank())
        assertFalse(BuildConfig.CONTACT.contains("roadly.example"))
    }
}
