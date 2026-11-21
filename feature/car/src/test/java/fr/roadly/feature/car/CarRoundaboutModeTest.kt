package fr.roadly.feature.car

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.RoundaboutBranch
import fr.roadly.core.navigation.RoundaboutLayout
import fr.roadly.core.navigation.RoundaboutLive
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RoundaboutProgressTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parité Android Auto du mode rond-point : même décompte que le téléphone, en une ligne et une image, jamais sans décompte fiable. */
class CarRoundaboutModeTest {
    private fun maneuver(type: ManeuverType) = Maneuver(type, "Entrez", "Rond-point", 100.0, 10.0, 0, 3)

    /** Anneau de 90 m, 3e sortie à 240° ; sorties à laisser à 30 m (80°) et 60 m (160°). */
    private fun layout(counted: Boolean = true) = RoundaboutLayout(
        ringLengthM = 90.0, sweepDeg = 240.0,
        branches = if (counted) listOf(RoundaboutBranch(1, 30.0, 80.0, false), RoundaboutBranch(2, 60.0, 160.0, false), RoundaboutBranch(3, 90.0, 240.0, true))
        else listOf(RoundaboutBranch(3, 90.0, 240.0, true)),
        targetRank = 3, counted = counted, exitStreet = "Rue de la Gare",
        profileOffsetsM = listOf(0.0, 90.0), profileAnglesDeg = listOf(0.0, 240.0),
    )

    private fun live(offsetM: Double?, counted: Boolean = true): RoundaboutLive {
        val l = layout(counted)
        val t = RoundaboutProgressTracker()
        return if (offsetM == null) t.update("r", 1, l, inside = false, distanceToNextM = 120.0)
        else t.update("r", 1, l, inside = true, distanceToNextM = 90.0 - offsetM)
    }

    @Test fun `the cue carries the phone countdown`() {
        val enter = maneuver(ManeuverType.ROUNDABOUT_ENTER)
        val exit = maneuver(ManeuverType.ROUNDABOUT_EXIT)
        assertEquals("Encore 2 sorties à passer · Rue de la Gare", CarGuidanceMapper.roundaboutCue(live(null), enter))
        assertEquals("Encore 1 sortie à passer · Rue de la Gare", CarGuidanceMapper.roundaboutCue(live(40.0), exit))
        assertEquals("Prochaine sortie · Rue de la Gare", CarGuidanceMapper.roundaboutCue(live(70.0), exit))
    }

    @Test fun `no countdown is claimed without a reliable count, after the exit, or on another manoeuvre`() {
        assertNull(CarGuidanceMapper.roundaboutCue(live(40.0, counted = false), maneuver(ManeuverType.ROUNDABOUT_EXIT)))
        assertNull(CarGuidanceMapper.roundaboutCue(null, maneuver(ManeuverType.ROUNDABOUT_ENTER)))
        assertNull(CarGuidanceMapper.roundaboutCue(live(40.0), maneuver(ManeuverType.TURN_LEFT)))
        val exited = RoundaboutProgressTracker().exited(layout(), 10.0)
        assertEquals(RoundaboutPhase.EXITED, exited.phase)
        assertNull(CarGuidanceMapper.roundaboutCue(exited, maneuver(ManeuverType.ROUNDABOUT_EXIT)))
    }

    @Test fun `the image places the exits to pass between the entry and the target, and dims the passed ones`() {
        // Sortie à gauche (−90°) : l'image dessine 270° d'anneau depuis l'entrée en bas (180°).
        val v = CarGuidanceMapper.roundabout(-90.0, 3, live(40.0))
        assertEquals(2, v.otherExits.size)
        assertEquals(listOf(true, false), v.otherExits.map { it.passed })
        // Du bas (180°) vers la sortie (−90°), dans le sens antihoraire : angles décroissants, strictement entre les deux.
        assertEquals(v.otherExits.map { it.angleDeg }.sortedDescending(), v.otherExits.map { it.angleDeg })
        v.otherExits.forEach { assertTrue("branche à ${it.angleDeg}°", it.angleDeg < 180.0 && it.angleDeg > -90.0) }
        assertEquals(90.0, v.otherExits[0].angleDeg, 1e-9)   // 80° / 240° de 270° → 180 − 90
        assertEquals(0.0, v.otherExits[1].angleDeg, 1e-9)    // 160° / 240° de 270° → 180 − 180
        val vehicle = v.vehicleAngleDeg!!
        assertTrue("véhicule entre les deux sorties : $vehicle", vehicle < 90.0 && vehicle > 0.0)
    }

    @Test fun `the vehicle moves by steps so the host is not refreshed at every fix`() {
        val a = CarGuidanceMapper.roundabout(-90.0, 3, live(40.0)).vehicleAngleDeg
        val b = CarGuidanceMapper.roundabout(-90.0, 3, live(41.5)).vehicleAngleDeg
        assertEquals(a, b)
        val c = CarGuidanceMapper.roundabout(-90.0, 3, live(70.0)).vehicleAngleDeg
        assertTrue(c!! < a!!)
    }

    @Test fun `without the phone roundabout mode the image is the plain one, and no vehicle before the ring`() {
        assertEquals(CarJunctionVisual.Roundabout(-90.0, 3), CarGuidanceMapper.roundabout(-90.0, 3, null))
        val uncounted = CarGuidanceMapper.roundabout(-90.0, 3, live(40.0, counted = false))
        assertTrue(uncounted.otherExits.isEmpty())
        val approach = CarGuidanceMapper.roundabout(-90.0, 3, live(null))
        assertNull(approach.vehicleAngleDeg)
        assertEquals(2, approach.otherExits.size)
        assertTrue(approach.otherExits.none { it.passed })
    }
}
