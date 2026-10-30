package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceModelTest {
    @Test fun `maneuver keeps the v1 constructor and defaults the guidance fields`() {
        val m = Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", "Rue X", 120.0, 30.0, 4, null)
        assertNull(m.verbalPre)
        assertNull(m.verbalAlert)
        assertNull(m.exitNumber)
        assertTrue(m.lanes.isEmpty())
    }

    @Test fun `route request defaults to no avoidance`() {
        val r = RouteRequest(GeoPoint(48.0, 2.0), GeoPoint(49.0, 3.0), costing = RouteCosting.FASTEST)
        assertEquals(RoutePreferences(avoidTolls = false, avoidHighways = false, avoidFerries = false), r.preferences)
    }

    @Test fun `lane equality ignores direction order but not validity`() {
        assertEquals(Lane(setOf(LaneDirection.LEFT, LaneDirection.STRAIGHT), true), Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.LEFT), true))
        assertNotEquals(Lane(setOf(LaneDirection.LEFT), true), Lane(setOf(LaneDirection.LEFT), false))
    }

    @Test fun `lane directions are ordered left to right`() {
        assertEquals(
            listOf(LaneDirection.LEFT, LaneDirection.SLIGHT_LEFT, LaneDirection.STRAIGHT, LaneDirection.SLIGHT_RIGHT, LaneDirection.RIGHT, LaneDirection.UTURN, LaneDirection.MERGE_LEFT, LaneDirection.MERGE_RIGHT),
            LaneDirection.entries.toList(),
        )
    }

    @Test fun `route progress is a value object`() {
        val a = RouteProgress("r", 3, 120.0, 2.0, 1, 80.0, 900.0, 60.0, GeoPoint(48.0, 2.0), 90f)
        assertEquals(a, a.copy())
        assertEquals(80.0, a.distanceToNextManeuverM, 0.0)
    }
}
