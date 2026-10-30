package fr.roadly.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationModeTest {
    @Test fun `map mode is 1 Hz and follow is 2 Hz`() {
        assertEquals(1000L, LocationMode.MAP.intervalMs)
        assertEquals(500L, LocationMode.FOLLOW.intervalMs)
        assertEquals(0L, LocationMode.OFF.intervalMs)
    }
}
