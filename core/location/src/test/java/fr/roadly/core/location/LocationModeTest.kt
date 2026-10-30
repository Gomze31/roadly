package fr.roadly.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationModeTest {
    @Test fun `map mode is 1 Hz and follow is 2 Hz`() {
        assertEquals(1000L, LocationMode.MAP.intervalMs)
        assertEquals(500L, LocationMode.FOLLOW.intervalMs)
        assertEquals(0L, LocationMode.OFF.intervalMs)
    }

    @Test fun `guidance mode is 1 Hz`() = assertEquals(1000L, LocationMode.GUIDANCE.intervalMs)

    @Test fun `guidance overrides the screen mode, permission overrides everything`() {
        assertEquals(LocationMode.GUIDANCE, effectiveMode(LocationMode.OFF, guidanceActive = true, permission = LocationPermissionState.GRANTED))
        assertEquals(LocationMode.GUIDANCE, effectiveMode(LocationMode.FOLLOW, guidanceActive = true, permission = LocationPermissionState.GRANTED))
        assertEquals(LocationMode.MAP, effectiveMode(LocationMode.MAP, guidanceActive = false, permission = LocationPermissionState.GRANTED))
        assertEquals(LocationMode.OFF, effectiveMode(LocationMode.MAP, guidanceActive = true, permission = LocationPermissionState.DENIED))
    }

    @Test fun `fake records the guidance flag`() {
        val fake = fr.roadly.core.location.testing.FakeLocationRepository()
        fake.setGuidanceActive(true)
        assertEquals(true, fake.guidanceActive)
        fake.setGuidanceActive(false)
        assertEquals(false, fake.guidanceActive)
    }
}
