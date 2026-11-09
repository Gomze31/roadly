package fr.roadly.core.navigation

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import java.util.Locale

/**
 * Repères « au feu » (comme les directions par repère de Google Maps) : une manœuvre de changement de direction dont le point
 * coïncide avec un feu tricolore OSM du profil du tracé (nœud `traffic_signal`, souvent posé juste avant le carrefour) est dite
 * « Au feu, tournez à droite… ». Rien n'est deviné : sans profil ou sans feu, l'instruction reste celle de Valhalla.
 */
object TrafficLightCues {
    /** Feu posé jusqu'à cette distance avant le point de manœuvre (ligne d'arrêt, branche d'approche). */
    const val BEFORE_M = 40.0
    /** … ou juste après (nœud du carrefour légèrement décalé sur la géométrie). */
    const val AFTER_M = 8.0

    private val TURNS = setOf(
        ManeuverType.TURN_LEFT, ManeuverType.TURN_RIGHT, ManeuverType.SLIGHT_LEFT, ManeuverType.SLIGHT_RIGHT,
        ManeuverType.SHARP_LEFT, ManeuverType.SHARP_RIGHT, ManeuverType.UTURN_LEFT, ManeuverType.UTURN_RIGHT,
    )

    /** Indices des manœuvres du tracé qui ont lieu à un feu. */
    fun indexes(route: Route, signalsAlongM: List<Double>): Set<Int> {
        if (signalsAlongM.isEmpty() || route.geometry.size < 2) return emptySet()
        val geo = RouteGeometry(route.geometry)
        return route.maneuvers.withIndex().filter { (_, m) ->
            if (m.type !in TURNS) return@filter false
            val at = geo.cumulative.getOrNull(m.beginShapeIndex) ?: return@filter false
            signalsAlongM.any { it >= at - BEFORE_M && it <= at + AFTER_M }
        }.map { it.index }.toSet()
    }

    /** « Tournez à droite sur Rue X. » → « Au feu, tournez à droite sur Rue X. » */
    fun phrase(text: String): String = "Au feu, " + text.trim().replaceFirstChar { it.lowercase(Locale.FRANCE) }
}
