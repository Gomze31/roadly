package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteFeatureBuilderTest {
    private fun route(id: String) = Route(id, RouteCosting.FASTEST, 1.0, 1.0, listOf(GeoPoint(48.0, 2.0), GeoPoint(48.1, 2.1)), emptyList(), false, "t")

    @Test fun `active route is last and flagged`() {
        val json = Json.parseToJsonElement(RouteFeatureBuilder.build(listOf(route("a"), route("b")), activeId = "a")).jsonObject
        val features = json["features"]!!.jsonArray
        assertEquals(2, features.size)
        val last = features.last().jsonObject
        assertEquals("a", last["properties"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("true", last["properties"]!!.jsonObject["active"]!!.jsonPrimitive.content)
        // GeoJSON = [lon, lat]
        assertEquals("2.0", last["geometry"]!!.jsonObject["coordinates"]!!.jsonArray[0].jsonArray[0].jsonPrimitive.content)
    }
}
