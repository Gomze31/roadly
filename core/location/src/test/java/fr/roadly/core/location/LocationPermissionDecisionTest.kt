package fr.roadly.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionDecisionTest {
    @Test fun `denied forever survives a denied refresh`() =
        assertEquals(LocationPermissionState.DENIED_FOREVER, nextPermissionState(LocationPermissionState.DENIED_FOREVER, LocationPermissionState.DENIED))

    @Test fun `denied forever is lifted by granted`() =
        assertEquals(LocationPermissionState.GRANTED, nextPermissionState(LocationPermissionState.DENIED_FOREVER, LocationPermissionState.GRANTED))

    @Test fun `unknown becomes denied`() =
        assertEquals(LocationPermissionState.DENIED, nextPermissionState(LocationPermissionState.UNKNOWN, LocationPermissionState.DENIED))

    @Test fun `denied becomes granted`() =
        assertEquals(LocationPermissionState.GRANTED, nextPermissionState(LocationPermissionState.DENIED, LocationPermissionState.GRANTED))
}
