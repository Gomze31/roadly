package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.0 : conduite libre — règles pures, décodage, et session (limitation espacée, zones, exclusivité avec le guidage). */
@OptIn(ExperimentalCoroutinesApi::class)
class FreeDriveTest {
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Règles pures ----

    @Test fun `public policy queries at most every 20 s and 250 m, sooner after a turn`() {
        val p = RoadQueryPolicy.PUBLIC
        assertTrue(p.shouldQuery(0, null, 0.0, false))
        assertFalse(p.shouldQuery(10_000, 0, 1_000.0, false))
        assertFalse("à l'arrêt : rien", p.shouldQuery(60_000, 0, 10.0, false))
        assertTrue(p.shouldQuery(20_000, 0, 250.0, false))
        assertFalse(p.shouldQuery(5_000, 0, 0.0, true))
        assertTrue(p.shouldQuery(8_000, 0, 0.0, true))
    }

    @Test fun `bearing and turn detection`() {
        val o = GeoPoint(45.0, 2.0)
        assertEquals(90.0, FreeDriveRules.bearing(o, GeoPoint(45.0, 2.01)), 0.5)
        assertEquals(0.0, FreeDriveRules.bearing(o, GeoPoint(45.01, 2.0)), 0.5)
        assertEquals(20.0, FreeDriveRules.angleDiff(350.0, 10.0), 1e-9)
        assertTrue(FreeDriveRules.turned(0.0, LocationFix(o, 90f, 10f, 5f, 0)))
        assertFalse("lent : cap non fiable", FreeDriveRules.turned(0.0, LocationFix(o, 90f, 1f, 5f, 0)))
        assertFalse(FreeDriveRules.turned(null, LocationFix(o, 90f, 10f, 5f, 0)))
    }

    private fun zone(id: String, lat: Double, lon: Double, limit: Int? = 50) = VigilanceZoneEntity(id, lat, lon, null, null, null, limit, 0, source = "test", confidence = 85, precisionM = 5.0)

    @Test fun `snapshot parse keeps the last matched edge and ignores unmatched points`() {
        // Réponse réelle FOSSGIS (2026-11-05, Paris), index hors bornes = point non reconnu.
        val body = """{"units":"kilometers","edges":[{"speed_limit":30,"names":["Avenue Victoria"]}],"matched_points":[{"edge_index":18446744073709551615},{"edge_index":0},{}],"alternate_paths":[]}"""
        assertEquals(RoadSnapshot(30, "Avenue Victoria"), ValhallaRoadSnapshotProvider.parse(json, body))
        assertNull(ValhallaRoadSnapshotProvider.parse(json, """{"edges":[]}"""))
        assertEquals(RoadSnapshot(null, null), ValhallaRoadSnapshotProvider.parse(json, """{"edges":[{"speed_limit":0}]}"""))
    }

    // ---- Session ----

    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private var snapshotCalls = 0
    private var road: RoadSnapshot? = RoadSnapshot(80, "D 820")
    private val roads = object : RoadSnapshotProvider {
        override suspend fun policy() = RoadQueryPolicy.PUBLIC
        override suspend fun snapshot(recent: List<LocationFix>): Outcome<RoadSnapshot?> { snapshotCalls++; return Outcome.Success(road) }
    }
    private var zones: List<VigilanceZoneEntity> = emptyList()
    private val vigilance = object : VigilanceRepository {
        override val count = flowOf(0)
        override val updatedAtMs = flowOf<Long?>(null)
        override suspend fun zonesIn(box: BoundingBox) = zones
        override suspend fun refresh(): Outcome<Int> = Outcome.Failure(AppError.NoResult)
        override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? = null
    }

    private fun TestScope.sessions(): Pair<FreeDriveSession, NavigationSession> {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.NoResult)
        }
        val nav = NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        val free = FreeDriveSession(location, roads, vigilance, speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopTripRecorder(), nav, dispatchers, backgroundScope)
        return free to nav
    }

    /** Fix [m] mètres à l'est du point de départ, cap est, 15 m/s, horodaté à l'instant virtuel. */
    private fun TestScope.fixEast(m: Double) = LocationFix(GeoPoint(45.0, 2.0 + m / (111_320.0 * Math.cos(Math.toRadians(45.0)))), 90f, 15f, 5f, testScheduler.currentTime)

    private fun active(s: FreeDriveSession) = s.state.value as FreeDriveState.Active

    @Test fun `limit comes from spaced road queries, distance accumulates, stop releases gps`() = runTest {
        val (free, _) = sessions()
        assertTrue(free.start()); runCurrent()
        assertTrue(location.guidanceActive)
        location.emit(fixEast(0.0)); runCurrent()
        location.emit(fixEast(15.0)); runCurrent()
        assertEquals(1, snapshotCalls)
        assertEquals(80, active(free).speedLimitKmh)
        assertEquals("D 820", active(free).roadName)
        advanceTimeBy(5_000); location.emit(fixEast(90.0)); runCurrent()
        assertEquals("ni 20 s ni 250 m : pas de nouvelle requête", 1, snapshotCalls)
        advanceTimeBy(20_000); location.emit(fixEast(400.0)); runCurrent()
        assertEquals(2, snapshotCalls)
        assertEquals(400.0, active(free).distanceM, 1.0)
        free.stop(); runCurrent()
        assertEquals(FreeDriveState.Idle, free.state.value)
        assertFalse(location.guidanceActive)
    }

    @Test fun `a vigilance zone ahead is announced once`() = runTest {
        val p = { m: Double -> GeoPoint(45.0, 2.0 + m / (111_320.0 * Math.cos(Math.toRadians(45.0)))) }
        zones = listOf(zone("z", p(250.0).lat, p(250.0).lon, 50))
        val (free, _) = sessions()
        free.start(); runCurrent()
        // Cap filtré (3 fixes), sens inconnu : ouverture à moins de 300 m après 3 observations consécutives.
        for (m in listOf(-60.0, -45.0, -30.0, -15.0, 0.0, 15.0, 30.0, 45.0, 60.0)) { location.emit(fixEast(m)); runCurrent() }
        assertEquals("z", active(free).zone?.id)
        location.emit(fixEast(75.0)); runCurrent()
        assertEquals(1, speech.spoken.count { it.startsWith("Zone de danger") })
    }

    @Test fun `starting guidance ends free drive without releasing the gps, and free drive cannot start while guiding`() = runTest {
        val (free, nav) = sessions()
        free.start(); runCurrent()
        val route = TestRoutes.straight()
        nav.start(RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        assertEquals(FreeDriveState.Idle, free.state.value)
        assertTrue("le GPS appartient au guidage", location.guidanceActive)
        assertFalse(free.start())
    }

    @Test fun `start is refused without location permission`() = runTest {
        location.permission.value = fr.roadly.core.location.LocationPermissionState.DENIED
        val (free, _) = sessions()
        assertFalse(free.start())
        assertTrue(free.state.value is FreeDriveState.Failed)
    }
}
