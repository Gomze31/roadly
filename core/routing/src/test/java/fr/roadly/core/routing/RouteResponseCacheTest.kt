package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cache des réponses `/route` : la classe seule (durée, bornes), puis à travers [ValhallaRoutingProvider]. */
class RouteResponseCacheTest {
    private var now = 0L

    @Test fun `hit within the TTL, miss once expired`() {
        val cache = RouteResponseCache(ttlMs = 60_000, nowMs = { now })
        cache.put("k", "réponse")
        now = 59_999
        assertEquals("réponse", cache.get("k"))
        now = 60_000
        assertNull(cache.get("k"))
        // Une lecture n'a pas prolongé la durée de vie, et l'entrée expirée a disparu.
        now = 0
        assertNull(cache.get("k"))
    }

    @Test fun `a different body is a different entry`() {
        val cache = RouteResponseCache(nowMs = { now })
        cache.put(RouteResponseCache.key("https://v/route", """{"costing":"auto"}"""), "a")
        assertNull(cache.get(RouteResponseCache.key("https://v/route", """{"costing":"truck"}""")))
        assertNull(cache.get(RouteResponseCache.key("https://autre/route", """{"costing":"auto"}""")))
        assertEquals("a", cache.get(RouteResponseCache.key("https://v/route", """{"costing":"auto"}""")))
    }

    @Test fun `bounded in entries and in size`() {
        val cache = RouteResponseCache(maxEntries = 2, maxEntryChars = 10, nowMs = { now })
        cache.put("a", "1"); cache.put("b", "2")
        assertEquals("1", cache.get("a")) // « a » redevient la plus récemment utilisée
        cache.put("c", "3")
        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        assertEquals("3", cache.get("c"))
        cache.put("trop", "x".repeat(11))
        assertNull(cache.get("trop"))
    }
}

class ValhallaRouteCachingTest {
    private val server = MockWebServer()
    private lateinit var provider: ValhallaRoutingProvider
    private fun fixture() = javaClass.classLoader!!.getResource("fixtures/valhalla_route.json")!!.readText()
    private val origin = GeoPoint(48.8566, 2.3522)
    private val dest = GeoPoint(48.8738, 2.2950)

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

    @Test fun `the same search twice hits the server once`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val request = RouteRequest(origin, dest, costing = RouteCosting.FASTEST)
        val first = (provider.route(request) as Outcome.Success).value.single()
        val second = (provider.route(request) as Outcome.Success).value.single()
        assertEquals(1, server.requestCount)
        assertEquals(first.geometry, second.geometry)
        assertEquals(first.maneuvers, second.maneuvers)
        assertNotEquals(first.id, second.id) // chaque réponse reste un tracé à part entière
    }

    @Test fun `another variant of the same search is a new request`() = runTest {
        repeat(2) { server.enqueue(MockResponse().setBody(fixture())) }
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.NO_TOLL))
        assertEquals(2, server.requestCount)
    }

    @Test fun `errors are never cached`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":442,"error":"No path could be found for input"}"""))
        server.enqueue(MockResponse().setBody("pas du json"))
        server.enqueue(MockResponse().setBody(fixture()))
        val request = RouteRequest(origin, dest, costing = RouteCosting.FASTEST)
        assertTrue((provider.route(request) as Outcome.Failure).error is AppError.ProviderUnavailable)
        assertEquals(AppError.NoResult, (provider.route(request) as Outcome.Failure).error)
        assertTrue((provider.route(request) as Outcome.Failure).error is AppError.Parse)
        assertTrue(provider.route(request) is Outcome.Success)
        assertEquals(4, server.requestCount)
    }

    @Test fun `a reroute with a heading always asks the server`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setBody(fixture())) }
        val reroute = RouteRequest(origin, dest, costing = RouteCosting.FASTEST, originHeadingDeg = 90f)
        assertTrue(provider.route(reroute) is Outcome.Success)
        assertTrue(provider.route(reroute) is Outcome.Success)
        assertEquals(2, server.requestCount)
        // … et sa réponse ne sert pas non plus à une recherche sans cap.
        assertTrue(provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) is Outcome.Success)
        assertEquals(3, server.requestCount)
    }

    @Test fun `a reroute at standstill, without heading, is never served from the cache either`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setBody(fixture())) }
        // Recherche d'itinéraire, puis recalcul depuis le même point, véhicule arrêté (pas de cap exploitable).
        assertTrue(provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) is Outcome.Success)
        val reroute = RouteRequest(origin, dest, costing = RouteCosting.FASTEST, reroute = true)
        assertTrue(provider.route(reroute) is Outcome.Success)
        assertTrue(provider.route(reroute) is Outcome.Success)
        assertEquals(3, server.requestCount)
    }
}
