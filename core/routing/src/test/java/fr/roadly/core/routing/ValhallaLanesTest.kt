package fr.roadly.core.routing

import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaLanesTest {
    @Test fun `bitmask maps to grouped directions`() {
        assertEquals(setOf(LaneDirection.STRAIGHT), ValhallaLanes.directions(2))
        assertEquals(setOf(LaneDirection.LEFT), ValhallaLanes.directions(4))   // sharp left → LEFT
        assertEquals(setOf(LaneDirection.LEFT), ValhallaLanes.directions(8))
        assertEquals(setOf(LaneDirection.SLIGHT_LEFT), ValhallaLanes.directions(16))
        assertEquals(setOf(LaneDirection.SLIGHT_RIGHT), ValhallaLanes.directions(32))
        assertEquals(setOf(LaneDirection.RIGHT), ValhallaLanes.directions(64 or 128))
        assertEquals(setOf(LaneDirection.UTURN), ValhallaLanes.directions(256))
        assertEquals(setOf(LaneDirection.MERGE_LEFT, LaneDirection.MERGE_RIGHT), ValhallaLanes.directions(512 or 1024))
        assertEquals(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), ValhallaLanes.directions(2 or 64))
    }

    @Test fun `none and empty masks give no direction`() {
        assertTrue(ValhallaLanes.directions(0).isEmpty())
        assertTrue(ValhallaLanes.directions(1).isEmpty())
    }

    @Test fun `parse accepts int or boolean validity and skips malformed lanes`() {
        val raw = Json.parseToJsonElement("""[{"directions":8,"valid":8},{"directions":2},{"directions":66,"active":64},{"directions":2,"valid":true},{"directions":"oops"},42]""").jsonArray
        assertEquals(
            listOf(
                Lane(setOf(LaneDirection.LEFT), true),
                Lane(setOf(LaneDirection.STRAIGHT), false),
                Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, active = true),
                Lane(setOf(LaneDirection.STRAIGHT), true),
            ),
            ValhallaLanes.parse(raw),
        )
    }
}
