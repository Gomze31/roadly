package fr.roadly.feature.car

import android.content.Intent
import android.os.Looper
import androidx.car.app.HandshakeInfo
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.car.app.versioning.CarAppApiLevels
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.data.repository.DefaultRadarSettingsRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.data.traffic.TrafficEvent
import fr.roadly.core.data.traffic.TrafficSource
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.data.zfe.ZfeRule
import fr.roadly.core.data.zfe.ZfeSource
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.CarScreenPresence
import fr.roadly.core.navigation.FreeDriveSession
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NoRoadSnapshotProvider
import fr.roadly.core.navigation.RainProvider
import fr.roadly.core.navigation.SpeedLimitProvider
import fr.roadly.core.navigation.TripRecorder
import fr.roadly.core.navigation.VigilanceZoneProvider
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Monde de test de la voiture : la vraie session de guidage et les vraies actions du téléphone, branchées sur des doubles
 * (position, itinéraire, voix, service), et un [TestCarContext] à la place de l'hôte Android Auto. À utiliser sous Robolectric.
 * Le guidage tourne sur un ordonnanceur de test, les écrans de la voiture sur la boucle principale : [idle] vide les deux.
 */
internal class CarTestWorld(
    permission: LocationPermissionState = LocationPermissionState.GRANTED,
    carApiLevel: Int = CarAppApiLevels.getLatest(),
) {
    val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val route: Route = TestRoutes.straight()
    val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    val location = FakeLocationRepository(permission)
    val service = FakeNavigationServiceController()
    val prefs = FakeUserPreferencesRepository()
    val home = MutableStateFlow<Place?>(null)
    val routeRequests = mutableListOf<RouteRequest>()
    val searches = mutableListOf<String>()
    private val speech = FakeSpeechService()
    private val clock = NavigationClock { scheduler.currentTime }

    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            routeRequests += request
            return Outcome.Success(listOf(route))
        }
    }
    private val speedLimits = object : SpeedLimitProvider {
        override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = Outcome.Success(null)
    }
    private val zones = object : VigilanceZoneProvider {
        override suspend fun zonesAlong(route: Route): List<RouteHazard> = emptyList()
    }
    private val rain = object : RainProvider {
        override suspend fun isRaining(at: GeoPoint): Outcome<Boolean?> = Outcome.Success(null)
    }
    private val recorder = object : TripRecorder {
        override fun begin(destinationName: String, startedAtMs: Long) = Unit
        override fun onFix(fix: LocationFix, nowMs: Long) = Unit
        override suspend fun end(arrived: Boolean, nowMs: Long) = Unit
    }
    private val vigilance = object : VigilanceRepository {
        override val count: Flow<Int> = flowOf(0)
        override val updatedAtMs: Flow<Long?> = flowOf(null)
        override suspend fun zonesIn(box: BoundingBox): List<VigilanceZoneEntity> = emptyList()
        override suspend fun refresh(): Outcome<Int> = Outcome.Success(0)
        override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? = null
    }
    private val favorites = object : FavoritesRepository {
        override fun observeAll(): Flow<List<Place>> = flowOf(emptyList())
        override fun observeHome(): Flow<Place?> = home
        override fun observeWork(): Flow<Place?> = flowOf(null)
        override suspend fun isFavorite(id: String) = false
        override suspend fun toggle(place: Place) = Unit
        override suspend fun setHome(place: Place) { home.value = place }
        override suspend fun setWork(place: Place) = Unit
        override suspend fun clear() = Unit
    }
    private val recents = object : RecentSearchRepository {
        override fun observe(): Flow<List<Place>> = flowOf(emptyList())
        override suspend fun record(place: Place) = Unit
        override suspend fun clear() = Unit
    }
    private val geocoder = object : GeocodingProvider {
        override val id = "fake"
        override suspend fun search(query: String, near: GeoPoint?, limit: Int): Outcome<List<Place>> {
            searches += query
            return Outcome.Success(emptyList())
        }
        override suspend fun reverse(point: GeoPoint): Outcome<Place?> = Outcome.Success(null)
    }
    private val vehicles = object : VehicleProfileRepository {
        override val profile: Flow<VehicleProfile?> = flowOf(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    private val config = object : ProviderConfigRepository {
        override val config: Flow<ProviderConfig> = flowOf(ProviderConfig("https://ban.test", "https://photon.test", "https://valhalla.test", "asset://light.json", "asset://dark.json"))
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }

    val navigation = NavigationSession(location, routing, speedLimits, speech, prefs, service, clock, zones, rain, recorder, dispatchers, scope)
    val guidance = GuidanceActions(navigation, RouteAlternativesUseCase(routing), routing, location, prefs, vehicles, clock)
    private val freeDrive = FreeDriveSession(location, NoRoadSnapshotProvider, vigilance, speech, prefs, service, clock, recorder, navigation, dispatchers, scope)

    val deps = CarDependencies(
        navigation = navigation, guidance = guidance, freeDrive = freeDrive, location = location, favorites = favorites, recents = recents,
        geocoder = geocoder, alternatives = RouteAlternativesUseCase(routing), prefs = prefs, vehicles = vehicles, config = config,
        clock = clock, dangerZones = zones, radarSettings = DefaultRadarSettingsRepository, carScreen = CarScreenPresence(),
        foregroundService = service,
        zfe = object : ZfeSource { override suspend fun rules(): List<ZfeRule> = emptyList() },
        traffic = object : TrafficSource { override suspend fun events(): List<TrafficEvent> = emptyList() },
    )

    /** Contexte de test, niveau d'API voiture négocié avec un hôte fictif (sans cela, `carAppApiLevel` lève une exception). */
    @Suppress("RestrictedApi")
    val carContext: TestCarContext = TestCarContext.createCarContext(RuntimeEnvironment.getApplication()).apply {
        updateHandshakeInfo(HandshakeInfo("fr.roadly.test.host", carApiLevel))
    }

    /**
     * Ouvre une session Android Auto sur ce monde et l'amène à [state].
     * `SessionController` (Car App 1.7.0) fait avancer le cycle de vie de la session, pas celui du contexte de test que suit le
     * gestionnaire d'écrans : sans [moveTo], les écrans poussés ne seraient jamais créés ni démarrés.
     */
    fun openSession(intent: Intent = Intent(Intent.ACTION_MAIN), state: Lifecycle.State = Lifecycle.State.STARTED): Pair<RoadlyCarSession, SessionController> {
        val session = RoadlyCarSession(deps)
        val controller = SessionController(session, carContext, intent)
        moveTo(controller, state)
        return session to controller
    }

    fun moveTo(controller: SessionController, state: Lifecycle.State) {
        carContext.lifecycleOwner.registry.currentState = state
        // La destruction du contexte de test détruit déjà la session (le contrôleur refuserait ensuite de la déplacer).
        if (state != Lifecycle.State.DESTROYED) controller.moveToState(state)
        idle()
    }

    /** Laisse tourner le guidage (ordonnanceur de test, sans avancer le temps) et les écrans (boucle principale) jusqu'au repos. */
    fun idle() {
        repeat(4) {
            scheduler.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** Démarre le guidage sur [route] comme le fait le téléphone (échoue sans autorisation de localisation). */
    fun startGuidance(destinationName: String? = "Rue d'Essai") {
        guidance.start(option, RoutePreferences(), null, emptyList(), destinationName)
        idle()
    }

    /** Position du véhicule à l'abscisse du point [index] du tracé. */
    fun fixAtPoint(index: Int) = LocationFix(route.geometry[index], 90f, 10f, 5f, scheduler.currentTime)

    fun emit(fix: LocationFix) {
        runBlocking { location.emit(fix) }
        idle()
    }

    fun close() = scope.cancel()
}
