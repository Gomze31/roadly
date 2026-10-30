package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedSmootherTest {
    @Test fun `exponential smoothing with alpha 0,3`() {
        val s = SpeedSmoother()
        assertNull(s.update(null))
        assertEquals(50.0, s.update(50.0)!!, 1e-9)
        assertEquals(65.0, s.update(100.0)!!, 1e-9)        // 0,3·100 + 0,7·50
        assertEquals(65.0, s.update(null)!!, 1e-9)         // pas de mesure : valeur conservée
        assertEquals(45.5, s.update(0.0)!!, 1e-9)          // 0,3·0 + 0,7·65
    }
}
