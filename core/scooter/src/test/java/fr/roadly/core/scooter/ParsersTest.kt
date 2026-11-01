package fr.roadly.core.scooter

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.scooter.geo.Polyline6Encoder
import fr.roadly.core.scooter.geo.RouteLine
import fr.roadly.core.scooter.osm.Overpass
import fr.roadly.core.scooter.osm.PoiCompatibility
import fr.roadly.core.scooter.osm.ScooterPoiKind
import fr.roadly.core.scooter.valhalla.TraceAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun traceAttributesParsing() {
        val body = """{"edges":[
            {"length":0.12,"use":"cycleway","cycle_lane":"separated","road_class":"residential","surface":"paved_smooth","weighted_grade":2.5,"way_id":123,"names":["Voie verte du canal"]},
            {"length":0.3,"use":"road","cycle_lane":"none","road_class":"primary","speed_limit":"unlimited"},
            {"length":0.05,"use":"road","speed_limit":50},
            {"use":"road"}
          ],"admins":[{"country_code":"fr","country_text":"France"}]}"""
        val r = TraceAttributes.parse(json, body)!!
        assertEquals(3, r.edges.size) // l'arête sans longueur est écartée
        assertEquals(120.0, r.edges[0].lengthM, 1e-9)
        assertEquals(123L, r.edges[0].wayId)
        assertEquals(listOf("Voie verte du canal"), r.edges[0].names)
        assertNull(r.edges[1].speedLimitKmh)
        assertEquals(50, r.edges[2].speedLimitKmh)
        assertEquals(listOf("FR"), r.countryCodes)
        assertNull(TraceAttributes.parse(json, "pas du json"))
    }

    @Test fun traceAttributesRequest() {
        val req = TraceAttributes.request("abc", mapOf("use_roads" to 0.1, "shortest" to true, "bicycle_type" to "hybrid"))
        assertEquals("edge_walk", req["shape_match"]!!.jsonPrimitive.content)
        val opts = req["costing_options"]!!.jsonObject["bicycle"]!!.jsonObject
        assertEquals("0.1", opts["use_roads"]!!.jsonPrimitive.content)
        assertEquals("true", opts["shortest"]!!.jsonPrimitive.content)
    }

    @Test fun overpassWayTags() {
        val body = """{"elements":[{"type":"way","id":5,"tags":{"highway":"cycleway","surface":"asphalt"}},{"type":"node","id":6}]}"""
        val tags = Overpass.parseWayTags(json, body)!!
        assertEquals(mapOf(5L to mapOf("highway" to "cycleway", "surface" to "asphalt")), tags)
        assertTrue(Overpass.wayTagsQuery(listOf(1, 2, 2)).contains("way(id:1,2)"))
    }

    @Test fun overpassPoisAndCompatibility() {
        val body = """{"elements":[
           {"type":"node","id":1,"lat":43.6,"lon":1.44,"tags":{"amenity":"kick-scooter_parking"}},
           {"type":"way","id":2,"center":{"lat":43.61,"lon":1.45},"tags":{"amenity":"charging_station","bicycle":"yes","name":"Borne"}},
           {"type":"node","id":3,"lat":43.62,"lon":1.46,"tags":{"amenity":"charging_station","small_electric_vehicle":"yes"}}]}"""
        val parking = Overpass.parsePois(json, body, ScooterPoiKind.PARKING)!!
        assertEquals(PoiCompatibility.EXPLICIT, parking.first { it.osmId == 1L }.compatibility)
        val charging = Overpass.parsePois(json, body, ScooterPoiKind.CHARGING)!!
        assertEquals(PoiCompatibility.E_BIKE_ONLY, charging.first { it.osmId == 2L }.compatibility)
        assertEquals(GeoPoint(43.61, 1.45), charging.first { it.osmId == 2L }.point)
        assertEquals(PoiCompatibility.EXPLICIT, charging.first { it.osmId == 3L }.compatibility)
        assertTrue(Overpass.poiQuery(ScooterPoiKind.CHARGING, GeoPoint(43.6, 1.44), 2000).contains("around:2000,43.600000,1.440000"))
    }

    @Test fun polyline6RoundTrip() {
        val pts = listOf(GeoPoint(43.604652, 1.444209), GeoPoint(43.605, 1.445), GeoPoint(43.6, 1.44))
        assertEquals(pts, decode(Polyline6Encoder.encode(pts)))
    }

    @Test fun routeLineSlices() {
        val line = RouteLine(Fixtures.line(100.0))
        assertEquals(100.0, line.lengthM, 0.5)
        val slice = line.slice(25.0, 55.0)
        assertEquals(25.0, slice.first().distanceTo(line.points.first()), 0.5)
        assertEquals(55.0, slice.last().distanceTo(line.points.first()), 0.5)
    }

    private fun decode(s: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(); var i = 0; var lat = 0L; var lon = 0L
        fun next(): Long { var r = 0L; var sh = 0; var b: Int; do { b = s[i++].code - 63; r = r or ((b and 0x1f).toLong() shl sh); sh += 5 } while (b >= 0x20); return if (r and 1L != 0L) (r shr 1).inv() else r shr 1 }
        while (i < s.length) { lat += next(); lon += next(); out += GeoPoint(lat / 1e6, lon / 1e6) }
        return out
    }
}
