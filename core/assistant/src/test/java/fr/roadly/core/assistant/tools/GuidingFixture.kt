package fr.roadly.core.assistant.tools

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.CommunityProfile
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LeaderboardEntry
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.time.ZoneId

/** Session de guidage réelle sur la route de test, avec des doubles pour tout le reste. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GuidingFixture(private val scope: TestScope) {
    val zone: ZoneId = ZoneId.of("Europe/Paris")
    val location = FakeLocationRepository()
    val prefs = FakeUserPreferencesRepository()
    val route = TestRoutes.straight()
    val tracker = RouteTracker(route)
    val requests = mutableListOf<RouteRequest>()
    /** Durée renvoyée pour chaque requête (par défaut : celle de la route de test). */
    var durationFor: (RouteRequest) -> Double = { route.durationSeconds }
    val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            return Outcome.Success(listOf(route.copy(id = "r${requests.size}-${request.costing.name}", durationSeconds = durationFor(request))))
        }
    }
    var vehicle: VehicleProfile? = null
    val vehicles = object : VehicleProfileRepository {
        override val profile: Flow<VehicleProfile?> get() = flowOf(vehicle)
        override suspend fun save(profile: VehicleProfile) { vehicle = profile }
        override suspend fun clear() { vehicle = null }
    }
    val clock = NavigationClock { scope.testScheduler.currentTime }
    val navigator = AppNavigator()
    val places = ToolPlaces()
    val session: NavigationSession
    val guidance: GuidanceActions

    init {
        val d = StandardTestDispatcher(scope.testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), clock, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, scope.backgroundScope)
        guidance = GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicles, clock)
    }

    fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)

    suspend fun guideAt(along: Double) {
        guidance.start(RouteOption(route, "Le plus rapide", 0L, null, false), RoutePreferences(), vehicle, destinationName = "Capitole")
        scope.runCurrent()
        location.emit(fixAt(along))
        scope.runCurrent()
    }

    suspend fun standAt(along: Double) = location.emit(fixAt(along))

    fun ctx(speech: String = "", consent: Boolean = true) = ToolContext(consent, scope.testScheduler.currentTime, zone) { speech }

    fun place(id: String, p: GeoPoint, name: String = id) = Place(id, name, "$name, Toulouse", p, PlaceKind.POI, "test")
}

internal class FakeGeocoder(private val results: Map<String, List<Place>>) : GeocodingProvider {
    override val id = "fake"
    override suspend fun search(query: String, near: GeoPoint?, limit: Int) = Outcome.Success(results[query].orEmpty())
    override suspend fun reverse(point: GeoPoint) = Outcome.Success<Place?>(null)
}

internal class FakeFavorites(var home: Place? = null, var work: Place? = null, var all: List<Place> = emptyList()) : FavoritesRepository {
    override fun observeAll() = flowOf(all)
    override fun observeHome() = flowOf(home)
    override fun observeWork() = flowOf(work)
    override suspend fun isFavorite(id: String) = all.any { it.id == id }
    override suspend fun toggle(place: Place) = Unit
    override suspend fun setHome(place: Place) { home = place }
    override suspend fun setWork(place: Place) { work = place }
    override suspend fun clear() = Unit
}

internal class FakeRecents(var places: List<Place> = emptyList()) : RecentSearchRepository {
    override fun observe() = flowOf(places)
    override suspend fun record(place: Place) = Unit
    override suspend fun clear() = Unit
}

internal class FakeCommunity(configured: Boolean = false, var reports: List<CommunityReport> = emptyList()) : CommunityRepository {
    override val isConfigured = MutableStateFlow(configured)
    val sent = mutableListOf<ReportKind>()
    val votes = mutableListOf<String>()
    override suspend fun report(kind: ReportKind, at: GeoPoint, headingDeg: Float?): Outcome<CommunityReport> {
        sent += kind
        return reports.firstOrNull()?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NoResult)
    }
    override suspend fun nearby(box: BoundingBox) = Outcome.Success(reports)
    override suspend fun vote(reportId: String, confirm: Boolean): Outcome<CommunityReport> {
        votes += reportId
        return reports.firstOrNull { it.id == reportId }?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NoResult)
    }
    override suspend fun profile(): Outcome<CommunityProfile> = Outcome.Failure(AppError.NoResult)
    override suspend fun leaderboard(limit: Int): Outcome<List<LeaderboardEntry>> = Outcome.Success(emptyList())
}
