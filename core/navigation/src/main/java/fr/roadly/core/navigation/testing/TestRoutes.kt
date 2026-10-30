package fr.roadly.core.navigation.testing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import kotlin.math.PI

/** Itinéraires synthétiques déterministes pour les tests. */
object TestRoutes {
    const val LAT = 48.85
    const val METERS_PER_DEG_LAT = 6_371_000.0 * PI / 180.0

    /** 21 points plein est (pas de 0,001° ≈ 73,2 m, ≈ 1 464 m au total) : départ (point 0), virage à droite (point 10), arrivée (point 20). */
    fun straight(id: String = "straight", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..20).map { GeoPoint(LAT, 2.300 + it * 0.001) }
        val total = (0 until 20).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val half = (0 until 10).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, half, 60.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.TURN_RIGHT, "Tournez à droite dans Rue d'Essai.", "Rue d'Essai", total - half, 60.0, 10, null,
                verbalPre = "Tournez à droite dans Rue d'Essai.", verbalAlert = "Tournez à droite dans Rue d'Essai.",
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 20, null,
                verbalPre = "Votre destination est sur la droite.", verbalAlert = "Votre destination sera sur la droite.",
            ),
        )
        return Route(id, costing, total, 120.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /** [p] décalé de [meters] vers le nord (perpendiculaire à [straight]). */
    fun north(p: GeoPoint, meters: Double) = GeoPoint(p.lat + meters / METERS_PER_DEG_LAT, p.lon)
}
