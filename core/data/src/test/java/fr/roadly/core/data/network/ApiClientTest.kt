package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var client: ApiClient
    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before fun setUp() { server.start(); client = ApiClient(OkHttpClient(), dispatchers) }
    @After fun tearDown() = server.shutdown()

    @Test fun `200 returns body`() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val r = client.get(server.url("/x"))
        assertEquals("""{"ok":true}""", (r as Outcome.Success).value)
    }

    @Test fun `429 maps to RateLimited`() = runTest {
        client.retryDelaysMs = emptyList() // sans nouvelle tentative
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(AppError.RateLimited, (client.get(server.url("/x")) as Outcome.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test fun `429 twice then 200 retries and succeeds`() = runTest {
        client.retryDelaysMs = listOf(0L, 0L)
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", (client.get(server.url("/x")) as Outcome.Success).value)
        assertEquals(3, server.requestCount)
    }

    @Test fun `429 three times gives up with RateLimited`() = runTest {
        client.retryDelaysMs = listOf(0L, 0L)
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) }
        assertEquals(AppError.RateLimited, (client.get(server.url("/x")) as Outcome.Failure).error)
        assertEquals(3, server.requestCount)
    }

    @Test fun `503 maps to ProviderUnavailable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val e = (client.get(server.url("/x")) as Outcome.Failure).error
        assertTrue(e is AppError.ProviderUnavailable && e.httpCode == 503)
    }

    @Test fun `400 maps to Http with body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad"}"""))
        val e = (client.postJson(server.url("/x"), "{}") as Outcome.Failure).error
        assertTrue(e is AppError.Http && e.code == 400 && e.body.contains("bad"))
    }

    @Test fun `connection failure maps to Network`() = runTest {
        val url = server.url("/x"); server.shutdown()
        assertEquals(AppError.Network, (client.get(url) as Outcome.Failure).error)
    }

    @Test fun `cancellation while waiting does not throw`() = runTest {
        server.enqueue(MockResponse().setBody("late").setBodyDelay(500, TimeUnit.MILLISECONDS))
        var failure: Throwable? = null
        val job = launch(Dispatchers.Default) {
            try { client.get(server.url("/x")) } catch (e: CancellationException) { throw e } catch (e: Throwable) { failure = e }
        }
        kotlinx.coroutines.withContext(Dispatchers.Default) { kotlinx.coroutines.delay(50) } // temps réel (runTest saute les delay)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertNull(failure)
    }
}
