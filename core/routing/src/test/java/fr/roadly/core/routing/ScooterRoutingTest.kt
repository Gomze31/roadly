package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.ScooterRouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysis
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysisBuilder
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.range.RangeRisk
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.segment.EdgeAttributes
import fr.roadly.core.scooter.segment.WayCategory
import fr.roadly.core.scooter.valhalla.TraceAttributesResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class ScooterRoutingTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var api: ApiClient
    private lateinit var config: ProviderConfigRepository
    private val origin = GeoPoint(48.8566, 2.3522)
    private val dest = GeoPoint(48.8738, 2.2950)
    private val g2 = ScooterPresets.NINEBOT_MAX_G2
    private fun fixture() = javaClass.classLoader!!.getResource("fixtures/valhalla_route.json")!!.readText()
    private fun JsonObject.bicycle() = this["costing_options"]!!.jsonObject["bicycle"]!!.jsonObject

    @Before fun setUp() {
        server.start()
        val d = object : DispatcherProvider { override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined }
        val base = server.url("/").toString().trimEnd('/')
        config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = base, overpassBaseUrl = base))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        api = ApiClient(OkHttpClient(), d)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `scooter request uses bicycle costing, never car preferences`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val provider = ValhallaRoutingProvider(api, config, json)
        val req = RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidTolls = true, avoidHighways = true),
            scooter = ScooterRouteRequest(g2, ScooterRoutePreferences(goal = ScooterRouteGoal.SAFEST)))
        val route = (provider.route(req) as Outcome.Success).value.single()
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("bicycle", body["costing"]!!.jsonPrimitive.content)
        assertEquals("30", body["elevation_interval"]!!.jsonPrimitive.content)
        val opts = body.bicycle()
        assertNull(opts["use_tolls"]); assertNull(opts["use_highways"])
        assertEquals("1000.0", opts["steps_factor"]!!.jsonPrimitive.content)
        assertEquals(ScooterRouteGoal.SAFEST, route.scooterGoal)
    }

    @Test fun `car and scooter requests differ for the same trip`() = runTest {
        server.enqueue(MockResponse().setBody(fixture())); server.enqueue(MockResponse().setBody(fixture()))
        val provider = ValhallaRoutingProvider(api, config, json)
        val car = VehicleProfile(VehicleType.SEDAN, FuelType.SP95, 6.0, 1.8)
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, vehicle = car, preferences = RoutePreferences(avoidTolls = true)))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, vehicle = car, scooter = ScooterRouteRequest(g2, ScooterRoutePreferences())))
        val carBody = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val scooterBody = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("auto", carBody["costing"]!!.jsonPrimitive.content)
        assertEquals("0", carBody["costing_options"]!!.jsonObject["auto"]!!.jsonObject["use_tolls"]!!.jsonPrimitive.content)
        assertNull(carBody["elevation_interval"])
        assertEquals("bicycle", scooterBody["costing"]!!.jsonPrimitive.content)
    }

    @Test fun `exclude polygons are sent as lon lat rings`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val ring = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.0, 2.1), GeoPoint(48.1, 2.1), GeoPoint(48.0, 2.0))
        ValhallaRoutingProvider(api, config, json).route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, scooter = ScooterRouteRequest(g2, ScooterRoutePreferences()), excludePolygons = listOf(ring)))
        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val first = body["exclude_polygons"]!!.jsonArray[0].jsonArray[1].jsonArray
        assertEquals(2.1, first[0].jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(48.0, first[1].jsonPrimitive.content.toDouble(), 1e-9)
    }

    @Test fun `elevation profile is parsed when present`() = runTest {
        val withElevation = fixture().replaceFirst("\"maneuvers\"", "\"elevation\":[35.0,36.5,40.0],\"elevation_interval\":30,\"maneuvers\"")
        server.enqueue(MockResponse().setBody(withElevation))
        val r = (ValhallaRoutingProvider(api, config, json).route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, scooter = ScooterRouteRequest(g2, ScooterRoutePreferences()))) as Outcome.Success).value.single()
        assertEquals(listOf(35.0, 36.5, 40.0), r.elevationM)
        assertEquals(30.0, r.elevationIntervalM!!, 1e-9)
    }

    @Test fun `analyzer combines trace attributes and overpass tags`() = runTest {
        server.enqueue(MockResponse().setBody("""{"edges":[{"length":0.3,"use":"cycleway","road_class":"residential","surface":"paved_smooth","way_id":11},{"length":0.2,"use":"road","cycle_lane":"none","road_class":"residential","speed_limit":30,"way_id":12}],"admins":[{"country_code":"FR"}]}"""))
        server.enqueue(MockResponse().setBody("""{"elements":[{"type":"way","id":11,"tags":{"highway":"cycleway","lit":"yes"}},{"type":"way","id":12,"tags":{"highway":"residential","maxspeed":"30","zone:maxspeed":"FR:30"}}]}"""))
        val route = Route("r", RouteCosting.FASTEST, 500.0, 100.0, listOf(origin, GeoPoint(48.8590, 2.3500), dest), emptyList(), false, "valhalla")
        val a = (ValhallaScooterRouteAnalyzer(api, config, json).analyze(route, ScooterRouteRequest(g2, ScooterRoutePreferences()), withOsmTags = true) as Outcome.Success).value
        assertEquals("/trace_attributes", server.takeRequest().path)
        val overpass = server.takeRequest()
        assertEquals("/interpreter", overpass.path)
        assertTrue(URLDecoder.decode(overpass.body.readUtf8(), "UTF-8").contains("way(id:11,12)"))
        assertTrue(a.osmTagsAvailable)
        assertEquals(listOf(WayCategory.CYCLE_TRACK, WayCategory.SHARED_ROAD), a.segments.map { it.category })
        assertEquals(LegalStatus.ALLOWED, a.segments[1].legal.status) // zone 30 en agglomération
        assertEquals(true, a.segments[0].lit)
    }

    @Test fun `analysis survives overpass outage without inventing tags`() = runTest {
        server.enqueue(MockResponse().setBody("""{"edges":[{"length":0.2,"use":"road","speed_limit":30,"way_id":12}]}"""))
        server.enqueue(MockResponse().setResponseCode(504))
        val route = Route("r", RouteCosting.FASTEST, 200.0, 40.0, listOf(origin, dest), emptyList(), false, "valhalla")
        val a = (ValhallaScooterRouteAnalyzer(api, config, json).analyze(route, ScooterRouteRequest(g2, ScooterRoutePreferences()), withOsmTags = true) as Outcome.Success).value
        assertFalse(a.osmTagsAvailable)
        assertEquals(LegalStatus.UNVERIFIED, a.segments.single().legal.status)
    }

    // ── Cas d'usage (routage et analyse simulés) ──

    private fun line(n: Int) = (0..n).map { GeoPoint(43.6, 1.44 + it * 0.0001) }

    private inner class FakeRouting : RoutingProvider {
        override val id = "fake"
        val requests = mutableListOf<RouteRequest>()
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            val avoid = request.excludePolygons.isNotEmpty()
            val goal = request.scooter!!.goal
            // Le plus rapide passe par une voie interdite ; avec exclusion, détour autorisé.
            val n = if (goal == ScooterRouteGoal.FASTEST && !avoid) 40 else 50 + goal.ordinal
            val id = "$goal${if (avoid) "-avoid" else ""}"
            return Outcome.Success(listOf(Route(id, request.costing, n * 8.1, n * 1.5, line(n), listOf(
                Maneuver(ManeuverType.DEPART, "Départ", null, 0.0, 0.0, 0, null), Maneuver(ManeuverType.ARRIVE, "Arrivée", null, 0.0, 0.0, n, null),
            ), false, "fake")))
        }
    }

    private inner class FakeAnalyzer : ScooterRouteAnalyzer {
        override suspend fun analyze(route: Route, request: ScooterRouteRequest, withOsmTags: Boolean): Outcome<ScooterRouteAnalysis> {
            val total = route.distanceMeters
            val edges = if (route.id == "FASTEST") listOf(EdgeAttributes(100.0, "cycleway"), EdgeAttributes(100.0, "road", roadClass = "trunk", speedLimitKmh = 90), EdgeAttributes(total - 200, "cycleway"))
            else listOf(EdgeAttributes(total, "cycleway"))
            return Outcome.Success(ScooterRouteAnalysisBuilder.build(route, TraceAttributesResult(edges, listOf("FR")), null, request.profile))
        }
    }

    @Test fun `forbidden portion triggers a second calculation around it`() = runTest {
        val routing = FakeRouting()
        val uc = ScooterRouteOptionsUseCase(routing, FakeAnalyzer())
        val options = (uc(ScooterRouteOptionsUseCase.Params(origin, dest, g2, ScooterRoutePreferences(goal = ScooterRouteGoal.FASTEST), 0L, batteryPct = 80.0, temperatureC = 18.0)) as Outcome.Success).value
        val fastest = options.first { it.scooter!!.goal == ScooterRouteGoal.FASTEST }
        assertTrue(fastest.scooter!!.reroutedAroundForbidden)
        assertEquals(0.0, fastest.scooter!!.analysis!!.summary.forbiddenLengthM, 1e-9)
        assertTrue(routing.requests.any { it.excludePolygons.isNotEmpty() })
        // Les préférences voiture ne sont jamais transmises en mode trottinette.
        assertTrue(routing.requests.all { it.preferences == RoutePreferences() && it.vehicle == null })
        assertEquals(RangeRisk.OK, fastest.scooter!!.range.risk)
        assertTrue(options.any { "Le plus sécurisé" in it.scooter!!.badges })
    }

    @Test fun `missing battery gives unknown range and partial failures are tolerated`() = runTest {
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> =
                if (request.scooter!!.goal == ScooterRouteGoal.COMFORT) Outcome.Failure(AppError.Timeout)
                else FakeRouting().route(request)
        }
        val failingAnalyzer = object : ScooterRouteAnalyzer {
            override suspend fun analyze(route: Route, request: ScooterRouteRequest, withOsmTags: Boolean): Outcome<ScooterRouteAnalysis> = Outcome.Failure(AppError.Network)
        }
        val options = (ScooterRouteOptionsUseCase(routing, failingAnalyzer)(ScooterRouteOptionsUseCase.Params(origin, dest, g2, ScooterRoutePreferences(goal = ScooterRouteGoal.SAFEST), 0L)) as Outcome.Success).value
        assertEquals(2, options.size)
        options.forEach {
            assertNull(it.scooter!!.analysis)
            assertNotNull(it.scooter!!.analysisError)
            assertEquals(RangeRisk.UNKNOWN, it.scooter!!.range.risk)
            assertFalse("Le plus sécurisé" in it.scooter!!.badges)
        }
    }
}
