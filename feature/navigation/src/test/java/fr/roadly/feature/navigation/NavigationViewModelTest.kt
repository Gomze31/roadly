package fr.roadly.feature.navigation

import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    private val main = StandardTestDispatcher()
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
    }

    private val fakeVehicle = object : fr.roadly.core.data.repository.VehicleProfileRepository {
        override val profile = flowOf<fr.roadly.core.model.VehicleProfile?>(null)
        override suspend fun save(profile: fr.roadly.core.model.VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }

    private fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)

    private fun NavigationViewModel(s: NavigationSession, p: FakeUserPreferencesRepository, c: ProviderConfigRepository) =
        NavigationViewModel(s, p, c, RouteAlternativesUseCase(routing), routing, fakeVehicle, NoopCommunity)

    @Test fun `alternatives are computed from the snapped position and switching restarts guidance on the chosen route`() = runTest {
        val okRouting = object : RoutingProvider {
            override val id = "ok"
            val origins = mutableListOf<fr.roadly.core.model.GeoPoint>()
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> { origins += request.origin; return Outcome.Success(listOf(route.copy(id = "alt-" + request.costing.name))) }
        }
        val s = session()
        val vm = NavigationViewModel(s, prefs, config, RouteAlternativesUseCase(okRouting), okRouting, fakeVehicle, NoopCommunity)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            vm.loadAlternatives(); runCurrent()
            val alt = vm.alternativesUi.value as AlternativesUi.Content
            assertEquals(3, alt.options.size)
            assertEquals(route.id, alt.currentRouteId)
            assertEquals((s.state.value as NavigationState.Guiding).progress.snapped, okRouting.origins.first())
            vm.switchTo(alt.options[1]); runCurrent()
            assertEquals("alt-SHORTEST", (s.state.value as NavigationState.Guiding).route.id)
            assertEquals(AlternativesUi.Hidden, vm.alternativesUi.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `guiding state is formatted for the banner, the upcoming strip and the ETA`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            val ui = vm.uiState.value
            val g = s.state.value as NavigationState.Guiding
            assertEquals(NavigationPhase.GUIDING, ui.phase)
            assertEquals(ManeuverType.TURN_RIGHT, ui.banner!!.type)
            assertEquals(Formatters.distance(g.progress.distanceToNextManeuverM), ui.banner!!.distanceText)
            assertEquals(route.maneuvers[1].instruction, ui.banner!!.instruction)
            assertEquals("Rue d'Essai", ui.banner!!.street)
            assertEquals(1, ui.upcoming.size)
            assertEquals(ManeuverType.ARRIVE, ui.upcoming[0].type)
            assertEquals(Formatters.distance(g.progress.distanceToNextManeuverM + route.maneuvers[1].lengthMeters), ui.upcoming[0].distanceText)
            assertEquals(Formatters.clockTime(g.etaEpochMs), ui.etaClock)
            assertEquals(Formatters.duration(g.progress.remainingDurationS), ui.remainingDuration)
            assertEquals(Formatters.distance(g.progress.remainingDistanceM), ui.remainingDistance)
            assertEquals(36.0, ui.speedKmh!!, 1e-9)
            assertNull("limitation inconnue sur l'instance publique", ui.speedLimitKmh)
            assertEquals(5, ui.toleranceKmh)
            assertEquals(ProviderConfig.DEFAULT.mapStyleDarkUrl, ui.styleUrl)
            assertTrue(ui.following)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `lanes and exit number of the next maneuver reach the screen`() = runTest {
        val withLanes = route.copy(maneuvers = route.maneuvers.mapIndexed { i, m ->
            if (i == 1) m.copy(lanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), false), Lane(setOf(LaneDirection.RIGHT), true)), exitNumber = "12") else m
        })
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option.copy(route = withLanes), RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            assertEquals(2, vm.uiState.value.lanes.size)
            assertTrue(vm.uiState.value.lanes[1].valid)
            assertEquals("12", vm.uiState.value.banner!!.exitNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `approach is flagged within 150 m of a turn and the exit direction reaches the banner`() = runTest {
        val withSign = route.copy(maneuvers = route.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(exitNumber = "12", exitToward = "Lyon · Mâcon") else m })
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option.copy(route = withSign), RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            assertNull(vm.uiState.value.approach)
            assertEquals("Lyon · Mâcon", vm.uiState.value.banner!!.exitToward)
            location.emit(fixAt(650.0)); runCurrent()
            val approach = vm.uiState.value.approach!!
            assertEquals(ManeuverType.TURN_RIGHT, approach.type)
            assertEquals("12", approach.exitNumber)
            assertEquals("Lyon · Mâcon", approach.exitToward)
            assertEquals(10, approach.maneuverShapeIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `mute toggles the session and stop ends in IDLE`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            vm.toggleMute(); runCurrent()
            assertTrue(s.muted.value)
            assertTrue(vm.uiState.value.muted)
            vm.toggleMute(); runCurrent()
            assertFalse(vm.uiState.value.muted)
            vm.stop(); runCurrent()
            assertEquals(NavigationPhase.IDLE, vm.uiState.value.phase)
            assertEquals(NavigationState.Idle, s.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a map gesture suspends follow for 8 s and recenter restores it at once`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            vm.onUserGesture(); runCurrent()
            assertFalse(vm.uiState.value.following)
            advanceTimeBy(NavigationViewModel.RECENTER_AFTER_MS - 1); runCurrent()
            assertFalse(vm.uiState.value.following)
            advanceTimeBy(1); runCurrent()
            assertTrue(vm.uiState.value.following)
            vm.onUserGesture(); runCurrent()
            vm.recenter(); runCurrent()
            assertTrue(vm.uiState.value.following)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `arrival is exposed`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            location.emit(fixAt(tracker.totalGeometryM)); runCurrent()
            assertEquals(NavigationPhase.ARRIVED, vm.uiState.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `failure is exposed and retry restarts guidance`() = runTest {
        location.permission.value = LocationPermissionState.DENIED
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            assertEquals(NavigationPhase.FAILED, vm.uiState.value.phase)
            assertEquals(AppError.PermissionDenied, vm.uiState.value.error)
            location.permission.value = LocationPermissionState.GRANTED
            vm.retry(); runCurrent()
            assertEquals(NavigationPhase.GUIDING, vm.uiState.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `tolerance preference is exposed for the badge`() = runTest {
        prefs.setSpeedAlertToleranceKmh(10)
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            assertEquals(10, vm.uiState.value.toleranceKmh)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private object NoopCommunity : fr.roadly.core.data.community.CommunityRepository {
    override val isConfigured = flowOf(false)
    override suspend fun report(kind: fr.roadly.core.model.ReportKind, at: fr.roadly.core.model.GeoPoint, headingDeg: Float?) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun nearby(box: fr.roadly.core.model.BoundingBox) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun vote(reportId: String, confirm: Boolean) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun profile() = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun leaderboard(limit: Int) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
}
