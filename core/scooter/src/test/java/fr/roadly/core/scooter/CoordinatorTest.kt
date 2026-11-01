package fr.roadly.core.scooter

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.scooter.Fixtures.edge
import fr.roadly.core.scooter.Fixtures.maneuver
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysisBuilder
import fr.roadly.core.scooter.guidance.AnnouncementStage
import fr.roadly.core.scooter.guidance.ScooterNavigationCoordinator
import fr.roadly.core.scooter.guidance.ScooterPhrases
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.range.RangeRisk
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import fr.roadly.core.scooter.segment.WayCategory
import fr.roadly.core.scooter.valhalla.TraceAttributesResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinatorTest {
    private val g2 = ScooterPresets.NINEBOT_MAX_G2

    private fun coordinator(edges: List<fr.roadly.core.scooter.segment.EdgeAttributes>, maneuvers: List<fr.roadly.core.model.Maneuver>? = null, battery: Double? = null, tags: Map<Long, Map<String, String>>? = null): ScooterNavigationCoordinator {
        val len = edges.sumOf { it.lengthM }
        val route = Fixtures.route(len, maneuvers ?: Fixtures.defaultManeuvers(len))
        val analysis = ScooterRouteAnalysisBuilder.build(route, TraceAttributesResult(edges, listOf("FR")), tags, g2)
        return ScooterNavigationCoordinator(route, analysis, ScooterRangeEstimator(g2), battery, temperatureC = 20.0, cruiseSpeedKmh = 20.0)
    }

    @Test fun turnOntoCycleTrack() {
        val c = coordinator(listOf(edge(300.0), edge(500.0, use = "cycleway")), listOf(maneuver(ManeuverType.DEPART, 0.0), maneuver(ManeuverType.TURN_RIGHT, 300.0), maneuver(ManeuverType.ARRIVE, 800.0)))
        assertEquals("Dans 100 mètres, prenez la piste cyclable à droite.", c.maneuverText(1, AnnouncementStage.NEAR, 100.0))
        assertEquals("Prenez la piste cyclable à droite.", c.maneuverText(1, AnnouncementStage.NOW, 10.0))
    }

    @Test fun plainTurnKeepsValhallaText() {
        val c = coordinator(listOf(edge(300.0), edge(300.0)), listOf(maneuver(ManeuverType.DEPART, 0.0), maneuver(ManeuverType.TURN_LEFT, 300.0), maneuver(ManeuverType.ARRIVE, 600.0)))
        assertNull(c.maneuverText(1, AnnouncementStage.FAR, 200.0))
    }

    @Test fun continueOnGreenway() {
        val tags = mapOf(1L to mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "traffic_sign" to "FR:C115"))
        val c = coordinator(listOf(edge(100.0), edge(900.0, use = "path", wayId = 1)), tags = tags)
        c.update(80.0, 20.0)
        val u = c.update(110.0, 20.0)
        assertTrue(u.speech.toString(), u.speech.any { it.startsWith("Continuez sur la voie verte pendant") })
        assertEquals(WayCategory.GREENWAY, u.ui.currentCategory)
    }

    @Test fun sharedRoadAndClimbWarningsAnnouncedOnce() {
        val c = coordinator(listOf(edge(400.0, use = "cycleway"), edge(300.0, speed = 30), edge(300.0, grade = 9.0)))
        val first = c.update(280.0, 20.0)
        assertTrue(first.speech.toString(), first.speech.contains(ScooterPhrases.sharedRoadAhead(30)))
        assertTrue(c.update(290.0, 20.0).speech.none { it.contains("route partagée") })
        val climb = c.update(400.0, 20.0)
        assertTrue(climb.speech.toString(), climb.speech.contains("Forte montée dans 300 mètres."))
    }

    @Test fun imprecisePositionDoesNotSpeakWarnings() {
        val c = coordinator(listOf(edge(400.0, use = "cycleway"), edge(300.0, speed = 30)))
        assertTrue(c.update(300.0, 20.0, extrapolated = true).speech.isEmpty())
    }

    @Test fun insufficientRangeAnnouncedOnceThenBatteryUpdate() {
        val c = coordinator(listOf(edge(20_000.0, use = "cycleway")), battery = 10.0)
        val u = c.update(0.0, 20.0)
        assertEquals(RangeRisk.INSUFFICIENT, u.ui.range!!.risk)
        assertTrue(u.speech.contains(ScooterPhrases.INSUFFICIENT_RANGE))
        assertTrue(c.update(50.0, 20.0).speech.none { it == ScooterPhrases.INSUFFICIENT_RANGE })
        c.setBattery(100.0)
        assertEquals(RangeRisk.OK, c.update(60.0, 20.0).ui.range!!.risk)
    }

    @Test fun noBatteryNoRange() {
        assertNull(coordinator(listOf(edge(1000.0))).update(0.0, 20.0).ui.range)
    }

    @Test fun nextCycleInfrastructure() {
        val c = coordinator(listOf(edge(250.0), edge(400.0, cycleLane = "dedicated")))
        val next = c.nextCycleInfrastructure(50.0)!!
        assertEquals(WayCategory.CYCLE_LANE, next.category)
        assertEquals(200.0, next.distanceM, 1.0)
    }

    @Test fun energyAccumulatesAlongTheRide() {
        val c = coordinator(listOf(edge(2000.0, use = "cycleway")), battery = 80.0)
        c.update(0.0, 20.0)
        val u = c.update(1000.0, 20.0)
        assertTrue(u.ui.range!!.batteryNowPct < 80.0)
        assertTrue(c.energyUsedWh > 0)
    }
}
