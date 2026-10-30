package fr.roadly.core.designsystem

import fr.roadly.core.designsystem.component.HorizonScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizonScaleTest {
    @Test fun zero_and_negative_are_at_start() {
        assertEquals(0f, HorizonScale.position(0.0), 0f)
        assertEquals(0f, HorizonScale.position(-50.0), 0f)
    }

    @Test fun range_end_is_at_one_and_clamped_beyond() {
        assertEquals(1f, HorizonScale.position(HorizonScale.RANGE_M), 1e-6f)
        assertEquals(1f, HorizonScale.position(50_000.0), 1e-6f)
    }

    @Test fun scale_is_monotonic_and_compresses_far_distances() {
        val near = HorizonScale.position(500.0)
        val mid = HorizonScale.position(2_500.0)
        assertTrue(near < mid)
        // Les premiers 500 m occupent plus du tiers du ruban : lisibilité des événements proches.
        assertTrue("near=$near", near > 0.33f)
        // Entre 2,5 km et 5 km, il reste moins d'un quart du ruban.
        assertTrue("mid=$mid", 1f - mid < 0.25f)
    }
}
