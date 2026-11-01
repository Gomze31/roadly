package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeMobilityPreferencesRepository
import fr.roadly.core.data.testing.FakeScooterProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.Trip
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTemperatureProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.ScooterServices
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.NoScooterRouteAnalyzer
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.routing.ScooterPoiProvider
import fr.roadly.core.routing.ScooterRouteOptionsUseCase
import fr.roadly.core.scooter.osm.ScooterPoi
import fr.roadly.core.scooter.osm.ScooterPoiKind
import fr.roadly.core.scooter.range.RangeRisk
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScooterRouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationRepository().apply { lastKnown = LocationFix(GeoPoint(43.6, 1.44), null, null, 5f, 0L) }
    private val car = VehicleProfile(VehicleType.SEDAN, FuelType.SP95, 6.0, 1.8)
    private val vehicle = object : VehicleProfileRepository {
        val p = MutableStateFlow<VehicleProfile?>(car)
        override val profile = p
        override suspend fun save(profile: VehicleProfile) { p.value = profile }
        override suspend fun clear() { p.value = null }
    }
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    val requests = mutableListOf<RouteRequest>()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            val n = 2 + (request.scooter?.goal?.ordinal ?: 0)
            val pts = (0..n).map { GeoPoint(43.6, 1.44 + it * 0.001) }
            return Outcome.Success(listOf(Route("${request.costing}-${request.scooter?.goal}", request.costing, 1000.0 + n, 240.0, pts,
                listOf(Maneuver(ManeuverType.DEPART, "Départ", null, 0.0, 0.0, 0, null), Maneuver(ManeuverType.ARRIVE, "Arrivée", null, 0.0, 0.0, n, null)), false, "fake")))
        }
    }
    private val prefs = FakeUserPreferencesRepository()
    private val mobility = FakeMobilityPreferencesRepository()
    private val profiles = FakeScooterProfileRepository()
    private val speech = FakeSpeechService()
    private val trips = object : TripRepository {
        override val trips = MutableStateFlow<List<Trip>>(emptyList())
        override suspend fun byId(id: Long): Trip? = null
        override suspend fun save(trip: Trip): Long = 1
        override suspend fun delete(id: Long) = Unit
        override suspend fun clear() = Unit
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
    private val pois = object : ScooterPoiProvider {
        override suspend fun around(kind: ScooterPoiKind, center: GeoPoint, radiusM: Int): Outcome<List<ScooterPoi>> = Outcome.Success(emptyList())
    }
    private lateinit var session: NavigationSession

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(): RouteViewModel {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        val services = ScooterServices(mobility, profiles, ScooterRouteOptionsUseCase(routing, NoScooterRouteAnalyzer), trips, NoopTemperatureProvider(), pois, NoopTripRecorder(), speech, favorites)
        return RouteViewModel(SavedStateHandle(mapOf("destLat" to 43.61, "destLon" to 1.45, "destName" to "Capitole")), RouteAlternativesUseCase(routing), vehicle, location, config, prefs, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicle, NavigationClock { testScheduler.currentTime }), routing, services)
    }

    @Test fun `car stays the default and switching to scooter really changes the calculation`() = runTest {
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            val carState = expectMostRecentItem()
            assertEquals(TravelMode.CAR, carState.travelMode)
            assertTrue(requests.all { it.scooter == null && it.vehicle == car })
            requests.clear()
            vm.setTravelMode(TravelMode.ELECTRIC_SCOOTER); advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(TravelMode.ELECTRIC_SCOOTER, s.travelMode)
            val content = s.phase as RouteUiState.Content
            assertEquals("Segway Ninebot MAX G2", content.scooter!!.profileName)
            assertTrue(requests.isNotEmpty() && requests.all { it.scooter != null && it.vehicle == null })
            assertTrue(content.options.all { it.scooter != null && it.cost == null })
            assertEquals(RangeRisk.UNKNOWN, content.options.first().scooter!!.range.risk)
        }
    }

    @Test fun `scooter preferences never touch car preferences`() = runTest {
        mobility.modeState.value = TravelMode.ELECTRIC_SCOOTER
        prefs.setRoutePreferences(RoutePreferences(avoidTolls = true))
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            vm.setScooterPreferences(mobility.prefsState.value.copy(goal = ScooterRouteGoal.COMFORT, avoidSteepHills = true)); advanceUntilIdle()
            expectMostRecentItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(RoutePreferences(avoidTolls = true), prefs.routePreferencesValue())
        assertEquals(ScooterRouteGoal.COMFORT, requests.last().scooter!!.preferences.goal)
    }

    @Test fun `mode and battery survive a restart and reach the guidance`() = runTest {
        val first = vm()
        first.uiState.test { advanceUntilIdle(); first.setTravelMode(TravelMode.ELECTRIC_SCOOTER); first.setBattery(76); advanceUntilIdle(); cancelAndIgnoreRemainingEvents() }
        assertEquals(TravelMode.ELECTRIC_SCOOTER, mobility.modeState.value)
        val second = vm()
        second.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(76, content.scooter!!.batteryPct)
            assertNotNull(content.options.first().scooter!!.range.arrivalBatteryPct)
            assertTrue(second.startGuidance())
            advanceUntilIdle()
            val g = session.state.value as NavigationState.Guiding
            assertNotNull(g.scooter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `assistant applies a goal and speaks a grounded answer`() = runTest {
        mobility.modeState.value = TravelMode.ELECTRIC_SCOOTER
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            vm.onAssistantText("Trouve-moi le trajet le plus sécurisé"); advanceUntilIdle()
            assertEquals(ScooterRouteGoal.SAFEST, mobility.prefsState.value.goal)
            vm.onAssistantText("Est-ce que j'ai assez de batterie ?"); advanceUntilIdle()
            assertTrue(speech.spoken.last().contains("Je ne connais pas le niveau de batterie"))
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(mobility.batteryState.value)
    }

    private suspend fun FakeUserPreferencesRepository.routePreferencesValue(): RoutePreferences = routePreferences.first()
}
