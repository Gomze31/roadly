package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceActionsTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val requests = mutableListOf<RouteRequest>()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            return Outcome.Success(listOf(route.copy(id = "r${requests.size}", durationSeconds = route.durationSeconds + 60.0 * requests.size)))
        }
    }
    private val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    private fun place(id: String, p: GeoPoint) = Place(id, id, null, p, PlaceKind.POI, "test")

    private fun TestScope.fixture(): Pair<NavigationSession, GuidanceActions> {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val clock = NavigationClock { testScheduler.currentTime }
        val session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), clock, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        return session to GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicles, clock)
    }

    private suspend fun TestScope.guideAt(actions: GuidanceActions, along: Double, stops: List<Place> = emptyList()) {
        actions.start(RouteOption(route, "Le plus rapide", 0L, null, false), RoutePreferences(), null, stops, destinationName = "Arrivée")
        runCurrent()
        location.emit(LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L))
        runCurrent()
    }

    @Test fun `adding a stop reroutes from the snapped position via the stop to the same destination`() = runTest {
        val (session, actions) = fixture()
        guideAt(actions, 300.0)
        val stop = place("s1", tracker.pointAt(800.0))
        val r = actions.addStop(stop); runCurrent()
        assertTrue(r is Outcome.Success)
        val req = requests.last()
        assertEquals((session.state.value as NavigationState.Guiding).route.id, (r as Outcome.Success).value.route.id)
        assertEquals(listOf(stop.point), req.waypoints)
        assertEquals(route.geometry.last(), req.destination)
        assertEquals(listOf("s1"), actions.remainingStops().map { it.id })
        assertEquals("Arrivée", actions.destinationName)
    }

    @Test fun `a stop behind the vehicle is no longer remaining`() = runTest {
        val (_, actions) = fixture()
        guideAt(actions, 900.0, stops = listOf(place("passed", tracker.pointAt(200.0)), place("ahead", tracker.pointAt(1_300.0))))
        assertEquals(listOf("ahead"), actions.remainingStops().map { it.id })
    }

    @Test fun `removing a stop keeps the other remaining stops`() = runTest {
        val (_, actions) = fixture()
        guideAt(actions, 100.0, stops = listOf(place("a", tracker.pointAt(500.0)), place("b", tracker.pointAt(900.0))))
        actions.removeStop("a"); runCurrent()
        assertEquals(listOf(tracker.pointAt(900.0)), requests.last().waypoints)
        assertTrue(actions.removeStop("unknown") is Outcome.Failure)
    }

    @Test fun `applying preferences persists them, reroutes and reports before and after`() = runTest {
        val (_, actions) = fixture()
        guideAt(actions, 300.0)
        val r = actions.applyPreferences(RoutePreferences(avoidTolls = true)); runCurrent()
        val change = (r as Outcome.Success).value
        assertEquals(route.id, change.before!!.route.id)
        assertEquals(true, requests.last().preferences.avoidTolls)
        assertEquals(true, prefs.routePreferences.first().avoidTolls)
        assertTrue(change.after!!.route.durationSeconds > 0)
    }

    @Test fun `alternatives are computed from the snapped position with remaining stops`() = runTest {
        val (session, actions) = fixture()
        guideAt(actions, 300.0, stops = listOf(place("a", tracker.pointAt(900.0))))
        val r = actions.alternativesFromHere()
        assertTrue(r is Outcome.Success)
        assertEquals((session.state.value as NavigationState.Guiding).progress.snapped, requests.last().origin)
        assertEquals(listOf(tracker.pointAt(900.0)), requests.last().waypoints)
    }

    @Test fun `without guidance, route changes fail with NoResult`() = runTest {
        val (_, actions) = fixture()
        assertEquals(Outcome.Failure(AppError.NoResult), actions.recalculate())
        assertEquals(Outcome.Failure(AppError.NoResult), actions.addStop(place("x", GeoPoint(43.6, 1.44))))
    }
}
