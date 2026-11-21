package fr.roadly.core.navigation

import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Situation de conduite : modes, fenêtres de préparation liées au temps, verrou sans clignotement, GPS incertain, voix progressive. */
class GuidanceSituationTest {
    private fun m(type: ManeuverType, lanes: List<Lane> = emptyList(), post: String? = null) =
        Maneuver(type, "instruction", null, 100.0, 10.0, 0, null, lanes = lanes, verbalPost = post)

    private fun lanes(vararg valid: Boolean) = valid.map { Lane(setOf(LaneDirection.STRAIGHT), it) }

    private fun input(
        next: Maneuver, d: Double, kmh: Double? = 50.0, index: Int = 3, following: Maneuver? = null, gap: Double? = null,
        rerouting: Boolean = false, gpsLost: Boolean = false, extrapolated: Boolean = false, accuracy: Float = 5f, roundabout: RoundaboutLive? = null,
    ) = SituationInput("r", index, next, following, d, gap, kmh, rerouting, gpsLost, extrapolated, accuracy, roundabout)

    @Test fun `far from anything the screen is minimal`() {
        val s = SituationEngine().update(input(m(ManeuverType.TURN_RIGHT), 2_000.0))
        assertEquals(GuidanceMode.CRUISE, s.mode)
        assertEquals(GuidanceDensity.MINIMAL, s.density)
        assertEquals(0f, s.urgency)
    }

    @Test fun `the preparation window follows time, not only distance`() {
        // Sortie : 500 m en ville, 22 s à 130 km/h ≈ 794 m.
        assertEquals(500.0, SituationEngine.windowM(GuidanceMode.EXIT, 30.0), 1e-9)
        assertEquals(130.0 / 3.6 * 22.0, SituationEngine.windowM(GuidanceMode.EXIT, 130.0), 1e-6)
        assertEquals(GuidanceMode.CRUISE, SituationEngine().update(input(m(ManeuverType.EXIT_RIGHT), 700.0, kmh = 50.0)).mode)
        val fast = SituationEngine().update(input(m(ManeuverType.EXIT_RIGHT), 700.0, kmh = 130.0))
        assertEquals(GuidanceMode.EXIT, fast.mode)
        assertEquals(GuidanceDensity.REINFORCED, fast.density)
        assertEquals(ManeuverSide.RIGHT, fast.side)
        assertEquals(700.0 / (130.0 / 3.6), fast.secondsToManeuver!!, 1e-6)
    }

    @Test fun `each manoeuvre family gets its own mode`() {
        fun mode(t: ManeuverType, following: Maneuver? = null, gap: Double? = null, lanes: List<Lane> = emptyList()) =
            SituationEngine().update(input(m(t, lanes), 60.0, following = following, gap = gap)).mode
        assertEquals(GuidanceMode.PREPARE, mode(ManeuverType.TURN_LEFT))
        assertEquals(GuidanceMode.ROUNDABOUT_APPROACH, mode(ManeuverType.ROUNDABOUT_ENTER))
        assertEquals(GuidanceMode.FORK, mode(ManeuverType.KEEP_LEFT))
        assertEquals(GuidanceMode.EXIT, mode(ManeuverType.RAMP_RIGHT))
        assertEquals(GuidanceMode.ARRIVAL, mode(ManeuverType.ARRIVE))
        assertEquals(GuidanceMode.COMPLEX, mode(ManeuverType.TURN_LEFT, following = m(ManeuverType.TURN_RIGHT), gap = 120.0))
        assertEquals(GuidanceMode.COMPLEX, mode(ManeuverType.TURN_LEFT, lanes = lanes(true, false, false)))
        // Une simple continuation ne prépare rien.
        assertEquals(GuidanceMode.CRUISE, mode(ManeuverType.CONTINUE))
    }

    @Test fun `a roundabout exit or an arrival right after is not a chain`() {
        val e = SituationEngine()
        assertEquals(GuidanceMode.PREPARE, e.update(input(m(ManeuverType.TURN_LEFT), 60.0, following = m(ManeuverType.ARRIVE), gap = 50.0)).mode)
    }

    @Test fun `once open the window stays open through a slowdown, and urgency never needs the speed`() {
        val e = SituationEngine()
        val exit = m(ManeuverType.EXIT_RIGHT)
        assertEquals(GuidanceMode.EXIT, e.update(input(exit, 780.0, kmh = 130.0)).mode)
        // Freinage : la fenêtre recalculée (500 m) serait fermée à 560 m, le verrou la garde ouverte.
        val slowed = e.update(input(exit, 560.0, kmh = 40.0))
        assertEquals(GuidanceMode.EXIT, slowed.mode)
        assertTrue(slowed.urgency > 0f)
        val near = e.update(input(exit, 40.0, kmh = 40.0))
        assertTrue(near.urgency > slowed.urgency)
        // Manœuvre passée : la suivante repart de zéro.
        assertEquals(GuidanceMode.CRUISE, e.update(input(m(ManeuverType.TURN_LEFT), 3_000.0, index = 4)).mode)
    }

    @Test fun `one bad fix changes nothing, three in a row do, a good one restores`() {
        val e = SituationEngine()
        val turn = m(ManeuverType.TURN_RIGHT)
        assertEquals(GuidanceMode.PREPARE, e.update(input(turn, 100.0, accuracy = 60f)).mode)
        assertEquals(GuidanceMode.PREPARE, e.update(input(turn, 95.0, accuracy = 60f)).mode)
        val third = e.update(input(turn, 90.0, accuracy = 60f))
        assertEquals(GuidanceMode.GPS_UNCERTAIN, third.mode)
        assertEquals(GuidanceDensity.STANDARD, third.density)
        assertEquals(GuidanceMode.PREPARE, e.update(input(turn, 85.0, accuracy = 6f)).mode)
        assertEquals(GuidanceMode.GPS_UNCERTAIN, SituationEngine().update(input(turn, 85.0, gpsLost = true)).mode)
    }

