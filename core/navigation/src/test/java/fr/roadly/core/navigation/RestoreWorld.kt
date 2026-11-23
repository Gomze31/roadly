package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeMobilityPreferencesRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf

/**
 * Monde de test de la reprise du guidage (NAV-1 / TST-1) : les vraies `NavigationSession`, `FreeDriveSession`, `GuidanceActions`
 * et `GuidanceRestorer`, branchées sur des doublures (position, réglages, calcul d'itinéraire, service). Sert aux tests JVM de la
 * reprise et aux tests Robolectric de `NavigationService`.
 */
internal class RestoreWorld(scope: CoroutineScope, dispatcher: CoroutineDispatcher, val clock: NavigationClock) {
    val location = FakeLocationRepository()
    val prefs = FakeUserPreferencesRepository()
    val mobility = FakeMobilityPreferencesRepository()
    val route: Route = TestRoutes.straight()
    val destination: GeoPoint = route.geometry.last()
    val requests = mutableListOf<RouteRequest>()
    /** Réponse du calcul d'itinéraire ; par défaut un tracé valable, d'identifiant différent à chaque demande. */
    var answer: (RouteRequest) -> Outcome<List<Route>> = { Outcome.Success(listOf(route.copy(id = "repris-${requests.size}"))) }
    val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return answer(request) }
    }
    val serviceController = FakeNavigationServiceController()
    val speech = FakeSpeechService()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher; override val default: CoroutineDispatcher = dispatcher; override val main: CoroutineDispatcher = dispatcher
    }
    private val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    private val vigilance = object : VigilanceRepository {
        override val count = flowOf(0)
        override val updatedAtMs = flowOf<Long?>(null)
        override suspend fun zonesIn(box: BoundingBox) = emptyList<fr.roadly.core.data.db.VigilanceZoneEntity>()
        override suspend fun refresh(): Outcome<Int> = Outcome.Failure(AppError.NoResult)
        override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? = null
    }
    val session = NavigationSession(
        location, routing, NoopSpeedLimitProvider(), speech, prefs, serviceController, clock, NoopVigilanceZoneProvider(), NoopRainProvider(),
        NoopTripRecorder(), dispatchers, scope,
    )
    val freeDrive = FreeDriveSession(location, NoRoadSnapshotProvider, vigilance, speech, prefs, serviceController, clock, NoopTripRecorder(), session, dispatchers, scope)
    private val alternatives = RouteAlternativesUseCase(routing)
    val actions = GuidanceActions(session, alternatives, routing, location, prefs, vehicles, clock, mobility)
    /** Budget en mémoire, avec la règle de production. */
    class MemoryBudget : RestoreBudget {
        var stamps: List<Long> = emptyList()
        override fun tryTake(nowMs: Long): Boolean = RestoreBudgetRule.take(stamps, nowMs)?.also { stamps = it } != null
    }
    val budget = MemoryBudget()
    val restorer = GuidanceRestorer(session, freeDrive, actions, alternatives, location, prefs, vehicles, clock, budget)

    /** Course interrompue par la mort du processus précédent, démarrée il y a [ageMs]. */
    fun remember(ageMs: Long = 10 * 60_000L, name: String? = "Maison") {
        prefs.last.value = LastGuidance(destination, name, "avant-arrêt", clock.nowMs() - ageMs)
    }

    fun fix(point: GeoPoint = route.geometry.first(), elapsedMs: Long? = null) = LocationFix(point, 90f, 10f, 5f, clock.nowMs(), elapsedMs)

    /** Démarre un guidage comme le ferait l'écran des itinéraires. */
    fun startGuidance(name: String = "Bureau") {
        session.start(RouteOption(route, "Le plus rapide", 0L, null, false), RoutePreferences(), destinationName = name)
    }

    val guiding: NavigationState.Guiding? get() = session.state.value as? NavigationState.Guiding
}
