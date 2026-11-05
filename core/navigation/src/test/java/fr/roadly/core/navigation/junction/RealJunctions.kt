package fr.roadly.core.navigation.junction

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.navigation.ValhallaFixture
import kotlinx.serialization.json.Json

/**
 * Données réelles enregistrées le 2026-11-05 sur valhalla1.openstreetmap.de (`/route` puis `trace_attributes` `edge_walk` sur la
 * forme complète, mêmes attributs que [ValhallaJunctionTopology.ATTRIBUTES]) :
 * - `motorway` : Toulouse, périphérique, sorties 31b et 19, A620, 4 ronds-points (2e, 2e, 1re, 3e sortie) ;
 * - `muret` : 2 ronds-points (2e, 3e sortie) ;
 * - `nation` : place de la Nation, Paris, 6e sortie ;
 * - `etoile` : place Charles-de-Gaulle, Paris, 6e sortie.
 */
internal object RealJunctions {
    fun route(name: String): Route =
        if (name == "motorway") ValhallaFixture("fixtures/valhalla_motorway.json", "motorway").route()
        else ValhallaFixture("fixtures/junction_${name}_route.json", name).route()

    fun topology(name: String, route: Route, maneuverIndex: Int): JunctionTopology {
        val text = RealJunctions::class.java.classLoader!!.getResource("fixtures/junction_${name}_trace.json")!!.readText()
        val edges = ValhallaJunctionTopology.parse(Json { ignoreUnknownKeys = true }, text, indexOffset = 0)!!
        return JunctionTopology(route.id, maneuverIndex, edges)
    }

    fun enterIndices(route: Route): List<Int> = route.maneuvers.indices.filter { route.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER }

    /** Position projetée à l'abscisse [along] : prochaine manœuvre = première dont le début est devant (comme `RouteTracker`). */
    fun progressAt(route: Route, along: Double, accuracyM: Float = 5f, extrapolated: Boolean = false, offRouteM: Double = 0.0): RouteProgress {
        val g = RouteGeometry(route.geometry)
        val alongOf = { i: Int -> g.cumulative[route.maneuvers[i].beginShapeIndex.coerceIn(0, g.cumulative.lastIndex)] }
        val next = route.maneuvers.indices.firstOrNull { it > 0 && alongOf(it) > along } ?: route.maneuvers.lastIndex
        val seg = (0 until g.cumulative.lastIndex).lastOrNull { g.cumulative[it] <= along } ?: 0
        return RouteProgress(
            routeId = route.id, segmentIndex = seg, distanceAlongM = along, offRouteM = offRouteM, nextManeuverIndex = next,
            distanceToNextManeuverM = (alongOf(next) - along).coerceAtLeast(0.0), remainingDistanceM = g.totalM - along,
            remainingDurationS = 0.0, snapped = route.geometry[seg], bearingDeg = null, extrapolated = extrapolated, accuracyM = accuracyM,
        )
    }

    fun alongOf(route: Route, index: Int): Double {
        val g = RouteGeometry(route.geometry)
        return g.cumulative[route.maneuvers[index].beginShapeIndex.coerceIn(0, g.cumulative.lastIndex)]
    }
}
