package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pannes du serveur Valhalla vues par le vrai fournisseur (MockWebServer local, aucun appel à un serveur public) : chaque cas
 * doit rendre un `Outcome.Failure` typé, sans exception.
 */
class ValhallaRoutingProviderFailureTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val origin = GeoPoint(48.8566, 2.3522)
    private val dest = GeoPoint(48.8738, 2.2950)
    private val request = RouteRequest(origin, dest, costing = RouteCosting.FASTEST)
    private fun fixture() = javaClass.classLoader!!.getResource("fixtures/valhalla_route.json")!!.readText()

    private fun provider(client: OkHttpClient = OkHttpClient()): ValhallaRoutingProvider {
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        return ValhallaRoutingProvider(ApiClient(client, dispatchers), config, json)
    }

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    private fun error(out: Outcome<List<Route>>): AppError = (out as Outcome.Failure).error

    /** La première étape de la fixture, avec [change] appliqué à ses champs. */
    private fun bodyWithLeg(change: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
        val root = json.parseToJsonElement(fixture()).jsonObject
        val trip = root["trip"]!!.jsonObject
        val leg = trip["legs"]!!.jsonArray[0].jsonObject.toMutableMap().also(change)
        return JsonObject(root + ("trip" to JsonObject(trip + ("legs" to JsonArray(listOf(JsonObject(leg))))))).toString()
    }

    @Test fun `HTTP 500 is a provider failure with its code, in one request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        assertEquals(AppError.ProviderUnavailable(server.hostName, 500), error(provider().route(request)))
        assertEquals(1, server.requestCount)
    }

    @Test fun `HTTP 503 is a provider failure with its code, in one request`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30").setBody("<html>Service Unavailable</html>"))
        assertEquals(AppError.ProviderUnavailable(server.hostName, 503), error(provider().route(request)))
        assertEquals(1, server.requestCount)
    }

    @Test fun `HTTP 429 is RateLimited after the two spaced retries of the HTTP client, never more`() = runTest {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"Too Many Requests"}""")) }
        assertEquals(AppError.RateLimited, error(provider().route(request)))
        assertEquals("1 requête + 2 nouvelles tentatives (2 s puis 8 s)", 3, server.requestCount)
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test fun `HTTP 429 then a valid answer gives the route`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody(fixture()))
        assertTrue(provider().route(request) is Outcome.Success)
        assertEquals(2, server.requestCount)
    }

    @Test fun `a server that accepts the request and never answers is a Timeout`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val client = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        assertEquals(AppError.Timeout, error(provider(client).route(request)))
        assertEquals(1, server.requestCount)
    }

    @Test fun `a connection cut in the middle of the body is a Network failure`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertEquals(AppError.Network, error(provider().route(request)))
    }

    @Test fun `truncated JSON in a 200 is a Parse failure`() = runTest {
        val whole = fixture()
        server.enqueue(MockResponse().setBody(whole.take(whole.length / 2)))
        assertTrue(error(provider().route(request)) is AppError.Parse)
    }

    @Test fun `an HTML page, an empty body or JSON without trip in a 200 is a Parse failure`() = runTest {
        val p = provider()
        listOf("<html><body>502 Bad Gateway</body></html>", "", "{}", "[]", """{"trip":null}""", """{"trip":{"legs":"oops"}}""").forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            assertTrue("corps : $body", error(p.route(request)) is AppError.Parse)
        }
    }

    @Test fun `a 200 with empty legs is NoResult`() = runTest {
        server.enqueue(MockResponse().setBody("""{"trip":{"legs":[],"summary":{"length":0.0,"time":0.0},"status":0}}"""))
        assertEquals(AppError.NoResult, error(provider().route(request)))
    }

    @Test fun `a leg without shape is NoResult`() = runTest {
        server.enqueue(MockResponse().setBody(bodyWithLeg { it.remove("shape") }))
        assertEquals(AppError.NoResult, error(provider().route(request)))
    }

    /** Un tracé sans manœuvre ferait échouer le guidage (départ, prochaine manœuvre) : il n'est pas rendu. */
    @Test fun `a leg without maneuvers is NoResult, never a route without maneuvers`() = runTest {
        server.enqueue(MockResponse().setBody(bodyWithLeg { it.remove("maneuvers") }))
        assertEquals(AppError.NoResult, error(provider().route(request)))
        server.enqueue(MockResponse().setBody(bodyWithLeg { it["maneuvers"] = JsonArray(emptyList()) }))
        assertEquals(AppError.NoResult, error(provider().route(request)))
    }

    @Test fun `a shape cut in the middle of a point is a Parse failure`() = runTest {
        val shape = json.parseToJsonElement(fixture()).jsonObject["trip"]!!.jsonObject["legs"]!!.jsonArray[0].jsonObject["shape"]!!.toString().trim('"')
        // Un octet de continuation en dernier caractère : le décodeur attend une suite qui n'existe pas.
        server.enqueue(MockResponse().setBody(bodyWithLeg { it["shape"] = kotlinx.serialization.json.JsonPrimitive(shape.take(40) + "~") }))
        assertTrue(error(provider().route(request)) is AppError.Parse)
    }

    @Test fun `HTTP 400 without a Valhalla error code stays an Http failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":154,"error":"Path distance exceeds the max distance limit","status_code":400}"""))
        val p = provider()
        assertEquals(400, (error(p.route(request)) as AppError.Http).code)
        assertEquals(400, (error(p.route(request)) as AppError.Http).code)
    }

    @Test fun `a failure is never kept in the response cache - the same request is sent again and succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody(fixture()))
        val p = provider()
        assertTrue(p.route(request) is Outcome.Failure)
        assertTrue(p.route(request) is Outcome.Failure)
        assertTrue(p.route(request) is Outcome.Success)
        assertEquals(3, server.requestCount)
    }
}
