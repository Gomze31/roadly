package fr.roadly.feature.home

import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationMode
import fr.roadly.core.location.PermissionEvents
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.map.CameraMode
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationRepository()
    private val reverseCalls = mutableListOf<GeoPoint>()
    private val geocoder = object : GeocodingProvider {
        override val id = "fake"
        override suspend fun search(query: String, near: GeoPoint?, limit: Int) = Outcome.Success(emptyList<Place>())
        override suspend fun reverse(point: GeoPoint): Outcome<Place?> { reverseCalls += point; return Outcome.Success(Place("r", "Rue Test", "75001 Paris", point, PlaceKind.ADDRESS, "fake")) }
    }
    private val favorites = object : FavoritesRepository {
        override fun observeAll() = flowOf(emptyList<Place>())
        override fun observeHome() = flowOf<Place?>(null)
        override fun observeWork() = flowOf<Place?>(null)
        override suspend fun isFavorite(id: String) = false
        override suspend fun toggle(place: Place) = Unit
        override suspend fun setHome(place: Place) = Unit
        override suspend fun setWork(place: Place) = Unit
        override suspend fun clear() = Unit
    }
    private val connectivity = object : ConnectivityObserver { override val isOnline = MutableStateFlow(true) }
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun fix(lat: Double, lon: Double) = LocationFix(GeoPoint(lat, lon), 90f, 13.9f, 5f, 0L)
    /** Itinéraires renvoyés par le faux fournisseur (échec par défaut) et requêtes reçues. */
    private var routeResult: Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    private val requests = mutableListOf<RouteRequest>()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return routeResult }
    }
    private val prefs = FakeUserPreferencesRepository()
    private val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }

    /** Vraie session de guidage câblée sur des doubles (comme `NavigationViewModelTest`), mêmes préférences que le ViewModel. */
    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(
            location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs,
            FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
        )
    }

    private fun TestScope.vm(session: NavigationSession = session()) =
        HomeViewModel(location, favorites, geocoder, connectivity, config, PermissionEvents(), session, NoopCommunity, prefs, RouteAlternativesUseCase(routing), vehicles)

    @Test fun `collecting state switches location to MAP mode and exposes speed in km per h`() = runTest {
        val vm = vm()
        vm.uiState.test {
            location.emit(fix(48.85, 2.35)); advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(LocationMode.MAP, location.mode)
            assertEquals(50.0, s.speedKmh!!, 0.1)
            assertEquals("Rue Test, 75001 Paris", s.address)
        }
    }

    @Test fun `reverse geocode only after moving more than 50 m`() = runTest {
        val vm = vm()
        vm.uiState.test {
            location.emit(fix(48.8500, 2.3500)); advanceUntilIdle()
            location.emit(fix(48.8501, 2.3500)); advanceUntilIdle() // ~11 m
            location.emit(fix(48.8510, 2.3500)); advanceUntilIdle() // ~111 m
            expectMostRecentItem()
            assertEquals(2, reverseCalls.size)
        }
    }

    @Test fun `user gesture frees camera and recenter cycles CENTERED then FOLLOW`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.onUserGesture(); advanceUntilIdle()
            assertEquals(CameraMode.FREE, expectMostRecentItem().cameraMode)
            vm.onRecenter(); advanceUntilIdle()
            assertEquals(CameraMode.CENTERED, expectMostRecentItem().cameraMode)
            vm.onRecenter(); advanceUntilIdle()
            assertEquals(CameraMode.FOLLOW, expectMostRecentItem().cameraMode)
            assertEquals(LocationMode.FOLLOW, location.mode)
        }
    }

    @Test fun `isGuiding reflects the session state`() = runTest {
        val session = session()
        val vm = vm(session)
        val route = TestRoutes.straight()
        vm.isGuiding.test {
            assertEquals(false, awaitItem())
            session.start(RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
            assertEquals(true, awaitItem())
            session.stop(); runCurrent()
            assertEquals(false, awaitItem())
        }
    }

    @Test fun `an interrupted run is offered at home while idle, and hidden while guiding`() = runTest { // scénario 14 (E3)
        prefs.setLastGuidance(LastGuidance(GeoPoint(43.6045, 1.4440), "Capitole", "r-avant", 0L))
        val s = session()
        val vm = vm(s)
        vm.resumeCard.test {
            runCurrent()
            assertEquals(ResumeCardUi("Capitole"), expectMostRecentItem())
            s.start(RouteOption(TestRoutes.straight(), "Le plus rapide", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
            assertNull("un guidage en cours n'est pas une reprise", expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `ignore forgets the interrupted run`() = runTest {
        prefs.setLastGuidance(LastGuidance(GeoPoint(43.6045, 1.4440), null, "r-avant", 0L))
        val vm = vm()
        vm.resumeCard.test {
            runCurrent()
            assertEquals("sans nom : « Reprendre le dernier guidage »", ResumeCardUi(null), expectMostRecentItem())
            vm.dismissResume(); runCurrent()
            assertNull(expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(prefs.last.value)
    }

    @Test fun `resume routes from the current position to the saved destination and starts guiding with its name`() = runTest {
        routeResult = Outcome.Success(listOf(TestRoutes.straight()))
        val saved = LastGuidance(TestRoutes.straight().geometry.last(), "Capitole", "r-avant", 0L)
        prefs.setLastGuidance(saved)
        location.lastKnown = fix(48.85, 2.35)
        val s = session()
        val vm = vm(s)
        vm.resumeCard.test {
            runCurrent()
            vm.resumeLastGuidance(); runCurrent()
            assertNull("guidage repris : plus de carte", expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("nouvel itinéraire depuis la position actuelle, pas le tracé enregistré", requests.isNotEmpty() && requests.all { it.origin == GeoPoint(48.85, 2.35) && it.destination == saved.destination })
        assertEquals(TestRoutes.straight().id, (s.state.value as NavigationState.Guiding).route.id)
        assertEquals("Capitole", prefs.last.value!!.destinationName)
    }

    @Test fun `a resume without route keeps the card, marked failed, and the remembered run`() = runTest {
        val saved = LastGuidance(GeoPoint(43.6045, 1.4440), "Capitole", "r-avant", 0L)
        prefs.setLastGuidance(saved)
        location.lastKnown = fix(43.60, 1.44)
        val vm = vm()
        vm.resumeCard.test {
            runCurrent()
            vm.resumeLastGuidance(); runCurrent()
            assertEquals(ResumeCardUi("Capitole", ResumePhase.FAILED), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(saved, prefs.last.value)
    }
}

/** Service communautaire non configuré : aucun appel. */
private object NoopCommunity : fr.roadly.core.data.community.CommunityRepository {
    override val isConfigured = kotlinx.coroutines.flow.flowOf(false)
    override suspend fun report(kind: fr.roadly.core.model.ReportKind, at: GeoPoint, headingDeg: Float?) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun nearby(box: fr.roadly.core.model.BoundingBox) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun vote(reportId: String, confirm: Boolean) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun profile() = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun leaderboard(limit: Int) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
}
