package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraversedLineTest {
    private val g = (0..200).map { GeoPoint(48.0 + it * 0.0001, 2.0) }
    private fun coords(json: String) = Json.parseToJsonElement(json).jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray

    @Test fun `for a fixed segment only the two-point tail is rebuilt and its size does not grow`() { // C3 : pas de GeoJSON croissant
        val line = TraversedLine()
        val first = line.update(g, 150, GeoPoint(48.01505, 2.0))
        assertEquals(151, coords(first.prefix!!).size)
        val sizes = (1..50).map { i ->
            val u = line.update(g, 150, GeoPoint(48.0150 + i * 0.000001, 2.0))
            assertNull("préfixe inchangé", u.prefix)
            assertEquals(2, coords(u.tail).size)
            u.tail.length
        }
        assertTrue("queue bornée : $sizes", sizes.max() < 200)
    }

    @Test fun `advancing a segment or switching route rebuilds the prefix`() {
        val line = TraversedLine()
        line.update(g, 10, g[11])
        assertNull(line.update(g, 10, g[11]).prefix)
        assertEquals(12, coords(line.update(g, 11, g[12]).prefix!!).size)
        val other = g.map { GeoPoint(it.lat, 2.1) }
        assertNotNull(line.update(other, 11, other[12]).prefix)
    }

    @Test fun `prefix plus tail draw the same line as the full builder`() {
        val snapped = GeoPoint(48.00125, 2.0)
        val u = TraversedLine().update(g, 12, snapped)
        val full = coords(TraversedFeatureBuilder.build(g, 12, snapped))
        assertEquals(full, JsonArray(coords(u.prefix!!) + coords(u.tail).drop(1)))
    }

    @Test fun `nothing traversed gives empty pieces`() {
        val none = TraversedLine().update(emptyList(), -1, null)
        assertEquals(TraversedFeatureBuilder.EMPTY, none.prefix)
        assertEquals(TraversedFeatureBuilder.EMPTY, none.tail)
        val start = TraversedLine().update(g, 0, g[0])
        assertEquals("p0 seul : pas de préfixe", TraversedFeatureBuilder.EMPTY, start.prefix)
        assertEquals(2, coords(start.tail).size)
    }
}
