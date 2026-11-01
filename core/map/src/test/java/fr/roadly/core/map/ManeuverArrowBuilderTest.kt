package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverArrowBuilderTest {
    // Ligne plein est, pas de 50 m, 21 points (0..1000 m) ; manœuvre au point 10 (500 m).
    private val lonPerM = 1.0 / (111_320.0 * Math.cos(Math.toRadians(48.85)))
    private val line = (0..20).map { GeoPoint(48.85, 2.3 + it * 50.0 * lonPerM) }

    private fun features(json: String) = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray

    @Test fun `arrow spans 80 m before and 40 m after the maneuver point`() {
        val f = features(ManeuverArrowBuilder.build(line, 10))
        val coords = f[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val pts = coords.map { GeoPoint(it.jsonArray[1].jsonPrimitive.double, it.jsonArray[0].jsonPrimitive.double) }
        assertEquals(80.0, pts.first().distanceTo(line[10]), 1.0)
        assertEquals(40.0, pts.last().distanceTo(line[10]), 1.0)
        val length = pts.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        assertEquals(120.0, length, 1.0)
    }

    @Test fun `head sits at the arrow end and points along the last segment`() {
        val f = features(ManeuverArrowBuilder.build(line, 10))
        val head = f[1].jsonObject
        assertEquals("head", head["properties"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(90.0, head["properties"]!!.jsonObject["bearing"]!!.jsonPrimitive.double, 0.5)
        val c = head["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(40.0, GeoPoint(c[1].jsonPrimitive.double, c[0].jsonPrimitive.double).distanceTo(line[10]), 1.0)
    }

    @Test fun `arrow is clipped at the route ends`() {
        val f = features(ManeuverArrowBuilder.build(line, 1))   // 50 m disponibles avant
        val coords = f[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val first = GeoPoint(coords[0].jsonArray[1].jsonPrimitive.double, coords[0].jsonArray[0].jsonPrimitive.double)
        assertEquals(0.0, first.distanceTo(line[0]), 0.1)
    }

    @Test fun `too short a geometry gives an empty collection`() {
        assertEquals(ManeuverArrowBuilder.EMPTY, ManeuverArrowBuilder.build(listOf(GeoPoint(48.85, 2.3)), 0))
        assertEquals(ManeuverArrowBuilder.EMPTY, ManeuverArrowBuilder.build(line, -1))
        assertEquals(ManeuverArrowBuilder.EMPTY, ManeuverArrowBuilder.build(line, 25))
    }

    @Test fun `u-turn keeps the way back and the head points backwards`() {
        // Aller-retour plein est puis plein ouest, pas de 5 m : points 0..4 puis 3..0 ; demi-tour au point 4.
        val out = (0..4).map { GeoPoint(48.85, 2.3 + it * 5.0 * lonPerM) }
        val geometry = out + out.dropLast(1).asReversed()
        val f = features(ManeuverArrowBuilder.build(geometry, 4))
        val coords = f[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val pts = coords.map { GeoPoint(it.jsonArray[1].jsonPrimitive.double, it.jsonArray[0].jsonPrimitive.double) }
        assertEquals(9, pts.size)
        assertEquals(out[4], pts[4])
        assertEquals(0.0, pts.last().distanceTo(out[0]), 0.1)
        assertEquals(270.0, f[1].jsonObject["properties"]!!.jsonObject["bearing"]!!.jsonPrimitive.double, 0.5)
    }
}