    @Test fun `rerouting wins over everything`() {
        val s = SituationEngine().update(input(m(ManeuverType.EXIT_RIGHT), 100.0, rerouting = true, gpsLost = true))
        assertEquals(GuidanceMode.REROUTING, s.mode)
    }

    @Test fun `no time to manoeuvre is claimed at a standstill`() {
        assertNull(SituationEngine().update(input(m(ManeuverType.TURN_RIGHT), 100.0, kmh = 2.0)).secondsToManeuver)
        assertNull(SituationEngine().update(input(m(ManeuverType.TURN_RIGHT), 100.0, kmh = null)).secondsToManeuver)
    }

    @Test fun `lane advice names the side and the count, and stays silent when there is nothing to say`() {
        assertEquals("Restez sur la voie de droite.", LaneAdvice.of(lanes(false, false, true))!!.phrase)
        assertEquals("Restez sur les deux voies de droite.", LaneAdvice.of(lanes(false, true, true))!!.phrase)
        assertEquals("Restez sur les deux voies de gauche.", LaneAdvice.of(lanes(true, true, false, false))!!.phrase)
        assertEquals("Restez sur la voie du milieu.", LaneAdvice.of(lanes(false, true, false))!!.phrase)
        assertNull(LaneAdvice.of(lanes(true, true, true)))
        assertNull(LaneAdvice.of(lanes(false, false)))
        assertNull(LaneAdvice.of(lanes(true, false, true)))
        assertNull(LaneAdvice.of(emptyList()))
        // Les voies conseillées par Valhalla priment sur les voies simplement permises.
        val active = listOf(Lane(setOf(LaneDirection.RIGHT), valid = true), Lane(setOf(LaneDirection.RIGHT), valid = true, active = true))
        assertEquals("Restez sur la voie de droite.", LaneAdvice.of(active)!!.phrase)
        assertEquals(ManeuverSide.LEFT, SituationEngine.sideOf(m(ManeuverType.TURN_LEFT, lanes(true, false, false))))
    }

    @Test fun `positioning is said between anticipation and manoeuvre, never glued to another announcement`() {
        val exit = m(ManeuverType.EXIT_RIGHT)
        assertEquals("Serrez à droite.", PositioningPolicy.next(exit, 800.0, 130.0, scooter = false, sinceLastSpeechMs = 20_000))
        assertNull(PositioningPolicy.next(exit, 1_500.0, 130.0, scooter = false, sinceLastSpeechMs = null))
        assertNull(PositioningPolicy.next(exit, 300.0, 130.0, scooter = false, sinceLastSpeechMs = null))
        assertNull(PositioningPolicy.next(exit, 800.0, 130.0, scooter = false, sinceLastSpeechMs = 2_000))
        assertNull(PositioningPolicy.next(exit, 800.0, 130.0, scooter = true, sinceLastSpeechMs = null))
        assertEquals("Serrez à gauche.", PositioningPolicy.next(m(ManeuverType.RAMP_LEFT), 300.0, 70.0, scooter = false, sinceLastSpeechMs = null))
        // En ville, seulement avec de vraies voies ; une bifurcation ou un virage sans voies ne reçoit rien.
        assertNull(PositioningPolicy.next(exit, 150.0, 30.0, scooter = false, sinceLastSpeechMs = null))
        assertEquals("Restez sur la voie de gauche.", PositioningPolicy.next(m(ManeuverType.TURN_LEFT, lanes(true, false)), 150.0, 30.0, scooter = false, sinceLastSpeechMs = null))
        assertNull(PositioningPolicy.phrase(m(ManeuverType.KEEP_RIGHT)))
        assertNull(PositioningPolicy.phrase(m(ManeuverType.TURN_RIGHT)))
    }

    @Test fun `lanes that contradict the manoeuvre side are not spoken`() {
        // Sortie à droite, mais les voies conseillées sont celles de gauche : donnée contradictoire, silence plutôt qu'une consigne fausse.
        assertNull(PositioningPolicy.phrase(m(ManeuverType.EXIT_RIGHT, lanes(true, true, false))))
        assertNull(PositioningPolicy.phrase(m(ManeuverType.TURN_LEFT, lanes(false, false, true))))
        // Voies cohérentes ou centrales : dites.
        assertEquals("Restez sur la voie de droite.", PositioningPolicy.phrase(m(ManeuverType.EXIT_RIGHT, lanes(false, false, true))))
        assertEquals("Restez sur la voie du milieu.", PositioningPolicy.phrase(m(ManeuverType.EXIT_RIGHT, lanes(false, true, false))))
    }

    @Test fun `confirmation only before a long quiet stretch`() {
        val passed = m(ManeuverType.EXIT_RIGHT, post = "Continuez pendant 12 kilomètres.")
        assertEquals("Continuez pendant 12 kilomètres.", ConfirmationPolicy.text(passed, 12_000.0, 110.0, scooter = false))
        assertNull(ConfirmationPolicy.text(passed, 900.0, 40.0, scooter = false))
        // 1,8 km à 130 km/h = 50 s : trop court pour mériter une phrase.
        assertNull(ConfirmationPolicy.text(passed, 1_800.0, 130.0, scooter = false))
        assertNull(ConfirmationPolicy.text(passed, 12_000.0, 110.0, scooter = true))
        assertNull(ConfirmationPolicy.text(m(ManeuverType.TURN_LEFT), 12_000.0, 110.0, scooter = false))
    }
}
