package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoPointTest {
    @Test fun `distance Paris Notre-Dame to Arc de Triomphe is about 4,5 km`() {
        val a = GeoPoint(48.8530, 2.3499)
        val b = GeoPoint(48.8738, 2.2950)
        assertEquals(4600.0, a.distanceTo(b), 150.0)
    }
    @Test fun `distance to self is zero`() {
        val p = GeoPoint(45.0, 5.0)
        assertEquals(0.0, p.distanceTo(p), 0.0001)
    }
}
