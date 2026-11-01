package fr.roadly.core.scooter

import fr.roadly.core.scooter.Fixtures.edge
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysisBuilder
import fr.roadly.core.scooter.map.ScooterCamera
import fr.roadly.core.scooter.map.ScooterRouteFeatures
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.valhalla.TraceAttributesResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    @Test fun routeIsDrawnByWayTypeWithCautionOverlay() {
        val route = Fixtures.route(600.0)
        val a = ScooterRouteAnalysisBuilder.build(route, TraceAttributesResult(listOf(edge(200.0, use = "cycleway"), edge(200.0, roadClass = "trunk", speed = 90), edge(200.0, cycleLane = "dedicated")), listOf("FR")), null, ScooterPresets.NINEBOT_MAX_G2)
        val fc = Json.parseToJsonElement(ScooterRouteFeatures.build(route.geometry, a)).jsonObject
        val groups = fc["features"]!!.jsonArray.map { it.jsonObject["properties"]!!.jsonObject["group"]!!.jsonPrimitive.content }
        assertEquals("track", groups.first())
        assertTrue("shared" in groups && "lane" in groups && "caution" in groups)
        assertEquals(ScooterRouteFeatures.EMPTY, ScooterRouteFeatures.build(route.geometry, null))
    }

    @Test fun cameraTransitionsSmoothlyNearIntersections() {
        val far = ScooterCamera.target(500.0, 20.0)
        val mid = ScooterCamera.target(105.0, 20.0)
        val near = ScooterCamera.target(30.0, 20.0)
        assertEquals(ScooterCamera.CRUISE_TILT, far.tiltDeg, 1e-9)
        assertEquals(ScooterCamera.APPROACH_TILT, near.tiltDeg, 1e-9)
        assertTrue(mid.tiltDeg < far.tiltDeg && mid.tiltDeg > near.tiltDeg)
        assertTrue(near.zoom > far.zoom)
    }
}
