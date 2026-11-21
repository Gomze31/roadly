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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
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
 * Calcule les trois variantes (rapide, court, sans péage) et signale celles qui reprennent le tracé du plus rapide.
 *
 * Les trois demandes sont déposées dans l'ordre (rapide d'abord) ; c'est le limiteur de débit d'`ApiClient` qui décide du
 * rythme réel : sur l'instance publique FOSSGIS elles partent une par une, espacées d'une seconde (plus de rafale de trois
 * requêtes simultanées, le résultat complet arrive donc environ 2 s plus tard) ; sur un serveur auto-hébergé elles restent
 * simultanées. [progressive] livre chaque variante dès qu'elle est prête pour ne pas faire attendre l'écran.
 */
class RouteAlternativesUseCase @Inject constructor(private val routing: RoutingProvider) {
    private val order = listOf(RouteCosting.FASTEST, RouteCosting.SHORTEST, RouteCosting.NO_TOLL)

    /** Résultat complet : les variantes obtenues, ou la première erreur si aucune n'a abouti. */
    suspend operator fun invoke(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicle: VehicleProfile?,
        departAtEpochMs: Long,
        preferences: RoutePreferences = RoutePreferences(),
        waypoints: List<GeoPoint> = emptyList(),
    ): Outcome<List<RouteOption>> = progressive(origin, destination, vehicle, departAtEpochMs, preferences, waypoints).last()

    /**
     * Même calcul, au fil de l'eau : une émission `Success` (liste partielle, dans l'ordre rapide / court / sans péage) à
     * chaque variante obtenue, puis une dernière émission identique au résultat d'[invoke] (liste complète ou erreur).
     */
    fun progressive(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicle: VehicleProfile?,
        departAtEpochMs: Long,
        preferences: RoutePreferences = RoutePreferences(),
        waypoints: List<GeoPoint> = emptyList(),
    ): Flow<Outcome<List<RouteOption>>> = flow {
        coroutineScope {
            val arrivals = Channel<Pair<Int, Outcome<List<Route>>>>(order.size)
            order.forEachIndexed { i, c ->
                // UNDISPATCHED : chaque demande atteint la file du limiteur avant le lancement de la suivante (ordre garanti).
                launch(start = CoroutineStart.UNDISPATCHED) {
                    arrivals.send(i to safely { routing.route(RouteRequest(origin, destination, waypoints = waypoints, costing = c, vehicle = vehicle, departAtEpochMs = departAtEpochMs, preferences = preferences)) })
                }
            }
            val results = arrayOfNulls<Outcome<List<Route>>>(order.size)
            repeat(order.size) { n ->
                val (i, r) = arrivals.receive()
                results[i] = r
                val options = options(results, vehicle, departAtEpochMs)
                val last = n == order.lastIndex
                if (options.isNotEmpty()) {
                    if (last || r is Outcome.Success) emit(Outcome.Success(options))
                } else if (last) {
                    emit(Outcome.Failure(results.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error } ?: AppError.NoResult))
                }
            }
        }
    }

    private fun options(results: Array<Outcome<List<Route>>?>, vehicle: VehicleProfile?, departAtEpochMs: Long): List<RouteOption> {
        val routes = results.mapIndexedNotNull { i, r -> (r as? Outcome.Success)?.value?.firstOrNull()?.copy(costing = order[i]) }
        val fastest = routes.firstOrNull { it.costing == RouteCosting.FASTEST }
        return routes.map { r ->
            RouteOption(
                route = r,
                label = r.costing.label(),
                arrivalEpochMs = departAtEpochMs + (r.durationSeconds * 1000).toLong(),
                cost = vehicle?.let { FuelCostCalculator.estimate(r.distanceMeters, it) },
                sameAsFastest = fastest != null && r.costing != RouteCosting.FASTEST && r.geometry == fastest.geometry,
            )
        }
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
