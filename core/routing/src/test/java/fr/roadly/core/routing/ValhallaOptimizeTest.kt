package fr.roadly.core.routing

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValhallaOptimizeTest {
    private val server = MockWebServer()
    private lateinit var provider: ValhallaRoutingProvider

    @Before fun setUp() {
        server.start()
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = ValhallaRoutingProvider(ApiClient(OkHttpClient(), dispatchers), config, Json { ignoreUnknownKeys = true })
    }
    @After fun tearDown() = server.shutdown()

    private val o = GeoPoint(48.85, 2.35); private val d = GeoPoint(48.86, 2.36)
    private val stops = listOf(GeoPoint(48.851, 2.351), GeoPoint(48.852, 2.352), GeoPoint(48.853, 2.353))

    @Test fun `optimized order comes from original_index without the fixed ends`() = runTest {
        server.enqueue(MockResponse().setBody("""{"trip":{"locations":[{"original_index":0},{"original_index":3},{"original_index":1},{"original_index":2},{"original_index":4}],"legs":[],"summary":{}}}"""))
        assertEquals(listOf(2, 0, 1), (provider.optimizeOrder(o, stops, d) as Outcome.Success).value)
        assertTrue(server.takeRequest().path!!.endsWith("/optimized_route"))
    }

    @Test fun `an incomplete order is rejected`() = runTest {
        server.enqueue(MockResponse().setBody("""{"trip":{"locations":[{"original_index":0},{"original_index":1},{"original_index":4}],"legs":[],"summary":{}}}"""))
        assertTrue(provider.optimizeOrder(o, stops, d) is Outcome.Failure)
    }

    @Test fun `fewer than two stops never calls the server`() = runTest {
        assertEquals(listOf(0), (provider.optimizeOrder(o, stops.take(1), d) as Outcome.Success).value)
        assertEquals(0, server.requestCount)
    }
}
