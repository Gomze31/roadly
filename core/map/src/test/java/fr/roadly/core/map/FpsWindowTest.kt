package fr.roadly.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsWindowTest {
    @Test fun `the average is given once per window, then starts over`() {
        val w = FpsWindow(windowMs = 5_000)
        assertNull(w.add(60.0, 0)); assertNull(w.add(50.0, 2_000)); assertNull(w.add(40.0, 4_999))
        assertEquals(47.5, w.add(40.0, 5_000)!!, 1e-9)
        assertNull(w.add(30.0, 6_000))
        assertEquals(30.0, w.add(30.0, 10_000)!!, 1e-9)
    }
}
