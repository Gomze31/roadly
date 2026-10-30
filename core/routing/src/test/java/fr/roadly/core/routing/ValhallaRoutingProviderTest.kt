package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValhallaRoutingProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: ValhallaRoutingProvider
    private val json = Json { ignoreUnknownKeys = true }
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
        provider = ValhallaRoutingProvider(ApiClient(OkHttpClient(), dispatchers), config, json)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `parses trip into Route with geometry near origin and destination`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val routes = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value
        val r = routes.single()
        assertEquals(RouteCosting.FASTEST, r.costing)
        assertTrue(r.distanceMeters in 4000.0..9000.0)
        assertTrue(r.durationSeconds in 300.0..2400.0)
        assertTrue(r.geometry.first().distanceTo(origin) < 100.0)
        assertTrue(r.geometry.last().distanceTo(dest) < 100.0)
        assertEquals(ManeuverType.DEPART, r.maneuvers.first().type)
        assertEquals(ManeuverType.ARRIVE, r.maneuvers.last().type)
        assertTrue(r.maneuvers.first().instruction.isNotBlank())
    }

    @Test fun `request body uses french, kilometers and costing options`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.NO_TOLL))
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("fr-FR", body["language"]!!.jsonPrimitive.content)
        assertEquals("kilometers", body["units"]!!.jsonPrimitive.content)
        assertEquals("auto", body["costing"]!!.jsonPrimitive.content)
        assertEquals("0", body["costing_options"]!!.jsonObject["auto"]!!.jsonObject["use_tolls"]!!.jsonPrimitive.content)
        assertEquals(2, body["locations"]!!.let { it as kotlinx.serialization.json.JsonArray }.size)
    }

    @Test fun `shortest sets shortest flag`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.SHORTEST))
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("true", body["costing_options"]!!.jsonObject["auto"]!!.jsonObject["shortest"]!!.jsonPrimitive.content)
    }

    @Test fun `valhalla 400 no path maps to NoResult`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":442,"error":"No path could be found for input","status_code":400}"""))
        assertEquals(AppError.NoResult, (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Failure).error)
    }

    @Test fun `waypoints are sent between origin and destination in order`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val waypoint = GeoPoint(48.86, 2.33)
        provider.route(RouteRequest(origin, dest, waypoints = listOf(waypoint), costing = RouteCosting.FASTEST))
        val locations = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["locations"]!!.jsonArray
        assertEquals(3, locations.size)
        val pts = locations.map { it.jsonObject["lat"]!!.jsonPrimitive.double to it.jsonObject["lon"]!!.jsonPrimitive.double }
        assertEquals(listOf(origin.lat to origin.lon, waypoint.lat to waypoint.lon, dest.lat to dest.lon), pts)
    }

    @Test fun `two legs are concatenated without duplicate junction point`() = runTest {
        val trip = json.parseToJsonElement(fixture()).jsonObject["trip"]!!.jsonObject
        val leg = trip["legs"]!!.jsonArray[0].jsonObject
        val n = Polyline6.decode(leg["shape"]!!.jsonPrimitive.content).size
        val legManeuvers = leg["maneuvers"]!!.jsonArray
        val twoLegs = buildJsonObject {
            put("trip", buildJsonObject {
                put("summary", trip["summary"]!!)
                put("legs", buildJsonArray { add(leg); add(leg) })
            })
        }
        server.enqueue(MockResponse().setBody(twoLegs.toString()))
        val route = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
        assertEquals(2 * n - 1, route.geometry.size)
        assertEquals(2 * legManeuvers.size, route.maneuvers.size)
        val m = legManeuvers.size
        // Premier et dernier jalons de la 2e étape : décalés de n - 1 (le point de jonction n'est pas dupliqué).
        listOf(0, 1, m - 1).forEach { i ->
            val original = legManeuvers[i].jsonObject["begin_shape_index"]!!.jsonPrimitive.int
            assertEquals(n - 1 + original, route.maneuvers[m + i].beginShapeIndex)
        }
        assertTrue(route.maneuvers.all { it.beginShapeIndex in route.geometry.indices })
    }
}
