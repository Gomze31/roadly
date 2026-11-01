package fr.roadly.feature.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RoundaboutResolver
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId

/** ViewModel de guidage sans réseau ni carte : session inactive, fausses implémentations du jeu principal (tests instrumentés). */
internal fun testNavigationViewModel(prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()): NavigationViewModel {
    val routing = object : RoutingProvider {
        override val id = "test"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }
    val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
    val session = NavigationSession(
        FakeLocationRepository(), routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(),
        NavigationClock { System.currentTimeMillis() }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
    val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    return NavigationViewModel(session, prefs, config, RouteAlternativesUseCase(routing), routing, vehicles, NoCommunity)
}

/** État d'écran de guidage à l'abscisse [along] de [route] (36 km/h, voies et rond-point comme les publierait la session). */
internal fun guidingUi(route: Route, along: Double): NavigationUiState {
    val t = RouteTracker(route)
    val p = t.update(LocationFix(t.pointAt(along), null, null, 5f, 0L))
    val ms = route.maneuvers
    val upcoming = ms.subList(minOf(p.nextManeuverIndex + 1, ms.size), minOf(p.nextManeuverIndex + 4, ms.size))
    val g = NavigationState.Guiding(
        route, p, 36.0, null, upcoming, ms[p.nextManeuverIndex].lanes, 0L, gpsLost = false, rerouting = false,
        roundabout = RoundaboutResolver.current(RoundaboutResolver.resolveAll(route), p.nextManeuverIndex),
    )
    return buildUiState(g, muted = false, following = true, toleranceKmh = 5, styleUrl = "", zone = ZoneId.of("Europe/Paris"))
}

private object NoCommunity : CommunityRepository {
    override val isConfigured = flowOf(false)
    override suspend fun report(kind: ReportKind, at: GeoPoint, headingDeg: Float?) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun nearby(box: BoundingBox) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun vote(reportId: String, confirm: Boolean) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun profile() = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun leaderboard(limit: Int) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
}
