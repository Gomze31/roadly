package fr.roadly.core.navigation

import fr.roadly.core.data.repository.MobilityPreferencesRepository
import fr.roadly.core.model.TravelMode
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.routing.FuelCostCalculator
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.routing.StopOrdering
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Arrêt de la course en cours ; [alongM] = abscisse sur le tracé courant. */
data class GuidanceStop(val place: Place, val alongM: Double) { val id: String get() = place.id }

/** Résultat d'un changement de préférences pendant un guidage ; les deux sont nuls hors guidage. */
data class PreferenceChange(val before: RouteOption?, val after: RouteOption?)

/** Détail de l'échec rendu quand un itinéraire automobile est demandé en mode trottinette ou vélo. */
const val LIGHT_VEHICLE_REFUSED = "light_vehicle_mode"

/**
 * Actions de guidage partagées par l'UI et l'assistant : démarrer vers un lieu, arrêts (≤ 10), alternatives,
 * préférences, recalcul. Garde la liste des arrêts de la course (la session ne connaît que le tracé).
 */
@Singleton
class GuidanceActions @Inject constructor(
    private val session: NavigationSession,
    private val alternatives: RouteAlternativesUseCase,
    private val routing: RoutingProvider,
    private val location: LocationRepository,
    private val prefs: UserPreferencesRepository,
    private val vehicles: VehicleProfileRepository,
    private val clock: NavigationClock,
    /** Mode de déplacement (null dans les tests existants : voiture). */
    private val mobility: MobilityPreferencesRepository? = null,
) {
    @Volatile private var stops: List<GuidanceStop> = emptyList()
    /** Tracé sur lequel les abscisses de [stops] ont été mesurées ; un recalcul automatique en change sans passer par [start]. */
    @Volatile private var stopsRouteId: String? = null

    init {
        session.rerouteWaypoints = { remainingStops().map { it.place.point } }
    }

    /** Derniers arrêts encore dus, tels que rendus par [remainingStops] : ceux qu'un recalcul automatique doit garder. */
    @Volatile private var carried: List<GuidanceStop> = emptyList()

    /** Nom de la destination de la course en cours, si connu. */
    var destinationName: String? = null
        private set

    private val guiding: NavigationState.Guiding? get() = session.state.value as? NavigationState.Guiding

    /** Arrêts encore devant soi, dans l'ordre du tracé. */
    fun remainingStops(): List<GuidanceStop> {
        val g = guiding ?: return emptyList()
        if (stopsRouteId != null && g.route.id != stopsRouteId) {
            // Recalcul automatique : les arrêts encore dus (ceux que la session vient de demander) sont replacés sur le nouveau tracé.
            val geo = RouteGeometry(g.route.geometry)
            stops = carried.map { GuidanceStop(it.place, geo.project(it.place.point)?.alongM ?: Double.MAX_VALUE) }
            stopsRouteId = g.route.id
        }
        return stops.filter { it.alongM > g.progress.distanceAlongM + PASSED_MARGIN_M }.sortedBy { it.alongM }.also { carried = it }
    }

    /** [scooter] non nul = guidage trottinette (voir [NavigationSession.start]). */
    fun start(
        option: RouteOption,
        preferences: RoutePreferences,
        vehicle: VehicleProfile?,
        stops: List<Place> = emptyList(),
        destinationName: String? = null,
        scooter: ScooterGuidanceConfig? = null,
    ) {
        val geo = RouteGeometry(option.route.geometry)
        this.stops = stops.take(StopOrdering.MAX_STOPS).map { GuidanceStop(it, geo.project(it.point)?.alongM ?: Double.MAX_VALUE) }
        stopsRouteId = option.route.id
        carried = this.stops
        if (destinationName != null) this.destinationName = destinationName
        session.start(option, preferences, vehicle, scooter, destinationName)
    }

    /** Réglages trottinette de la course en cours, batterie à jour de l'estimation ; null en voiture / moto. */
    private fun currentScooter(): ScooterGuidanceConfig? =
        session.scooterConfig?.let { it.copy(batteryPct = guiding?.scooter?.range?.batteryNowPct ?: it.batteryPct) }

    /** Itinéraire le plus rapide depuis la position (projetée si guidage, sinon dernier fix) vers [destination]. */
    suspend fun startTo(destination: Place): Outcome<RouteOption> {
        // Trottinette ou vélo : ce raccourci (voix, Android Auto) calcule un itinéraire automobile, voies rapides comprises.
        // Il est refusé ; l'itinéraire adapté se calcule depuis l'écran des itinéraires.
        if (lightVehicleMode()) return Outcome.Failure(AppError.Unknown(LIGHT_VEHICLE_REFUSED))
        val origin = guiding?.progress?.snapped ?: location.lastKnown?.point ?: return Outcome.Failure(AppError.PermissionDenied)
        val p = prefs.routePreferences.first()
        val v = vehicles.profile.first()
        return when (val r = alternatives(origin, destination.point, v, clock.nowMs(), p)) {
            is Outcome.Success -> {
                val best = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                start(best, p, v, emptyList(), destination.name)
                Outcome.Success(best)
            }
            is Outcome.Failure -> r
        }
    }

    suspend fun alternativesFromHere(): Outcome<List<RouteOption>> {
        val g = guiding ?: return Outcome.Failure(AppError.NoResult)
        return alternatives(g.progress.snapped, g.route.geometry.last(), vehicles.profile.first(), clock.nowMs(), prefs.routePreferences.first(), remainingStops().map { it.place.point })
    }

    /** Bascule sur [option] en gardant les arrêts restants. */
    suspend fun select(option: RouteOption) {
        val cfg = currentScooter()
        if (cfg != null) start(option, RoutePreferences(), null, remainingStops().map { it.place }, scooter = cfg)
        else start(option, prefs.routePreferences.first(), vehicles.profile.first(), remainingStops().map { it.place })
    }

    suspend fun recalculate(): Outcome<RouteOption> = reroute(remainingStops().map { it.place }, label = null)

    /** Insère [place] dans l'ordre du tracé courant parmi les arrêts restants, puis recalcule. */
    suspend fun addStop(place: Place): Outcome<RouteOption> {
        val g = guiding ?: return Outcome.Failure(AppError.NoResult)
        val current = remainingStops().map { it.place }
        if (current.size >= StopOrdering.MAX_STOPS) return Outcome.Failure(AppError.Unknown("max_stops"))
        val geo = RouteGeometry(g.route.geometry)
        val ordered = (current + place).sortedBy { geo.project(it.point)?.alongM ?: Double.MAX_VALUE }
        return reroute(ordered, "Via ${place.name}")
    }

    suspend fun removeStop(id: String): Outcome<RouteOption> {
        val current = remainingStops().map { it.place }
        if (current.none { it.id == id }) return Outcome.Failure(AppError.NoResult)
        return reroute(current.filterNot { it.id == id }, label = null)
    }

    /** Enregistre [value] ; en guidage, recalcule avec elles et renvoie l'avant / après. */
    suspend fun applyPreferences(value: RoutePreferences): Outcome<PreferenceChange> {
        prefs.setRoutePreferences(value)
        val g = guiding ?: return Outcome.Success(PreferenceChange(null, null))
        val before = RouteOption(g.route, "Itinéraire précédent", g.etaEpochMs, null, sameAsFastest = false)
        return when (val r = reroute(remainingStops().map { it.place }, label = null, preferences = value)) {
            is Outcome.Success -> Outcome.Success(PreferenceChange(before, r.value))
            is Outcome.Failure -> r
        }
    }

    /** Vrai si le mode de déplacement choisi est la trottinette ou le vélo (itinéraires automobiles interdits). */
    suspend fun lightVehicleMode(): Boolean =
        mobility?.travelMode?.first().let { it == TravelMode.ELECTRIC_SCOOTER || it == TravelMode.BICYCLE }

    fun stop() {
        stops = emptyList()
        carried = emptyList()
        stopsRouteId = null
        destinationName = null
        session.stop()
    }

    private suspend fun reroute(waypoints: List<Place>, label: String?, preferences: RoutePreferences? = null): Outcome<RouteOption> {
        val g = guiding ?: return Outcome.Failure(AppError.NoResult)
        // Trottinette : costing vélo et préférences trottinette, jamais les préférences ni le véhicule de la voiture.
        val cfg = currentScooter()
        val p = if (cfg != null) RoutePreferences() else preferences ?: prefs.routePreferences.first()
        val v = if (cfg != null) null else vehicles.profile.first()
        val request = RouteRequest(
            origin = g.progress.snapped, destination = g.route.geometry.last(), waypoints = waypoints.map { it.point },
            costing = g.route.costing, preferences = p, originHeadingDeg = g.progress.bearingDeg, vehicle = v, scooter = cfg?.request,
        )
        return when (val r = routing.route(request)) {
            is Outcome.Success -> {
                val route: Route = r.value.firstOrNull() ?: return Outcome.Failure(AppError.NoResult)
                val option = RouteOption(
                    route.copy(costing = g.route.costing), label ?: "Itinéraire recalculé",
                    clock.nowMs() + (route.durationSeconds * 1000).toLong(),
                    v?.let { FuelCostCalculator.estimate(route.distanceMeters, it) }, sameAsFastest = false,
                )
                start(option, p, v, waypoints, scooter = cfg)
                Outcome.Success(option)
            }
            is Outcome.Failure -> r
        }
    }

    private companion object {
        /** Un arrêt à moins de 30 m derrière la position projetée est considéré comme desservi. */
        const val PASSED_MARGIN_M = 30.0
    }
}
