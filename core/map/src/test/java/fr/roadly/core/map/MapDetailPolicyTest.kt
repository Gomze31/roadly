package fr.roadly.core.map

import fr.roadly.core.model.MapDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class MapDetailPolicyTest {
    @Test fun `reduced setting always wins`() {
        assertEquals(MapDetail.REDUCED, MapDetailPolicy.effective(MapDetail.REDUCED, 30.0, MapDetail.NORMAL))
        assertEquals(MapDetail.REDUCED, MapDetailPolicy.effective(MapDetail.REDUCED, null, MapDetail.NORMAL))
    }

    @Test fun `normal is reduced above 90 km per h and restored below 80 only`() {
        var d = MapDetail.NORMAL
        val steps = listOf(50.0 to MapDetail.NORMAL, 95.0 to MapDetail.REDUCED, 85.0 to MapDetail.REDUCED, 80.0 to MapDetail.REDUCED, 79.0 to MapDetail.NORMAL, 89.0 to MapDetail.NORMAL)
        for ((speed, expected) in steps) {
            d = MapDetailPolicy.effective(MapDetail.NORMAL, speed, d)
            assertEquals("à $speed km/h", expected, d)
        }
    }

    @Test fun `an unknown speed keeps the current level`() {
        assertEquals(MapDetail.REDUCED, MapDetailPolicy.effective(MapDetail.NORMAL, null, MapDetail.REDUCED))
        assertEquals(MapDetail.NORMAL, MapDetailPolicy.effective(MapDetail.NORMAL, null, MapDetail.NORMAL))
    }
}
