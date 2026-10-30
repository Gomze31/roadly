package fr.roadly.core.routing

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest

/** Fournisseur d'itinéraires : calcule un ou plusieurs trajets pour une demande donnée. Ne lève jamais d'exception. */
interface RoutingProvider {
    val id: String
    suspend fun route(request: RouteRequest): Outcome<List<Route>>
}
