package fr.roadly.feature.navigation

import androidx.compose.ui.geometry.Size
import fr.roadly.core.navigation.GuidanceDensity
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.GuidanceSituation
import fr.roadly.core.navigation.ManeuverSide
import fr.roadly.core.navigation.RoundaboutPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode rond-point et densité adaptative côté écran : règles d'affichage pures et géométrie du schéma vivant. */
class RoundaboutModeUiTest {
    private fun guiding(mode: GuidanceMode, density: GuidanceDensity = GuidanceDensity.REINFORCED, urgency: Float = 0.4f, side: ManeuverSide? = null) =
        NavigationUiState(phase = NavigationPhase.GUIDING, situation = GuidanceSituation(mode, density, urgency, side = side))

    @Test fun `the mode shows inside the ring and after the exit, and on approach only once the window is open`() {
        val approach = previewRoundaboutLive(3, 0, RoundaboutPhase.APPROACH)
        assertFalse(roundaboutModeVisible(guiding(GuidanceMode.CRUISE, GuidanceDensity.MINIMAL).copy(roundaboutLive = approach)))
        assertTrue(roundaboutModeVisible(guiding(GuidanceMode.ROUNDABOUT_APPROACH).copy(roundaboutLive = approach)))
        assertTrue(roundaboutModeVisible(guiding(GuidanceMode.ROUNDABOUT).copy(roundaboutLive = previewRoundaboutLive(3, 1, RoundaboutPhase.INSIDE))))
        assertTrue(roundaboutModeVisible(guiding(GuidanceMode.ROUNDABOUT_EXITED).copy(roundaboutLive = previewRoundaboutLive(3, 2, RoundaboutPhase.EXITED))))
        assertFalse(roundaboutModeVisible(guiding(GuidanceMode.ROUNDABOUT)))
        // Arrivée : plus de guidage, plus de schéma.
        assertFalse(roundaboutModeVisible(guiding(GuidanceMode.ROUNDABOUT).copy(phase = NavigationPhase.ARRIVED, roundaboutLive = approach)))
    }

    @Test fun `countdown text follows the count and claims nothing without a reliable count`() {
        assertEquals(RoundaboutCountdown(RoundaboutCountdown.Kind.LET_PASS, 2), roundaboutCountdown(previewRoundaboutLive(3, 0, RoundaboutPhase.APPROACH)))
        assertEquals(RoundaboutCountdown(RoundaboutCountdown.Kind.LET_PASS, 1), roundaboutCountdown(previewRoundaboutLive(3, 1, RoundaboutPhase.INSIDE)))
        assertEquals(RoundaboutCountdown.Kind.NEXT_EXIT, roundaboutCountdown(previewRoundaboutLive(3, 2, RoundaboutPhase.INSIDE))!!.kind)
        assertEquals(RoundaboutCountdown.Kind.NEXT_EXIT, roundaboutCountdown(previewRoundaboutLive(1, 0, RoundaboutPhase.APPROACH))!!.kind)
        assertEquals(RoundaboutCountdown.Kind.DONE, roundaboutCountdown(previewRoundaboutLive(3, 2, RoundaboutPhase.EXITED))!!.kind)
        assertNull(roundaboutCountdown(previewRoundaboutLive(4, 0, RoundaboutPhase.INSIDE, counted = false)))
    }

    @Test fun `complex situations hide the secondary information, simple ones keep it`() {
        assertTrue(secondaryInfoVisible(guiding(GuidanceMode.CRUISE, GuidanceDensity.MINIMAL)))
        assertTrue(secondaryInfoVisible(guiding(GuidanceMode.PREPARE, GuidanceDensity.STANDARD)))
        assertFalse(secondaryInfoVisible(guiding(GuidanceMode.EXIT)))
        val withItems = guiding(GuidanceMode.EXIT).copy(upcoming = listOf(UpcomingUi(fr.roadly.core.model.ManeuverType.TURN_LEFT, "300 m", null, 300.0)))
        assertFalse(horizonVisible(withItems, landscape = false))
        assertTrue(horizonVisible(withItems.copy(situation = GuidanceSituation()), landscape = false))
    }

