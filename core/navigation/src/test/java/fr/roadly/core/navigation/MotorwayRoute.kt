package fr.roadly.core.navigation

import fr.roadly.core.model.Route
import kotlinx.serialization.json.JsonObject

/** Itinéraire réel Toulouse (périphérique, A620, sorties 31b et 19) issu de la fixture Valhalla FOSSGIS du 2026-10-31. */
internal object MotorwayRoute {
    private val fixture = ValhallaFixture("fixtures/valhalla_motorway.json", "motorway")
    val rawManeuvers: List<JsonObject> get() = fixture.rawManeuvers
    fun route(): Route = fixture.route()
}
