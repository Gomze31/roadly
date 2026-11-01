package fr.roadly.core.navigation.testing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.StreetSide
import kotlin.math.PI
import kotlin.math.cos

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

    /** Écart latéral (m) entre l'aller et le retour de [outAndBack]. */
    const val OUT_AND_BACK_LANE_M = 10.0

    /**
     * Aller 600 m plein est, demi-tour, retour 600 m plein ouest sur une chaussée décalée de ~10 m au nord (2x1 voies séparées).
     * Points 0..12 = aller (pas de 50 m), 13..25 = retour ; le segment 12 est le demi-tour (≈ 10 m).
     * Manœuvres : DEPART (point 0), UTURN_LEFT (point 12), ARRIVE (point 25).
     */
    fun outAndBack(id: String = "outAndBack", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val lonPerMeter = 1.0 / (METERS_PER_DEG_LAT * cos(LAT * PI / 180.0))
        val laneLat = LAT + OUT_AND_BACK_LANE_M / METERS_PER_DEG_LAT
        val outbound = (0..12).map { GeoPoint(LAT, 2.300 + it * 50.0 * lonPerMeter) }
        val inbound = (12 downTo 0).map { GeoPoint(laneLat, 2.300 + it * 50.0 * lonPerMeter) }
        val pts = outbound + inbound
        val total = (0 until pts.lastIndex).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val leg = (0 until 12).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, leg, 60.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.UTURN_LEFT, "Faites demi-tour.", null, total - leg, 60.0, 12, null,
                verbalPre = "Faites demi-tour.", verbalAlert = "Faites demi-tour.",
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 25, null,
                verbalPre = "Votre destination est sur la gauche.", verbalAlert = "Votre destination sera sur la gauche.",
            ),
        )
        return Route(id, costing, total, 120.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /** [p] décalé de [meters] vers le nord (perpendiculaire à [straight]). */
    fun north(p: GeoPoint, meters: Double) = GeoPoint(p.lat + meters / METERS_PER_DEG_LAT, p.lon)

    /** Point à [eastM] m à l'est et [northM] m au nord de (LAT, 2,300). */
    fun xy(eastM: Double, northM: Double): GeoPoint {
        val lonPerMeter = 1.0 / (METERS_PER_DEG_LAT * cos(LAT * PI / 180.0))
        return GeoPoint(LAT + northM / METERS_PER_DEG_LAT, 2.300 + eastM * lonPerMeter)
    }

    /**
     * Deux ronds-points rapprochés (scénario 6) : 600 m plein est (61 points, pas de 10 m) ; entrée 1 au point 20 (200 m), sortie au point 23,
     * entrée 2 au point 35 (350 m, 120 m après la sortie 1), sortie au point 38, arrivée au point 60.
     */
    fun twoRoundabouts(id: String = "twoRoundabouts", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..60).map { xy(it * 10.0, 0.0) }
        val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
        fun len(a: Int, b: Int) = cum[b] - cum[a]
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, len(0, 20), 24.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.ROUNDABOUT_ENTER, "Entrez dans le rond-point et prenez la 2e sortie.", "Rond-point Nord", len(20, 23), 4.0, 20, 2,
                verbalPre = "Entrez dans le rond-point et prenez la 2e sortie.", verbalAlert = "Entrez dans le rond-point et prenez la 2e sortie.",
            ),
            Maneuver(
                ManeuverType.ROUNDABOUT_EXIT, "Quittez le rond-point dans Rue Médiane.", "Rue Médiane", len(23, 35), 14.0, 23, null,
                verbalPre = "Quittez le rond-point dans Rue Médiane.",
            ),
            Maneuver(
                ManeuverType.ROUNDABOUT_ENTER, "Entrez dans le rond-point et prenez la 1re sortie.", "Rond-point Sud", len(35, 38), 4.0, 35, 1,
                verbalPre = "Entrez dans le rond-point et prenez la 1re sortie.", verbalAlert = "Entrez dans le rond-point et prenez la 1re sortie.",
            ),
            Maneuver(
                ManeuverType.ROUNDABOUT_EXIT, "Quittez le rond-point dans Rue Finale.", "Rue Finale", len(38, 60), 26.0, 38, null,
                verbalPre = "Quittez le rond-point dans Rue Finale.",
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 60, null,
                verbalPre = "Votre destination est sur la droite.", verbalAlert = "Votre destination sera sur la droite.",
            ),
        )
        return Route(id, costing, cum.last(), 72.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /** Virage à gauche (scénario 3) : 500 m plein est (pas de 50 m) puis 500 m plein nord ; caps 90° → 0° (angle −90°). */
    fun leftTurn(id: String = "leftTurn", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..10).map { xy(it * 50.0, 0.0) } + (1..10).map { xy(500.0, it * 50.0) }
        val total = (0 until pts.lastIndex).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val leg = (0 until 10).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, leg, 50.0, 0, null, verbalPre = "Conduisez vers l'est.", bearingAfterDeg = 90),
            Maneuver(
                ManeuverType.TURN_LEFT, "Tournez à gauche dans Rue du Nord.", "Rue du Nord", total - leg, 50.0, 10, null,
                verbalPre = "Tournez à gauche dans Rue du Nord.", verbalAlert = "Tournez à gauche dans Rue du Nord.",
                bearingBeforeDeg = 90, bearingAfterDeg = 0,
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 20, null,
                verbalPre = "Votre destination est sur la gauche.", bearingBeforeDeg = 0, sideOfStreet = StreetSide.LEFT,
            ),
        )
        return Route(id, costing, total, 100.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /** Écart (m) de la voie de desserte de [parallelRoads] (scénario 9). */
    const val PARALLEL_ROAD_OFFSET_M = 25.0

    /**
     * Route principale 1 km plein est (pas de 50 m), virage à droite à 600 m ; la voie de desserte, hors itinéraire, est parallèle
     * à [PARALLEL_ROAD_OFFSET_M] au nord (positions simulées par `FixScript.drive(lateralM = …)`).
     */
    fun parallelRoads(id: String = "parallelRoads", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..20).map { xy(it * 50.0, 0.0) }
        val total = (0 until 20).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val first = (0 until 12).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, first, 43.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.TURN_RIGHT, "Tournez à droite dans Rue de la Desserte.", "Rue de la Desserte", total - first, 29.0, 12, null,
                verbalPre = "Tournez à droite dans Rue de la Desserte.", verbalAlert = "Tournez à droite dans Rue de la Desserte.",
            ),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 20, null, verbalPre = "Votre destination est sur la droite."),
        )
        return Route(id, costing, total, 72.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /**
     * Passage supérieur (B6, « pont au-dessus ») : 300 m plein est, 50 m nord, 175 m ouest, puis 100 m sud en recoupant le premier
     * tronçon à 125 m du départ (abscisse ≈ 563 m au croisement). Manœuvres : départ, 3 virages à gauche, arrivée.
     */
    fun overpass(id: String = "overpass", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = listOf(0.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0).map { xy(it, 0.0) } +
            listOf(25.0, 50.0).map { xy(300.0, it) } +
            listOf(250.0, 200.0, 150.0, 125.0).map { xy(it, 50.0) } +
            listOf(25.0, 0.0, -25.0, -50.0).map { xy(125.0, it) }
        val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
        fun len(a: Int, b: Int) = cum[b] - cum[a]
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, len(0, 6), 30.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", null, len(6, 8), 8.0, 6, null, verbalPre = "Tournez à gauche."),
            Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", null, len(8, 12), 18.0, 8, null, verbalPre = "Tournez à gauche."),
            Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche sur le pont.", null, len(12, 16), 12.0, 12, null, verbalPre = "Tournez à gauche sur le pont."),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 16, null, verbalPre = "Votre destination est devant vous."),
        )
        return Route(id, costing, cum.last(), 68.0, pts, maneuvers, hasToll = false, providerId = "test")
    }
}
