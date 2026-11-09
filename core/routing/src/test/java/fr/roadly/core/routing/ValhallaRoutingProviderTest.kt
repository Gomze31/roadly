package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.StreetSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
import org.junit.Assert.assertNull
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

    @Test fun `a walking request uses pedestrian costing, even with a vehicle and toll preferences`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val truck = fr.roadly.core.model.VehicleProfile(fr.roadly.core.model.VehicleType.TRUCK, fr.roadly.core.model.FuelType.DIESEL, 28.0, null)
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, vehicle = truck, preferences = RoutePreferences(avoidTolls = true), pedestrian = true))
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("pedestrian", body["costing"]!!.jsonPrimitive.content)
        assertNull(body["costing_options"])
        assertEquals("fr-FR", body["language"]!!.jsonPrimitive.content)
    }

    @Test fun `a truck profile switches to truck costing with only the entered dimensions`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val truck = fr.roadly.core.model.VehicleProfile(
            fr.roadly.core.model.VehicleType.TRUCK, fr.roadly.core.model.FuelType.DIESEL, 28.0, null,
            fr.roadly.core.model.VehicleDimensions(heightM = 3.9, weightT = 38.0, axles = 5),
        )
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, vehicle = truck, preferences = RoutePreferences(avoidTolls = true)))
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("truck", body["costing"]!!.jsonPrimitive.content)
        val opts = body["costing_options"]!!.jsonObject["truck"]!!.jsonObject
        assertEquals("3.9", opts["height"]!!.jsonPrimitive.content)
        assertEquals("38.0", opts["weight"]!!.jsonPrimitive.content)
        assertEquals("5", opts["axle_count"]!!.jsonPrimitive.content)
        assertEquals("0", opts["use_tolls"]!!.jsonPrimitive.content)
        assertTrue("largeur non saisie → absente", opts["width"] == null)
    }

    @Test fun `motorcycle and taxi use their own costing, cars stay auto`() {
        assertEquals("motorcycle", provider.costingFor(fr.roadly.core.model.VehicleType.MOTORCYCLE))
        assertEquals("taxi", provider.costingFor(fr.roadly.core.model.VehicleType.TAXI))
        assertEquals("auto", provider.costingFor(fr.roadly.core.model.VehicleType.SUV))
        assertEquals("auto", provider.costingFor(null))
    }

    @Test fun `origin heading is sent on the first location only`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, originHeadingDeg = 90.4f))
        val locations = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["locations"]!!.jsonArray
        val first = locations[0].jsonObject
        assertEquals(90, first["heading"]!!.jsonPrimitive.int)
        assertEquals(45, first["heading_tolerance"]!!.jsonPrimitive.int)
        assertNull(locations[1].jsonObject["heading"])
        assertNull(locations[1].jsonObject["heading_tolerance"])
    }

    @Test fun `no heading is sent when the origin heading is null`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST))
        val first = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["locations"]!!.jsonArray[0].jsonObject
        assertNull(first["heading"])
        assertNull(first["heading_tolerance"])
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

    private fun rawManeuvers() = json.parseToJsonElement(fixture()).jsonObject["trip"]!!.jsonObject["legs"]!!.jsonArray[0].jsonObject["maneuvers"]!!.jsonArray

    @Test fun `verbal instructions come from the fixture in french`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val r = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
        val raw = rawManeuvers()
        assertEquals(raw[0].jsonObject["verbal_pre_transition_instruction"]!!.jsonPrimitive.content, r.maneuvers[0].verbalPre)
        assertEquals("Conduisez vers l'est.", r.maneuvers[0].verbalPre)
        assertNull("pas d'alerte pour le départ dans la fixture", r.maneuvers[0].verbalAlert)
        raw.forEachIndexed { i, m ->
            assertEquals(m.jsonObject["verbal_pre_transition_instruction"]?.jsonPrimitive?.content, r.maneuvers[i].verbalPre)
            assertEquals(m.jsonObject["verbal_transition_alert_instruction"]?.jsonPrimitive?.content, r.maneuvers[i].verbalAlert)
        }
        assertEquals(2, r.maneuvers[10].roundaboutExit)
        assertTrue("la fixture FOSSGIS ne contient ni voies ni panneaux", r.maneuvers.all { it.lanes.isEmpty() && it.exitNumber == null })
    }

    @Test fun `lanes and exit number are mapped when present`() = runTest {
        val root = json.parseToJsonElement(fixture()).jsonObject
        val trip = root["trip"]!!.jsonObject
        val leg = trip["legs"]!!.jsonArray[0].jsonObject
        val maneuvers = leg["maneuvers"]!!.jsonArray
        val enriched = JsonObject(maneuvers[1].jsonObject + mapOf(
            "lanes" to json.parseToJsonElement("""[{"directions":8,"valid":8},{"directions":2},{"directions":66,"active":64}]"""),
            "sign" to json.parseToJsonElement("""{"exit_number_elements":[{"text":"12"}],"exit_branch_elements":[{"text":"A62"}],"exit_toward_elements":[{"text":"Toulouse-Centre"},{"text":"Blagnac"},{"text":"Aéroport"}]}"""),
        ))
        val newLeg = JsonObject(leg + ("maneuvers" to JsonArray(maneuvers.toMutableList().also { it[1] = enriched })))
        val body = JsonObject(root + ("trip" to JsonObject(trip + ("legs" to JsonArray(listOf(newLeg))))))
        server.enqueue(MockResponse().setBody(body.toString()))
        val m = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single().maneuvers[1]
        assertEquals(
            listOf(Lane(setOf(LaneDirection.LEFT), true), Lane(setOf(LaneDirection.STRAIGHT), false), Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, active = true)),
            m.lanes,
        )
        assertEquals("12", m.exitNumber)
        assertEquals("Toulouse-Centre · Blagnac", m.exitToward)
        assertEquals("A62", m.exitBranch)
    }

    @Test fun `route preferences map to valhalla use flags`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidTolls = true, avoidHighways = true, avoidFerries = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("0", auto["use_tolls"]!!.jsonPrimitive.content)
        assertEquals("0", auto["use_highways"]!!.jsonPrimitive.content)
        assertEquals("0", auto["use_ferry"]!!.jsonPrimitive.content)
    }

    @Test fun `default preferences add no use flags`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertNull(auto["use_tolls"]); assertNull(auto["use_highways"]); assertNull(auto["use_ferry"])
    }

    @Test fun `avoid tunnels sends the strict exclude_tunnels flag`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidTunnels = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("true", auto["exclude_tunnels"]!!.jsonPrimitive.content)
        assertNull(auto["use_tolls"])
    }

    @Test fun `when no path exists without tunnels the route is computed again without the exclusion`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":442,"error":"No path could be found for input","status_code":400}"""))
        server.enqueue(MockResponse().setBody(fixture()))
        val out = provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidTunnels = true)))
        assertTrue(out is Outcome.Success)
        val first = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        val second = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("true", first["exclude_tunnels"]!!.jsonPrimitive.content)
        assertNull(second["exclude_tunnels"])
    }

    @Test fun `avoid unpaved sends exclude_unpaved, and falls back without it when no path exists`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":442,"error":"No path could be found for input","status_code":400}"""))
        server.enqueue(MockResponse().setBody(fixture()))
        val out = provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidUnpaved = true)))
        assertTrue(out is Outcome.Success)
        val first = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        val second = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("true", first["exclude_unpaved"]!!.jsonPrimitive.content)
        assertNull(second["exclude_unpaved"])
    }

    @Test fun `default preferences never exclude tunnels`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertNull(auto["exclude_tunnels"])
    }

    @Test fun `shortest variant keeps the avoid preferences`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.SHORTEST, preferences = RoutePreferences(avoidFerries = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("true", auto["shortest"]!!.jsonPrimitive.content)
        assertEquals("0", auto["use_ferry"]!!.jsonPrimitive.content)
        assertNull(auto["use_tolls"])
    }

    @Test fun `shortest without tolls uses the strict exclude_tolls flag, ignored by valhalla otherwise`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.SHORTEST, preferences = RoutePreferences(avoidTolls = true, avoidHighways = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("true", auto["shortest"]!!.jsonPrimitive.content)
        assertEquals("true", auto["exclude_tolls"]!!.jsonPrimitive.content)
        assertEquals("true", auto["exclude_highways"]!!.jsonPrimitive.content)
        assertNull(auto["exclude_ferries"])
    }

    @Test fun `shortest without tolls and no strict path falls back to distance weighting with the use flags`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":442,"error":"No path could be found for input","status_code":400}"""))
        server.enqueue(MockResponse().setBody(fixture()))
        val out = provider.route(RouteRequest(origin, dest, costing = RouteCosting.SHORTEST, preferences = RoutePreferences(avoidTolls = true)))
        assertTrue(out is Outcome.Success)
        server.takeRequest()
        val second = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertNull(second["exclude_tolls"])
        assertNull(second["shortest"])
        assertEquals("1.0", second["use_distance"]!!.jsonPrimitive.content)
        assertEquals("0", second["use_tolls"]!!.jsonPrimitive.content)
    }

    @Test fun `fastest without tolls keeps the soft use_tolls flag only`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidTolls = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("0", auto["use_tolls"]!!.jsonPrimitive.content)
        assertNull(auto["exclude_tolls"])
    }

    @Test fun `bearings, end index, post instruction, street names and side come from the fixture`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val r = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
        rawManeuvers().forEachIndexed { i, raw ->
            val m = raw.jsonObject
            assertEquals("bearing_before $i", m["bearing_before"]?.jsonPrimitive?.int, r.maneuvers[i].bearingBeforeDeg)
            assertEquals("bearing_after $i", m["bearing_after"]?.jsonPrimitive?.int, r.maneuvers[i].bearingAfterDeg)
            assertEquals("end_shape_index $i", m["end_shape_index"]?.jsonPrimitive?.int, r.maneuvers[i].endShapeIndex)
            assertEquals("verbal_post $i", m["verbal_post_transition_instruction"]?.jsonPrimitive?.content, r.maneuvers[i].verbalPost)
            assertEquals("street_names $i", (m["street_names"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(), r.maneuvers[i].streetNames)
        }
        // Relevé dans la fixture Paris : entrée du rond-point des Champs-Élysées (manœuvre 10) et destination à gauche.
        assertEquals(296, r.maneuvers[10].bearingBeforeDeg)
        assertEquals(355, r.maneuvers[10].bearingAfterDeg)
        assertEquals(224, r.maneuvers[10].endShapeIndex)
        assertNull(r.maneuvers[0].bearingBeforeDeg)
        assertEquals(StreetSide.LEFT, r.maneuvers.last().sideOfStreet)
        assertNull(r.maneuvers[5].sideOfStreet)
    }
}
