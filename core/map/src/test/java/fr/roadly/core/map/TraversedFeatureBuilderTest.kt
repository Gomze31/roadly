package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TraversedFeatureBuilderTest {
    private val g = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.1, 2.1), GeoPoint(48.2, 2.2))
    private fun coords(json: String) = Json.parseToJsonElement(json).jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray

    @Test fun `line runs from the start to the snapped position`() {
        val c = coords(TraversedFeatureBuilder.build(g, 1, GeoPoint(48.15, 2.15)))
        assertEquals(3, c.size)
        assertEquals("2.0", c[0].jsonArray[0].jsonPrimitive.content)       // [lon, lat]
        assertEquals("2.15", c[2].jsonArray[0].jsonPrimitive.content)
        assertEquals("48.15", c[2].jsonArray[1].jsonPrimitive.content)
    }

    @Test fun `nothing traversed gives an empty collection`() {
        assertEquals(TraversedFeatureBuilder.EMPTY, TraversedFeatureBuilder.build(g, -1, null))
        assertEquals(TraversedFeatureBuilder.EMPTY, TraversedFeatureBuilder.build(g, 0, null))
        assertEquals(TraversedFeatureBuilder.EMPTY, TraversedFeatureBuilder.build(emptyList(), 3, GeoPoint(48.0, 2.0)))
    }

    @Test fun `index beyond the geometry is clamped`() = assertEquals(3, coords(TraversedFeatureBuilder.build(g, 10, null)).size)
}
