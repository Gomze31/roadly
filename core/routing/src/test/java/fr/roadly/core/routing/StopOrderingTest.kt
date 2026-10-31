package fr.roadly.core.routing

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class StopOrderingTest {
    private fun p(east: Double) = GeoPoint(48.85, 2.35 + east / 73_000.0)

    @Test fun `nearest neighbour visits stops in increasing distance from the origin`() {
        val stops = listOf(p(5_000.0), p(1_000.0), p(3_000.0))
        assertEquals(listOf(1, 2, 0), StopOrdering.nearestNeighbour(p(0.0), stops))
    }

    @Test fun `move reorders and ignores invalid indexes`() {
        assertEquals(listOf("b", "a", "c"), StopOrdering.move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("c", "a", "b"), StopOrdering.move(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b", "c"), StopOrdering.move(listOf("a", "b", "c"), 0, 5))
    }
}
