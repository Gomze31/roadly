package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sauts vers l'avant et arrivée prématurée (phase 5). Défauts reproduits avant correction :
 * - arrêté en face de sa destination, de l'autre côté d'un terre-plein, 18 m de dérive GPS accrochaient la chaussée de retour
 *   420 m plus loin sur le tracé : demi-tour réputé fait, arrivée déclarée, guidage terminé ;
 * - un seul fix à 120 m de précision posé près de la destination déclarait l'arrivée 290 m trop tôt ;
 * - à la sortie d'un long tunnel, le premier fix (loin du dernier point connu) marquait le véhicule hors itinéraire.
 */
class RouteTrackerPlausibilityTest {
    /**
     * Aller 600 m plein est, demi-tour, retour sur une chaussée à [gapM] au nord ; la destination est sur le retour, à la hauteur
     * du point 400 m de l'aller. Manœuvres : départ, demi-tour (point 12), arrivée.
     */
    private fun dividedRoad(gapM: Double): Route {
        val outbound = (0..12).map { TestRoutes.xy(it * 50.0, 0.0) }
        val inbound = listOf(600.0, 550.0, 500.0, 450.0, 400.0).map { TestRoutes.xy(it, gapM) }
        val pts = outbound + inbound
        val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, cum[12], 60.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(ManeuverType.UTURN_LEFT, "Faites demi-tour.", null, cum.last() - cum[12], 30.0, 12, null, verbalPre = "Faites demi-tour."),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, pts.lastIndex, null, verbalPre = "Votre destination est sur la droite."),
        )
        return Route("divided", RouteCosting.FASTEST, cum.last(), 90.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    @Test fun `stopped opposite the destination across the median - GPS drift does not jump to the return carriageway nor declare arrival`() {
        for (gap in listOf(20.0, 30.0)) {
            val route = dividedRoad(gap)
            val t = RouteTracker(route)
            FixScript(route).drive(0.0, 400.0, speedKmh = 40.0).forEach { t.update(it) }
            // Feu rouge : 9 s à l'arrêt, la position dérive jusqu'à 28 m vers la chaussée d'en face (au-delà d'elle pour 20 m), puis revient.
            listOf(0.0, 10.0, 18.0, 24.0, 28.0, 28.0, 18.0, 10.0, 0.0).forEachIndexed { i, drift ->
                val p = t.update(LocationFix(TestRoutes.xy(400.0, drift), null, 0f, 12f, 40_000L + i * 1_000L))
                assertEquals("terre-plein $gap m, dérive $drift m : position gardée sur l'aller", 400.0, p.distanceAlongM, 5.0)
                assertEquals("le demi-tour reste à faire", 1, p.nextManeuverIndex)
                assertFalse("pas d'arrivée avant le demi-tour", t.hasArrived)
            }
            // Le trajet se termine ensuite normalement : demi-tour, retour, arrivée à la fin seulement.
            val rest = FixScript(route).drive(400.0, t.totalGeometryM, speedKmh = 30.0, startMs = 50_000L)
            rest.dropLast(4).forEach { t.update(it); assertFalse(t.hasArrived) }
            rest.takeLast(4).forEach { t.update(it) }
            assertTrue(t.hasArrived)
        }
    }

    @Test fun `stop-and-go traffic still advances - the forward bound never holds back a crawling vehicle`() {
        val route = dividedRoad(20.0)
        val t = RouteTracker(route)
        // 5 km/h (1,4 m/s, sous le seuil du filtre de cap) avec 4 m de bruit, sur tout l'aller et le demi-tour.
        val fixes = FixScript(route).drive(0.0, 640.0, speedKmh = 5.0, noiseM = 4.0)
        fixes.forEachIndexed { i, fix ->
            val p = t.update(fix)
            assertEquals("fix $i : position suivie", i * 5.0 / 3.6, p.distanceAlongM, 20.0)
        }
        assertEquals("demi-tour passé au pas", 2, t.lastProgress!!.nextManeuverIndex)
    }

    @Test fun `a very imprecise fix near the destination is not an arrival - the next usable fix decides`() {
        val route = TestRoutes.straight()
        val t = RouteTracker(route)
        val end = t.totalGeometryM
        val script = FixScript(route)
        script.drive(0.0, end - 300.0, speedKmh = 50.0).forEach { t.update(it) }
        assertEquals("dernière manœuvre visée", route.maneuvers.lastIndex, t.lastProgress!!.nextManeuverIndex)
        // Fix réseau (120 m de précision) posé à 10 m de la destination alors que le véhicule en est à 300 m.
        t.update(script.drive(end - 10.0, end - 10.0, speedKmh = 50.0, accuracyM = 120f).single())
        assertFalse("un fix à 120 m de précision ne prouve pas l'arrivée", t.hasArrived)
        t.update(script.drive(end - 280.0, end - 280.0, speedKmh = 50.0).single())
        assertFalse(t.hasArrived)
        // À destination avec un fix exploitable (25 m de précision, ville dense) : arrivée.
        t.update(script.drive(end - 10.0, end - 10.0, speedKmh = 20.0, accuracyM = 25f).single())
        assertTrue(t.hasArrived)
    }

    @Test fun `long tunnel - the first fix 1,8 km further is not off route and the second confirms the position`() {
        val route = MotorwayRoute.route()
        val t = RouteTracker(route)
        val script = FixScript(route)
        script.drive(2_000.0, 2_300.0, speedKmh = 110.0).forEach { t.update(it) }
        // 60 s sans GPS à 110 km/h : au-delà de la fenêtre de recherche (500 m) et du plafond d'extrapolation (150 m).
        val out = script.drive(4_133.0, 4_255.0, speedKmh = 110.0, startMs = 70_000L)
        t.update(out[0])
        assertFalse("premier fix après le tunnel : position en attente, pas un hors itinéraire", t.isOffRoute)
        val p = t.update(out[1])
        assertFalse(t.isOffRoute)
        assertEquals("position confirmée au second fix", 4_133.0 + 110.0 / 3.6, p.distanceAlongM, 5.0)
        assertTrue(p.offRouteM < 5.0)
    }

    @Test fun `really leaving the route is still detected after the pending fix`() {
        val route = TestRoutes.straight()
        val t = RouteTracker(route)
        FixScript(route).drive(0.0, 300.0, speedKmh = 50.0).forEach { t.update(it) }
        // 150 m au nord du tracé, en s'éloignant : hors itinéraire dès le premier fix exploitable (règle des 100 m).
        val away = FixScript(route).drive(300.0, 360.0, speedKmh = 50.0, lateralM = 150.0, headingOffsetDeg = -90.0, startMs = 22_000L)
        away.forEach { t.update(it) }
        assertTrue(t.isOffRoute)
    }
}
