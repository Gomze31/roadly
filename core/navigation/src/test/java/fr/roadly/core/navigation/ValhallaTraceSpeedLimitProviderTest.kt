package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
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

class ValhallaTraceSpeedLimitProviderTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private var now = 100_000L
    private val cfg = MutableStateFlow(ProviderConfig.DEFAULT)
    private lateinit var provider: ValhallaTraceSpeedLimitProvider
    private lateinit var dispatchers: DispatcherProvider
    private lateinit var config: ProviderConfigRepository
    private val fixes = listOf(
        LocationFix(GeoPoint(48.8566, 2.3522), 90f, 13f, 5f, 0L),
        LocationFix(GeoPoint(48.8567, 2.3530), 90f, 13f, 5f, 1_000L),
    )

    @Before fun setUp() {
        server.start()
        dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        config = object : ProviderConfigRepository {
            override val config = cfg
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = ValhallaTraceSpeedLimitProvider(ApiClient(OkHttpClient(), dispatchers), config, json) { now }
    }

    /** Fournisseur dont le client HTTP échoue bruyamment au moindre appel réseau. */
    private fun tripwireProvider() = ValhallaTraceSpeedLimitProvider(
        ApiClient(
            OkHttpClient.Builder().addInterceptor { throw AssertionError("network call on public instance") }.build(),
            dispatchers,
        ),
        config, json,
    ) { now }
    @After fun tearDown() = server.shutdown()

    private fun selfHosted() { cfg.value = ProviderConfig.DEFAULT.copy(valhallaBaseUrl = server.url("/").toString().trimEnd('/')) }

    @Test fun `public FOSSGIS instance is never called`() = runTest {
        assertTrue(ValhallaTraceSpeedLimitProvider.isPublicInstance("https://valhalla1.openstreetmap.de/trace_attributes".toHttpUrl()))
        assertFalse(ValhallaTraceSpeedLimitProvider.isPublicInstance("https://valhalla.exemple.fr/trace_attributes".toHttpUrl()))
        assertEquals(Outcome.Success(null), tripwireProvider().speedLimitKmh(fixes))
        assertEquals(0, server.requestCount)
    }

    @Test fun `public FOSSGIS instance with a trailing slash is never called`() = runTest {
        cfg.value = ProviderConfig.DEFAULT.copy(valhallaBaseUrl = "https://valhalla1.openstreetmap.de/")
        assertEquals(Outcome.Success(null), tripwireProvider().speedLimitKmh(fixes))
    }

    @Test fun `self hosted instance returns the last edge limit and sends the required filters`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":30,"names":["Rue A"]},{"speed_limit":50,"names":["Rue B"],"road_class":"secondary"}]}"""))
        assertEquals(Outcome.Success(50), provider.speedLimitKmh(fixes))
        val req = server.takeRequest()
        assertEquals("/trace_attributes", req.path)
        val body = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("auto", body["costing"]!!.jsonPrimitive.content)
        assertEquals("map_snap", body["shape_match"]!!.jsonPrimitive.content)
        assertEquals(2, body["shape"]!!.jsonArray.size)
        assertEquals(
            listOf("edge.speed_limit", "edge.names", "edge.road_class"),
            body["filters"]!!.jsonObject["attributes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test fun `limit comes from the edge of the last matched point`() = runTest {
        selfHosted()
        server.enqueue(
            MockResponse().setBody(
                """{"matched_points":[{"edge_index":0},{"edge_index":0}],"edges":[{"speed_limit":30},{"speed_limit":50}]}""",
            ),
        )
        assertEquals(Outcome.Success(30), provider.speedLimitKmh(fixes))
    }

    @Test fun `without matched points the last edge is used`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":30},{"speed_limit":50}]}"""))
        assertEquals(Outcome.Success(50), provider.speedLimitKmh(fixes))
    }

    @Test fun `out of range edge index falls back to the last edge`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"matched_points":[{"edge_index":7}],"edges":[{"speed_limit":30},{"speed_limit":50}]}"""))
        assertEquals(Outcome.Success(50), provider.speedLimitKmh(fixes))
    }

    @Test fun `unknown or unlimited limits give null`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":0}]}"""))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
        now += 5_000
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":"unlimited"}]}"""))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
        now += 5_000
        server.enqueue(MockResponse().setBody("""{"edges":[]}"""))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
    }

    @Test fun `at most one call every 5 s`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":70}]}"""))
        assertEquals(Outcome.Success(70), provider.speedLimitKmh(fixes))
        now += 4_999
        assertEquals(Outcome.Success(70), provider.speedLimitKmh(fixes))
        assertEquals(1, server.requestCount)
        now += 1
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":90}]}"""))
        assertEquals(Outcome.Success(90), provider.speedLimitKmh(fixes))
        assertEquals(2, server.requestCount)
    }

    @Test fun `failure clears the limit`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":70}]}"""))
        provider.speedLimitKmh(fixes)
        now += 5_000
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(provider.speedLimitKmh(fixes) is Outcome.Failure)
        now += 1_000
        assertEquals("pas de valeur périmée après un échec", Outcome.Success(null), provider.speedLimitKmh(fixes))
    }

    @Test fun `fewer than two fixes is not enough`() = runTest {
        selfHosted()
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes.take(1)))
        assertEquals(0, server.requestCount)
    }

    @Test fun `noop provider always answers unknown`() = runTest {
        assertNull((NoopSpeedLimitProvider().speedLimitKmh(fixes) as Outcome.Success).value)
    }
}
