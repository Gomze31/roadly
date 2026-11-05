package fr.roadly.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenZonesTest {
    @Test fun `add keeps order, removes duplicates and commas`() {
        var keys = HiddenZones.add(emptyList(), "06001")
        keys = HiddenZones.add(keys, "06002")
        keys = HiddenZones.add(keys, "06001")
        assertEquals(listOf("06002", "06001"), keys)
        assertEquals(listOf("06002", "06001", "a b"), HiddenZones.add(keys, "a,b".replace(",", " ")))
        assertEquals(keys, HiddenZones.add(keys, " , "))
        assertEquals(keys, HiddenZones.decode(HiddenZones.encode(keys)))
        assertEquals(emptyList<String>(), HiddenZones.decode(null))
    }

    @Test fun `list is capped, oldest out`() {
        var keys = emptyList<String>()
        repeat(RadarSettings.MAX_HIDDEN + 20) { keys = HiddenZones.add(keys, "k$it") }
        assertEquals(RadarSettings.MAX_HIDDEN, keys.size)
        assertEquals("k20", keys.first())
        assertEquals("k${RadarSettings.MAX_HIDDEN + 19}", keys.last())
    }
}
