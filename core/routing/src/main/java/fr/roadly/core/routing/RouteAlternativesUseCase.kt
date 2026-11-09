package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.model.CostEstimate
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/** Un itinéraire proposé à l'utilisateur, avec son libellé, son heure d'arrivée estimée et son coût (si estimable). */
data class RouteOption(
    val route: Route,
    val label: String,
    val arrivalEpochMs: Long,
    val cost: CostEstimate?,
    val sameAsFastest: Boolean,
    /** Mode trottinette : analyse des voies, autonomie et classement ; null pour un itinéraire motorisé. */
    val scooter: ScooterOptionInfo? = null,
)

/**
 * Calcule en parallèle les variantes (rapide, court, sans péage) et ne garde que des tracés distincts : une variante qui reprend
 * le tracé d'une précédente est retirée. « Sans péage » n'est pas calculée quand les péages sont déjà évités (elle doublerait
 * « Le plus rapide »).
 */
class RouteAlternativesUseCase @Inject constructor(private val routing: RoutingProvider) {
    private fun order(preferences: RoutePreferences) =
        if (preferences.avoidTolls) listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST)
        else listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST, RouteCosting.NO_TOLL)

    suspend operator fun invoke(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicle: VehicleProfile?,
        departAtEpochMs: Long,
        preferences: RoutePreferences = RoutePreferences(),
        waypoints: List<GeoPoint> = emptyList(),
    ): Outcome<List<RouteOption>> {
        val results = coroutineScope {
            order(preferences).map { c ->
                async { c to safely { routing.route(RouteRequest(origin, destination, waypoints = waypoints, costing = c, vehicle = vehicle, departAtEpochMs = departAtEpochMs, preferences = preferences)) } }
            }.map { it.await() }
        }
        val routes = results.mapNotNull { (c, r) -> (r as? Outcome.Success)?.value?.firstOrNull()?.copy(costing = c) }
            .distinctBy { it.geometry }
        if (routes.isEmpty()) {
            val firstError = results.firstNotNullOfOrNull { (_, r) -> (r as? Outcome.Failure)?.error } ?: AppError.NoResult
            return Outcome.Failure(firstError)
        }
        val fastest = routes.firstOrNull { it.costing == RouteCosting.FASTEST }
        return Outcome.Success(routes.map { r ->
            RouteOption(
                route = r,
                label = r.costing.label(),
                arrivalEpochMs = departAtEpochMs + (r.durationSeconds * 1000).toLong(),
                cost = vehicle?.let { FuelCostCalculator.estimate(r.distanceMeters, it) },
                sameAsFastest = fastest != null && r.costing != RouteCosting.FASTEST && r.geometry == fastest.geometry,
            )
        })
    }

    /** Une exception d'un fournisseur devient un échec : elle ne doit pas annuler les autres variantes. */
    private suspend inline fun safely(block: () -> Outcome<List<Route>>): Outcome<List<Route>> =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failure(AppError.Unknown(e.message))
        }

    private fun RouteCosting.label() = when (this) {
        RouteCosting.FASTEST -> "Le plus rapide"
        RouteCosting.SHORTEST -> "Le plus court"
        RouteCosting.NO_TOLL -> "Sans péage"
    }
}
