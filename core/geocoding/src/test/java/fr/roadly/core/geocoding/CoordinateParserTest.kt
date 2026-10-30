package fr.roadly.core.geocoding

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {
    @Test fun `decimal with comma separator`() = assertEquals(GeoPoint(48.8566, 2.3522), CoordinateParser.parse("48.8566, 2.3522"))
    @Test fun `decimal with french decimal comma and space`() = assertEquals(GeoPoint(48.8566, 2.3522), CoordinateParser.parse("48,8566 2,3522"))
    @Test fun `dms north east`() {
        val p = CoordinateParser.parse("48°51'24\"N 2°21'08\"E")!!
        assertEquals(48.8567, p.lat, 0.0005); assertEquals(2.3522, p.lon, 0.0005)
    }
    @Test fun `dms west with O`() { assertEquals(-1.55, CoordinateParser.parse("47°13'00\"N 1°33'00\"O")!!.lon, 0.001) }
    @Test fun `text is not a coordinate`() = assertNull(CoordinateParser.parse("Paris"))
    @Test fun `out of range rejected`() = assertNull(CoordinateParser.parse("95.0, 2.0"))
}