    @Test fun `urgency bar only while a manoeuvre is being prepared`() {
        assertEquals(0.4f, bannerUrgency(guiding(GuidanceMode.EXIT)))
        assertEquals(0.4f, bannerUrgency(guiding(GuidanceMode.ROUNDABOUT)))
        assertEquals(0f, bannerUrgency(guiding(GuidanceMode.GPS_UNCERTAIN)))
        assertEquals(0f, bannerUrgency(guiding(GuidanceMode.REROUTING)))
        assertEquals(0f, bannerUrgency(NavigationUiState()))
    }

    @Test fun `side hint for exits and forks, never over lanes or a roundabout`() {
        assertEquals(ManeuverSide.RIGHT, sideHint(guiding(GuidanceMode.EXIT, side = ManeuverSide.RIGHT)))
        assertEquals(ManeuverSide.LEFT, sideHint(guiding(GuidanceMode.FORK, side = ManeuverSide.LEFT)))
        assertNull(sideHint(guiding(GuidanceMode.PREPARE, side = ManeuverSide.RIGHT)))
        assertNull(sideHint(guiding(GuidanceMode.EXIT, side = ManeuverSide.RIGHT).copy(showLanes = true)))
        assertNull(sideHint(guiding(GuidanceMode.EXIT)))
    }

    @Test fun `gps uncertain pill never doubles the gps lost pill`() {
        assertTrue(gpsUncertainVisible(guiding(GuidanceMode.GPS_UNCERTAIN, GuidanceDensity.STANDARD)))
        assertFalse(gpsUncertainVisible(guiding(GuidanceMode.GPS_UNCERTAIN, GuidanceDensity.STANDARD).copy(gpsLost = true)))
        assertFalse(gpsUncertainVisible(guiding(GuidanceMode.CRUISE)))
    }

    @Test fun `diagram geometry - every branch stays inside the square and the vehicle stays on its path`() {
        val size = Size(1000f, 1000f)
        val c = RoundaboutModeDiagram.center(size)
        val r = 1000f * RoundaboutModeDiagram.RING
        for (angle in 0..360 step 5) {
            val tip = RoundaboutModeDiagram.targetTip(size, angle.toDouble())
            assertTrue("pointe $angle° dans le carré : $tip", tip.x in 0f..1000f && tip.y in 0f..1000f)
            assertTrue("la sortie à prendre dépasse toujours l'anneau ($angle°)", (tip - c).getDistance() > r * 1.3f)
            val stub = RoundaboutModeDiagram.stubEnd(size, angle.toDouble())
            assertTrue("branche $angle° dans le carré : $stub", stub.x in 0f..1000f && stub.y in 0f..1000f)
            assertEquals(r, (RoundaboutModeDiagram.onRing(size, angle.toDouble()) - c).getDistance(), 0.5f)
        }
        // 0° = bas (entrée), 90° = droite : le véhicule tourne dans le sens antihoraire, circulation à droite.
        assertTrue(RoundaboutModeDiagram.onRing(size, 0.0).y > c.y)
        assertTrue(RoundaboutModeDiagram.onRing(size, 90.0).x > c.x)
        val inside = previewRoundaboutLive(4, 1, RoundaboutPhase.INSIDE)
        assertEquals(r, (RoundaboutModeDiagram.vehicle(size, inside, inside.vehicleAngleDeg!!) - c).getDistance(), 0.5f)
        val approach = previewRoundaboutLive(4, 0, RoundaboutPhase.APPROACH)
        assertTrue("à l'approche, le véhicule est sous l'anneau", RoundaboutModeDiagram.vehicle(size, approach, 0.0).y > c.y + r)
        val exited = previewRoundaboutLive(4, 3, RoundaboutPhase.EXITED)
        assertTrue("sorti, le véhicule est hors de l'anneau", (RoundaboutModeDiagram.vehicle(size, exited, 0.0) - c).getDistance() > r)
    }

    @Test fun `preview layouts keep exits ordered and the vehicle between the right exits`() {
        val live = previewRoundaboutLive(4, 2, RoundaboutPhase.INSIDE)
        assertEquals(2, live.exitsPassed)
        assertEquals(1, live.exitsRemaining)
        val angle = live.vehicleAngleDeg!!
        assertTrue(angle > live.layout.branches[1].angleDeg && angle < live.layout.branches[2].angleDeg)
    }
}
