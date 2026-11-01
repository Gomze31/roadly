package fr.roadly.core.scooter

import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.ScooterRouteRequest
import fr.roadly.core.model.SuspensionType
import fr.roadly.core.model.TireType
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.routing.ScooterRoutingProfile
import fr.roadly.core.scooter.routing.SurfaceSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingProfileTest {
    private val g2 = ScooterPresets.NINEBOT_MAX_G2
    private fun opts(goal: ScooterRouteGoal, prefs: ScooterRoutePreferences = ScooterRoutePreferences(preferCycleways = false, avoidBusyRoads = false, avoidUnpaved = false, avoidRoughSurfaces = false)) =
        ScooterRoutingProfile.costingOptions(ScooterRouteRequest(g2, prefs, goal))

    @Test fun goalsChangeTheCosting() {
        assertTrue((opts(ScooterRouteGoal.SAFEST)["use_roads"] as Double) < (opts(ScooterRouteGoal.FASTEST)["use_roads"] as Double))
        assertTrue((opts(ScooterRouteGoal.COMFORT)["use_hills"] as Double) < (opts(ScooterRouteGoal.FASTEST)["use_hills"] as Double))
        assertEquals(true, opts(ScooterRouteGoal.SHORTEST)["shortest"])
        assertFalse(opts(ScooterRouteGoal.FASTEST).containsKey("shortest"))
    }

    @Test fun preferencesTightenWeights() {
        val p = ScooterRoutePreferences(goal = ScooterRouteGoal.FASTEST, preferCycleways = true, avoidBusyRoads = true, avoidSteepHills = true, avoidUnpaved = true, avoidRoughSurfaces = true)
        val o = ScooterRoutingProfile.costingOptions(ScooterRouteRequest(g2, p, ScooterRouteGoal.FASTEST))
        assertEquals(0.1, o["use_roads"] as Double, 1e-9)
        assertEquals(0.05, o["use_hills"] as Double, 1e-9)
        assertTrue((o["avoid_bad_surfaces"] as Double) in 0.75..0.95)
    }

    @Test fun speedCappedByFrenchRuleAndStepsDiscouraged() {
        val o = ScooterRoutingProfile.costingOptions(ScooterRouteRequest(g2.copy(maxSpeedKmh = 35.0, cruiseSpeedKmh = 32.0), ScooterRoutePreferences()))
        assertEquals(25.0, o["cycling_speed"] as Double, 1e-9)
        assertEquals(ScooterRoutingProfile.STEPS_FACTOR, o["steps_factor"] as Double, 1e-9)
    }

    @Test fun smallSolidWheelsAreMoreSensitive() {
        assertEquals(SurfaceSensitivity.LOW, ScooterRoutingProfile.sensitivity(g2))
        val small = g2.copy(tireType = TireType.SOLID, suspension = SuspensionType.NONE, wheelSizeInch = 8.0)
        assertEquals(SurfaceSensitivity.HIGH, ScooterRoutingProfile.sensitivity(small))
        assertEquals("road", ScooterRoutingProfile.costingOptions(ScooterRouteRequest(small, ScooterRoutePreferences()))["bicycle_type"])
    }

    @Test fun variantsStartWithSelectedGoal() {
        assertEquals(listOf(ScooterRouteGoal.SHORTEST, ScooterRouteGoal.SAFEST, ScooterRouteGoal.FASTEST), ScooterRoutingProfile.variantGoals(ScooterRouteGoal.SHORTEST))
    }
}
