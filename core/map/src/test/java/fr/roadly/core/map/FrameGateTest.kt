package fr.roadly.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGateTest {
    /** Une seconde d'images à [displayHz] (horodatage en ms entières, comme `ValueAnimator`) ; renvoie le nombre d'écritures. */
    private fun writesInOneSecond(displayHz: Int, fps: Int): Int {
        val gate = FrameGate(FrameGate.intervalFor(fps))
        return (0..displayHz).count { k -> gate.pass(k * 1_000L / displayHz, last = k == displayHz) }
    }

    @Test fun `a 120 Hz display is capped to the render fps`() { // C3 : pas plus d'écritures que d'images rendues
        assertTrue(writesInOneSecond(displayHz = 120, fps = 60) <= 61)
        assertTrue(writesInOneSecond(displayHz = 120, fps = 30) <= 31)
        assertTrue("90 Hz plafonné à 60", writesInOneSecond(displayHz = 90, fps = 60) <= 61)
    }

    @Test fun `a 60 Hz display at 60 fps drops no frame`() {
        assertEquals(61, writesInOneSecond(displayHz = 60, fps = 60))
    }

    @Test fun `the final frame always passes, and reset starts over`() {
        val gate = FrameGate(FrameGate.intervalFor(60))
        assertTrue(gate.pass(0))
        assertFalse(gate.pass(5))
        assertTrue("dernière image gardée", gate.pass(8, last = true))
        gate.reset()
        assertTrue("nouvelle animation : première image écrite", gate.pass(2))
    }

    @Test fun `the interval follows the fps, never divided by zero`() {
        assertEquals(16L, FrameGate.intervalFor(60))
        assertEquals(33L, FrameGate.intervalFor(30))
        assertEquals(1_000L, FrameGate.intervalFor(0))
    }
}
