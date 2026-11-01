package fr.roadly.core.navigation

import fr.roadly.core.model.Route
import kotlinx.serialization.json.JsonObject

/** Itinéraire réel Paris (Hôtel de Ville → Étoile) issu de la fixture Valhalla FOSSGIS. */
internal object ParisRoute {
    private val fixture = ValhallaFixture("fixtures/valhalla_route.json", "paris")
    val rawManeuvers: List<JsonObject> get() = fixture.rawManeuvers
    fun route(): Route = fixture.route()
}
