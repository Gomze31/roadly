package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteIdentityTest {
    @Test fun `share, time above 90 and gantries come from the real profile only`() {
        val a = RouteAttributes(
            routeId = "r", totalM = 10_000.0,
            speedLimits = listOf(
                SpeedLimitSpan(0.0, 2_000.0, 50),
                SpeedLimitSpan(2_000.0, 6_600.0, 130), // 4,6 km à 130 → 127 s
                SpeedLimitSpan(7_000.0, 8_000.0, 110), // 1 km à 110 → 33 s
            ),
            tollGantries = listOf(TollGantry(3_000.0, "A1", "fr"), TollGantry(5_000.0, "A1", "fr")),
        )
        val id = RouteIdentity.from(a)
        assertEquals(0.76, id.knownLimitShare, 1e-9)
        assertEquals(76, id.knownLimitPercent)
        assertEquals(4_600.0 / (130 / 3.6) + 1_000.0 / (110 / 3.6), id.fastRoadSeconds!!, 1e-6)
        assertEquals(3, id.fastRoadMinutes)
        assertEquals(0.56, id.fastRoadShare, 1e-9)
        assertEquals(2, id.tollGantries)
    }

    @Test fun `90 exactly is not fast, and no known limit means unknown time`() {
        val slow = RouteIdentity.from(RouteAttributes("r", 1_000.0, listOf(SpeedLimitSpan(0.0, 1_000.0, 90)), emptyList()))
        assertEquals(0, slow.fastRoadMinutes)
        val unknown = RouteIdentity.from(RouteAttributes("r", 1_000.0, emptyList(), emptyList()))
        assertNull(unknown.fastRoadSeconds)
        assertNull(unknown.fastRoadMinutes)
        assertEquals(0, unknown.knownLimitPercent)
    }

    @Test fun `a short fast stretch still shows at least one minute`() {
        val id = RouteIdentity.from(RouteAttributes("r", 1_000.0, listOf(SpeedLimitSpan(0.0, 300.0, 110)), emptyList()))
        assertEquals(1, id.fastRoadMinutes)
    }
}
