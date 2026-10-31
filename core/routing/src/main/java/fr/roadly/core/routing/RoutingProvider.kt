package fr.roadly.core.routing

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest

/** Fournisseur d'itinéraires : calcule un ou plusieurs trajets pour une demande donnée. Ne lève jamais d'exception. */
interface RoutingProvider {
    val id: String
    suspend fun route(request: RouteRequest): Outcome<List<Route>>

    /**
     * Ordre optimal des [stops] intermédiaires entre [origin] et [destination] (mode tournée) : indices dans [stops].
     * Par défaut, plus proche voisin local ; un fournisseur peut calculer l'ordre côté serveur.
     */
    suspend fun optimizeOrder(origin: GeoPoint, stops: List<GeoPoint>, destination: GeoPoint, preferences: RoutePreferences = RoutePreferences()): Outcome<List<Int>> =
        Outcome.Success(StopOrdering.nearestNeighbour(origin, stops))
}
