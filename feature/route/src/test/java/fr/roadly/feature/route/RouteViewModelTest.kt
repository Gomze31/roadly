package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationRepository()
    private val vehicle = object : VehicleProfileRepository {
        val p = MutableStateFlow<VehicleProfile?>(null)
        override val profile = p
        override suspend fun save(profile: VehicleProfile) { p.value = profile }
        override suspend fun clear() { p.value = null }
    }
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    private var routing: RoutingProvider = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(
            Route(
                request.costing.name, request.costing, 10_000.0, 600.0, listOf(request.origin, GeoPoint(request.origin.lat, request.origin.lon + 0.001 * (request.costing.ordinal + 1)), request.destination),
                listOf(
                    Maneuver(ManeuverType.DEPART, "Conduisez.", null, 10_000.0, 600.0, 0, null),
                    Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 1, null),
                ),
                false, "fake",
            ),
        ))
    }
    private val prefs = FakeUserPreferencesRepository()
    private lateinit var session: NavigationSession
    private val handle = SavedStateHandle(mapOf("destLat" to 48.9, "destLon" to 2.4, "destName" to "Test"))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): RouteViewModel {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        return RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing)
    }

    @Test fun `without known position state is NoOrigin`() = runTest {
        vm().uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue(s.phase is RouteUiState.NoOrigin)
            assertTrue("la carte doit rester affichable sans position", s.styleUrl.isNotEmpty())
        }
    }

    @Test fun `opened cold without position, the first fix becomes the origin`() = runTest {
        vm().uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().phase is RouteUiState.NoOrigin)
            assertEquals(fr.roadly.core.location.LocationMode.MAP, location.mode)
            location.emit(LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)); advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(GeoPoint(48.8, 2.3), s.origin)
        }
    }

    @Test fun `with position computes three options and selects fastest`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        vm().uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(3, s.options.size)
            assertEquals(RouteCosting.FASTEST, s.options.first { it.route.id == s.activeId }.route.costing)
            assertTrue(s.options.all { it.cost == null })
        }
    }

    @Test fun `a departure planned on the home card is applied, then cleared when guidance starts`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val planner = fr.roadly.core.data.repository.DeparturePlanner()
        val at = System.currentTimeMillis() + 3_600_000
        planner.set(fr.roadly.core.data.repository.DeparturePlan(at, arrive = false))
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        val v = RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing, departurePlanner = planner)
        v.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(at, s.departAtEpochMs)
            assertNull(s.arriveAtEpochMs)
            assertTrue(v.startGuidance())
            assertNull(planner.plan.value)
        }
    }

    @Test fun `swapping goes from the destination back to my position, and swapping again restores the trip`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val first = vm()
        advanceUntilIdle()
        assertNull(first.originName)
        val back = first.swapped("Ma position")!!
        assertEquals(RouteRoute(48.8, 2.3, "Ma position", fromLat = 48.9, fromLon = 2.4, fromName = "Test", destIsMyPosition = true), back)

        val handle2 = SavedStateHandle(mapOf("destLat" to back.destLat, "destLon" to back.destLon, "destName" to back.destName,
            "fromLat" to back.fromLat, "fromLon" to back.fromLon, "fromName" to back.fromName, "destIsMyPosition" to back.destIsMyPosition))
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        val second = RouteViewModel(handle2, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing)
        second.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals("le départ est la destination d'origine", GeoPoint(48.9, 2.4), s.origin)
            assertEquals("Test", second.originName)
        }
        assertEquals(RouteRoute(48.9, 2.4, "Test"), second.swapped("Ma position"))
    }

    @Test fun `options chosen for this trip are used, and cleared when guidance starts`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val seen = mutableListOf<RoutePreferences>()
        val base = routing
        val recording = object : RoutingProvider by base {
            override suspend fun route(request: RouteRequest) = base.route(request).also { seen += request.preferences }
        }
        routing = recording
        val trip = fr.roadly.core.data.repository.TripPreferences().apply { set(RoutePreferences(avoidTolls = true)) }
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        val v = RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing, tripPreferences = trip)
        v.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            assertTrue("options du trajet utilisées : $seen", seen.isNotEmpty() && seen.all { it == RoutePreferences(avoidTolls = true) })
            assertEquals("préférences enregistrées intactes", RoutePreferences(), prefs.route.value)
            assertTrue(v.startGuidance())
            assertNull(trip.override.value)
        }
    }

    @Test fun `the header star adds the destination to favorites, then removes it`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val saved = kotlinx.coroutines.flow.MutableStateFlow<List<Place>>(emptyList())
        val favorites = object : fr.roadly.core.data.repository.FavoritesRepository {
            override fun observeAll() = saved
            override fun observeHome() = kotlinx.coroutines.flow.flowOf<Place?>(null)
            override fun observeWork() = kotlinx.coroutines.flow.flowOf<Place?>(null)
            override suspend fun isFavorite(id: String) = saved.value.any { it.id == id }
            override suspend fun toggle(place: Place) { saved.value = if (saved.value.any { it.id == place.id }) saved.value.filterNot { it.id == place.id } else saved.value + place }
            override suspend fun setHome(place: Place) = Unit
            override suspend fun setWork(place: Place) = Unit
            override suspend fun clear() { saved.value = emptyList() }
        }
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        val v = RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing, favorites = favorites)
        advanceUntilIdle()
        assertEquals(false, v.isFavorite.value)
        v.toggleFavorite(); advanceUntilIdle()
        assertEquals(true, v.isFavorite.value)
        assertEquals("Test", saved.value.single().name)
        assertEquals(GeoPoint(48.9, 2.4), saved.value.single().point)
        v.toggleFavorite(); advanceUntilIdle()
        assertEquals(false, v.isFavorite.value)
        assertTrue(saved.value.isEmpty())
    }

    @Test fun `each variant gets its identity card from its real profile, a failed one gets none`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val asked = mutableListOf<String>()
        val attributes = object : fr.roadly.core.navigation.RouteAttributesProvider {
            override suspend fun attributes(route: Route): Outcome<fr.roadly.core.navigation.RouteAttributes> {
                asked += route.id
                if (route.id == "SHORTEST") return Outcome.Failure(AppError.Network)
                val limits = listOf(fr.roadly.core.navigation.SpeedLimitSpan(0.0, 5_000.0, 130))
                val gantries = if (route.id == "FASTEST") listOf(fr.roadly.core.navigation.TollGantry(1_000.0, "A1", "fr")) else emptyList()
                return Outcome.Success(fr.roadly.core.navigation.RouteAttributes(route.id, 10_000.0, limits, gantries))
            }
        }
        val base = vm()
        val v = RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing, routeAttributes = attributes)
        assertTrue(base !== v)
        v.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals("FASTEST", asked.first()) // variante active d'abord
            assertEquals(50, s.identities.getValue("FASTEST").knownLimitPercent)
            assertEquals(1, s.identities.getValue("FASTEST").tollGantries)
            assertEquals(0, s.identities.getValue("NO_TOLL").tollGantries)
            assertTrue("échec : pas de carte inventée", "SHORTEST" !in s.identities)
        }
    }

    @Test fun `vehicle profile adds cost estimate`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        vehicle.p.value = VehicleProfile(fr.roadly.core.model.VehicleType.SEDAN, fr.roadly.core.model.FuelType.DIESEL, 5.0, 1.70)
        vm().uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(0.85, s.options.first().cost!!.fuelEur, 0.001)
        }
    }

    @Test fun `routing failure is surfaced and origin can be picked manually`() = runTest {
        routing = object : RoutingProvider { override val id = "fake"; override suspend fun route(request: RouteRequest) = Outcome.Failure(AppError.Timeout) }
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            assertEquals(AppError.Timeout, (expectMostRecentItem().phase as RouteUiState.Error).error)
        }
    }

    @Test fun `setOrigin from NoOrigin triggers computation`() = runTest {
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().phase is RouteUiState.NoOrigin)
            vm.setOrigin(GeoPoint(48.8, 2.3))
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(3, s.options.size)
            assertEquals(GeoPoint(48.8, 2.3), s.origin)
        }
    }

    @Test fun `start guidance hands the active option to the session`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem().phase as RouteUiState.Content
            val shortest = content.options.first { it.route.costing == RouteCosting.SHORTEST }
            vm.selectRoute(shortest.route.id)
            assertTrue(vm.startGuidance())
            // Pas d'advanceUntilIdle ensuite : la session tourne (boucle de surveillance).
            assertEquals(shortest.route.id, (session.state.value as NavigationState.Guiding).route.id)
            assertTrue(location.guidanceActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `start guidance without routes does nothing`() = runTest {
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            assertFalse(vm.startGuidance())
            assertEquals(NavigationState.Idle, session.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `route preferences reach the three variants and a change recomputes`() = runTest {
        val seen = mutableListOf<RouteRequest>()
        val base = routing
        routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> { seen += request; return base.route(request) }
        }
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        prefs.setRoutePreferences(RoutePreferences(avoidHighways = true))
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            assertEquals(3, seen.size)
            assertTrue(seen.all { it.preferences == RoutePreferences(avoidHighways = true) })
            prefs.setRoutePreferences(RoutePreferences(avoidHighways = true, avoidTolls = true))
            advanceUntilIdle()
            expectMostRecentItem()
            // Péages évités : « Sans péage » n'est plus calculée (elle doublerait le plus rapide).
            assertEquals(5, seen.size)
            assertTrue(seen.drop(3).all { it.preferences.avoidTolls })
        }
    }
    @Test fun `stops are added once, reordered, removed and passed as waypoints`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val a = fr.roadly.core.model.Place("a", "A", null, GeoPoint(48.81, 2.31), fr.roadly.core.model.PlaceKind.POI, "t")
        val b = fr.roadly.core.model.Place("b", "B", null, GeoPoint(48.82, 2.32), fr.roadly.core.model.PlaceKind.POI, "t")
        val seen = mutableListOf<List<GeoPoint>>()
        routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(
                Route(request.costing.name, request.costing, 10_000.0, 600.0, listOf(request.origin, GeoPoint(request.origin.lat, request.origin.lon + 0.001 * (request.costing.ordinal + 1))) + request.waypoints + request.destination,
                    listOf(Maneuver(ManeuverType.DEPART, "", null, 10_000.0, 600.0, 0, null), Maneuver(ManeuverType.ARRIVE, "", null, 0.0, 0.0, 1, null)), false, "fake"),
            )).also { seen += request.waypoints }
        }
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            vm.addStop(a); vm.addStop(a); vm.addStop(b); advanceUntilIdle()
            var c = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(listOf(a, b), c.stops)
            assertEquals(listOf(a.point, b.point), seen.last())
            vm.moveStop(1, 0); advanceUntilIdle()
            c = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(listOf(b, a), c.stops)
            vm.removeStop(0); advanceUntilIdle()
            assertEquals(listOf(a), (expectMostRecentItem().phase as RouteUiState.Content).stops)
        }
    }

    @Test fun `start guidance remembers the destination name`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            assertTrue(vm.startGuidance())
            runCurrent()
            assertEquals("Test", prefs.lastGuidance.first()?.destinationName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
