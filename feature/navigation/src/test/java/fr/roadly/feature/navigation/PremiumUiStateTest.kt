package fr.roadly.feature.navigation

import fr.roadly.core.common.Formatters
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RoundaboutResolver
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Étape D : données du bandeau premium (rond-point, « puis », panneau, voies) et de la feuille « Trajet complet ». */
class PremiumUiStateTest {
    private val zone = ZoneId.of("Europe/Paris")

    /** `Guiding` à l'abscisse [along] de [r], avec les voies et le rond-point que publierait la session. */
    private fun guiding(r: Route, along: Double): NavigationState.Guiding {
        val t = RouteTracker(r)
        val p = t.update(LocationFix(t.pointAt(along), null, null, 5f, 0L))
        val ms = r.maneuvers
        val upcoming = ms.subList(minOf(p.nextManeuverIndex + 1, ms.size), minOf(p.nextManeuverIndex + 4, ms.size))
        val roundabout = RoundaboutResolver.current(RoundaboutResolver.resolveAll(r), p.nextManeuverIndex)
        return NavigationState.Guiding(r, p, 36.0, null, upcoming, ms[p.nextManeuverIndex].lanes, 0L, gpsLost = false, rerouting = false, roundabout = roundabout)
    }

    private fun ui(s: NavigationState) = buildUiState(s, muted = false, following = true, toleranceKmh = 5, styleUrl = "", zone = zone)

    private val twoLanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.RIGHT), valid = true, active = true))

    @Test fun `the roundabout reaches the banner only when the next maneuver is in a roundabout`() {
        val r = TestRoutes.twoRoundabouts()
        val before = ui(guiding(r, 120.0))
        assertEquals(ManeuverType.ROUNDABOUT_ENTER, before.banner!!.type)
        assertEquals(RoundaboutResolver.resolveAll(r)[1], before.roundabout)
        val turn = guiding(TestRoutes.straight(), 300.0).copy(roundabout = RoundaboutResolver.resolveAll(r)[1])
        assertNull("prochaine manœuvre = virage : pas de schéma", ui(turn).roundabout)
    }

    @Test fun `the following maneuver is shown under the banner when it comes within 500 m`() {
        val then = ui(guiding(TestRoutes.twoRoundabouts(), 120.0)).then!!   // entrée à 200 m, sortie 30 m plus loin
        assertEquals(ManeuverType.ROUNDABOUT_EXIT, then.type)
        assertEquals("Rue Médiane", then.label)
        val straight = TestRoutes.straight()
        val t = RouteTracker(straight)
        assertTrue(t.maneuverStartM[2] - t.maneuverStartM[1] >= THEN_WITHIN_M)
        assertNull("arrivée à plus de 500 m après le virage", ui(guiding(straight, 300.0)).then)
    }

    @Test fun `then uses the street, else the instruction, and stops at 500 m`() {
        val arrive = TestRoutes.straight().maneuvers[2]
        assertEquals("Vous êtes arrivé.", thenFor(arrive, 499.0)!!.label)
        assertNull(thenFor(arrive, 500.0)); assertNull(thenFor(null, 10.0)); assertNull(thenFor(arrive, null))
    }

    @Test fun `an exit sign is kept for exits, ramps, forks and merges that carry sign data`() {
        val base = TestRoutes.straight().maneuvers[1]
        val exit = base.copy(type = ManeuverType.EXIT_RIGHT, exitNumber = "31b", exitToward = "Toulouse-Centre · Blagnac", exitBranch = "A62")
        assertEquals(ExitSignUi("31b", "Toulouse-Centre · Blagnac", "A62"), exitSignFor(exit))
        assertEquals(ExitSignUi(null, "Montpellier", null), exitSignFor(base.copy(type = ManeuverType.KEEP_LEFT, exitToward = "Montpellier")))
        assertNull("virage : pas de panneau", exitSignFor(base.copy(exitNumber = "12")))
        assertNull("bretelle sans données de panneau", exitSignFor(base.copy(type = ManeuverType.RAMP_RIGHT)))
    }

    @Test fun `the sign of the next maneuver reaches the state before the approach`() {
        val r0 = TestRoutes.straight()
        val r = r0.copy(maneuvers = r0.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(type = ManeuverType.EXIT_RIGHT, exitNumber = "19", exitToward = "Balma") else m })
        val far = ui(guiding(r, 100.0))
        assertEquals(ExitSignUi("19", "Balma", null), far.sign)
        assertNull("loin : pas d'approche, le panneau n'est que dans l'aperçu", far.approach)
    }

    @Test fun `upcoming maneuvers carry their instruction and sign`() {
        val r = TestRoutes.straight()
        val exit = r.maneuvers[1].copy(type = ManeuverType.EXIT_LEFT, exitNumber = "20", exitToward = "Albi")
        val u = ui(guiding(r, 100.0).copy(upcoming = listOf(exit))).upcoming.single()
        assertEquals(exit.instruction, u.instruction)
        assertEquals(ExitSignUi("20", "Albi", null), u.sign)
    }

    @Test fun `lanes show from 400 m or at the approach, never without data`() {
        assertFalse(lanesVisible(twoLanes, 450.0, approaching = false))
        assertTrue(lanesVisible(twoLanes, 399.0, approaching = false))
        assertTrue("approche verrouillée au-delà de 400 m", lanesVisible(twoLanes, 600.0, approaching = true))
        assertFalse("sans données : rien", lanesVisible(emptyList(), 50.0, approaching = true))
    }

    @Test fun `lanes of the next maneuver appear within 400 m and go after it (scenario 18)`() {
        val r0 = TestRoutes.straight()
        val r = r0.copy(maneuvers = r0.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(lanes = twoLanes) else m })
        val turnAt = RouteTracker(r).maneuverStartM[1]
        assertFalse(ui(guiding(r, turnAt - 450.0)).showLanes)
        val near = ui(guiding(r, turnAt - 390.0))
        assertTrue(near.showLanes); assertEquals(twoLanes, near.lanes)
        assertFalse("après le virage : voies de l'arrivée (aucune)", ui(guiding(r, turnAt + 30.0)).showLanes)
    }

    @Test fun `lane states - advised, possible, forbidden, and valid lanes advised when none is active`() {
        assertEquals(
            listOf(LaneState.VALID, LaneState.ACTIVE, LaneState.INVALID),
            laneStates(listOf(Lane(setOf(LaneDirection.STRAIGHT), true), Lane(setOf(LaneDirection.RIGHT), true, active = true), Lane(setOf(LaneDirection.LEFT), false))),
        )
        assertEquals(
            listOf(LaneState.ACTIVE, LaneState.INVALID),
            laneStates(listOf(Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), true), Lane(setOf(LaneDirection.LEFT), false))),
        )
    }

    @Test fun `the full trip lists every step, passed ones marked, with Valhalla step lengths`() {
        val r = TestRoutes.straight()
        val steps = tripSteps(r, nextIndex = 1)
        assertEquals(3, steps.size)
        assertTrue(steps[0].passed); assertFalse(steps[0].isNext)
        assertTrue(steps[1].isNext); assertFalse(steps[1].passed)
        assertEquals("Rue d'Essai", steps[1].street)
        assertEquals(Formatters.distance(r.maneuvers[1].lengthMeters), steps[1].lengthText)
        assertNull("arrivée : pas de longueur", steps[2].lengthText)
    }
}
