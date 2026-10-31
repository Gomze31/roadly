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

    @Test fun `the prefix is rebuilt per chunk of segments, on a backtrack or on a new route`() {
        val chunk = TraversedLine.TRAVERSED_CHUNK
        val line = TraversedLine()
        assertEquals(11, coords(line.update(g, 10, g[11]).prefix!!).size)
        assertNull(line.update(g, 10, g[11]).prefix)
        assertNull("avance d'un segment : la queue suffit", line.update(g, 11, g[12]).prefix)
        assertNull(line.update(g, 10 + chunk - 1, g[10 + chunk]).prefix)
        assertEquals(11 + chunk, coords(line.update(g, 10 + chunk, g[11 + chunk]).prefix!!).size)
        assertEquals("recul : préfixe reconstruit", 6, coords(line.update(g, 5, g[6]).prefix!!).size)
        val other = g.map { GeoPoint(it.lat, 2.1) }
        assertNotNull(line.update(other, 5, other[6]).prefix)
    }

    @Test fun `over 1 000 advances the prefix is rebuilt per chunk, the tail stays bounded and the union is the traversed line`() { // C3
        val long = (0..1_100).map { GeoPoint(48.0 + it * 0.0001, 2.0) }
        val chunk = TraversedLine.TRAVERSED_CHUNK
        val line = TraversedLine()
        var prefix: JsonArray? = null
        var rebuilds = 0
        for (i in 0 until 1_000) {
            val snapped = GeoPoint(48.0 + i * 0.0001 + 0.00005, 2.0)
            val u = line.update(long, i, snapped)
            u.prefix?.let { rebuilds++; prefix = if (it == TraversedFeatureBuilder.EMPTY) JsonArray(emptyList()) else coords(it) }
            val tail = coords(u.tail)
            assertTrue("queue de ${tail.size} points à $i", tail.size <= chunk + 2)
            val union = if (prefix!!.isEmpty()) tail else JsonArray(prefix!! + tail.drop(1))
            assertEquals("tracé dessiné à $i", coords(TraversedFeatureBuilder.build(long, i, snapped)), union)
        }
        assertTrue("$rebuilds reconstructions du préfixe", rebuilds <= 1_000 / chunk + 1)
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
