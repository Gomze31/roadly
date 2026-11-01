package fr.roadly.feature.navigation

import fr.roadly.core.common.Formatters
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** Formatage pur de l'état de guidage (B1 hystérésis, B2 distances). */
class BuildUiStateTest {
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val zone = ZoneId.of("Europe/Paris")

    /** État `Guiding` à l'abscisse [along] de [r] (suivi neuf, vitesse 36 km/h). */
    private fun guiding(along: Double, r: Route = route): NavigationState.Guiding {
        val t = RouteTracker(r)
        val p = t.update(LocationFix(t.pointAt(along), null, null, 5f, 0L))
        val ms = r.maneuvers
        val upcoming = ms.subList(minOf(p.nextManeuverIndex + 1, ms.size), minOf(p.nextManeuverIndex + 4, ms.size))
        return NavigationState.Guiding(r, p, 36.0, null, upcoming, emptyList(), 0L, gpsLost = false, rerouting = false)
    }

    private fun ui(s: NavigationState, previous: NavigationUiState? = null) =
        buildUiState(s, muted = false, following = true, toleranceKmh = 5, styleUrl = "", zone = zone, previous = previous)

    @Test fun `approach stays on when the distance flickers back above the threshold`() {
        val turnAt = tracker.maneuverStartM[1]
        val near = ui(guiding(turnAt - 140.0))
        assertNotNull(near.approach)
        assertNotNull("hystérésis : l'approche reste active", ui(guiding(turnAt - 160.0), previous = near).approach)
        assertNull("sans état précédent, 160 m n'est pas une approche", ui(guiding(turnAt - 160.0)).approach)
    }

    @Test fun `approach is released when the next maneuver changes`() {
        val turnAt = tracker.maneuverStartM[1]
        val near = ui(guiding(turnAt - 140.0))
        val after = ui(guiding(turnAt + 20.0), previous = near)
        assertEquals(2, after.progress!!.nextManeuverIndex)
        assertNull("l'arrivée n'a jamais d'approche", after.approach)
    }

    @Test fun `approach of another route is not carried over`() {
        val turnAt = tracker.maneuverStartM[1]
        val near = ui(guiding(turnAt - 140.0))
        assertNull(ui(guiding(turnAt - 160.0, TestRoutes.straight("rerouted")), previous = near).approach)
    }

    @Test fun `horizon and banner use the same base and ignore Valhalla lengths`() {
        val skewed = route.copy(maneuvers = route.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(lengthMeters = 9_999.0) else m })
        val ui = ui(guiding(300.0, skewed))
        val t = RouteTracker(skewed)
        assertEquals(t.maneuverStartM[2] - t.maneuverStartM[1], ui.upcoming[0].distanceM - ui.progress!!.distanceToNextManeuverM, 1.0)
        assertEquals(Formatters.maneuverDistance(ui.progress!!.distanceToNextManeuverM), ui.banner!!.distanceText)
        assertEquals(Formatters.maneuverDistance(ui.upcoming[0].distanceM), ui.upcoming[0].distanceText)
    }

    @Test fun `430 m reads 450 m on the banner`() {
        assertEquals("450 m", ui(guiding(tracker.maneuverStartM[1] - 430.0)).banner!!.distanceText)
    }
}
