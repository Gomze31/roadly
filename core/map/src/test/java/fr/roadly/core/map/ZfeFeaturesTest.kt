package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ZfeFeaturesTest {
    @Test fun `empty list gives an empty collection`() = assertEquals(ZfeFeatures.EMPTY, ZfeFeatures.build(emptyList()))

    @Test fun `polygons are closed, lon-lat ordered and carry their state`() {
        val ring = listOf(GeoPoint(43.59, 1.43), GeoPoint(43.59, 1.46), GeoPoint(43.61, 1.46))
        val json = Json.parseToJsonElement(ZfeFeatures.build(listOf(ZfeShape("forbidden", listOf(listOf(ring)))))).jsonObject
        val f = json["features"]!!.jsonArray.single().jsonObject
        assertEquals("forbidden", f["properties"]!!.jsonObject["state"]!!.jsonPrimitive.content)
        val coords = f["geometry"]!!.jsonObject["coordinates"]!!.jsonArray[0].jsonArray[0].jsonArray
        assertEquals(4, coords.size)
        assertEquals(1.43, coords[0].jsonArray[0].jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(coords.first(), coords.last())
    }
}
