package fr.roadly.feature.navigation

import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.RouteHazard
import org.junit.Assert.assertEquals
import org.junit.Test

class HazardsAheadTest {
    private val curve = RouteHazard(HazardKind.CURVE, 1_200.0, 1_260.0, angleDeg = 70.0)
    private val zone = RouteHazard(HazardKind.VIGILANCE_ZONE, 500.0, 2_800.0, speedLimitKmh = 90)
    private val far = RouteHazard(HazardKind.CURVE, 9_000.0, 9_050.0, angleDeg = 60.0)
    private val passed = RouteHazard(HazardKind.CURVE, 100.0, 150.0, angleDeg = 60.0)

    @Test fun `keeps events ahead or in progress within 5 km, sorted, with distance to their start`() {
        val ahead = hazardsAhead(listOf(far, curve, zone, passed), alongM = 1_000.0)
        assertEquals(listOf(HazardKind.VIGILANCE_ZONE, HazardKind.CURVE), ahead.map { it.kind })
        assertEquals(0.0, ahead[0].distanceM, 0.0)   // zone en cours : distance 0
        assertEquals(200.0, ahead[1].distanceM, 0.0)
        assertEquals("200 m", ahead[1].distanceText)
        assertEquals(90, ahead[0].limitKmh)
    }
}
