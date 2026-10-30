package fr.roadly.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class Polyline6Test {
    @Test fun `decodes google reference polyline at 1e5`() {
        val pts = Polyline6.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 1e5)
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-5); assertEquals(-120.2, pts[0].lon, 1e-5)
        assertEquals(43.252, pts[2].lat, 1e-5); assertEquals(-126.453, pts[2].lon, 1e-5)
    }
    @Test fun `empty string gives empty list`() = assertEquals(0, Polyline6.decode("").size)
}
