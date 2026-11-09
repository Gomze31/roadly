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
    /** Variante la plus économe (≠ la plus rapide) : économie de carburant estimée (%) par rapport à la plus rapide ; null sinon. */
    val ecoSavingPct: Int? = null,
    /** Litres économisés par rapport à la plus rapide, si le profil véhicule donne la consommation. */
    val ecoSavedLitres: Double? = null,
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
        // Le plus économe : consommation estimée selon la vitesse des tronçons ; signalé s'il économise ≥ 5 % sur le plus rapide.
        val fuel = vehicle?.fuel
        val use = routes.associate { it.costing to FuelSpeedModel.equivalentMeters(it, fuel) }
        val eco = routes.filter { it.costing != RouteCosting.FASTEST }.minByOrNull { use.getValue(it.costing) }
        val ecoPct = if (fastest == null || eco == null) null else {
            val f = use.getValue(fastest.costing); val e = use.getValue(eco.costing)
            if (f <= 0.0 || e > f * (1 - ECO_MIN_SAVING)) null else ((f - e) / f * 100).toInt()
        }
        val ecoLitres = if (ecoPct == null || vehicle == null || vehicle.consumptionPer100 <= 0.0) null
            else (use.getValue(fastest!!.costing) - use.getValue(eco!!.costing)) / 100_000.0 * vehicle.consumptionPer100
        return Outcome.Success(routes.map { r ->
            val isEco = ecoPct != null && r.costing == eco?.costing
            RouteOption(
                route = r,
                label = r.costing.label(),
                arrivalEpochMs = departAtEpochMs + (r.durationSeconds * 1000).toLong(),
                cost = vehicle?.let { FuelCostCalculator.estimate(r, it) },
                sameAsFastest = fastest != null && r.costing != RouteCosting.FASTEST && r.geometry == fastest.geometry,
                ecoSavingPct = if (isEco) ecoPct else null,
                ecoSavedLitres = if (isEco) ecoLitres else null,
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

    private companion object { const val ECO_MIN_SAVING = 0.05 }

    private fun RouteCosting.label() = when (this) {
        RouteCosting.FASTEST -> "Le plus rapide"
        RouteCosting.SHORTEST -> "Le plus court"
        RouteCosting.NO_TOLL -> "Sans péage"
    }
}
