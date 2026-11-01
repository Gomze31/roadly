package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerInterpolationTest {
    private val a = MarkerPose(48.0, 2.0, 350.0)
    private val b = MarkerPose(48.001, 2.001, 10.0)   // ~134 m plus loin

    @Test fun `halfway is the midpoint and the arrow turns the short way`() {
        val m = MarkerInterpolation.lerp(a, b, 0.5)
        assertEquals(48.0005, m.lat, 1e-12)
        assertEquals(2.0005, m.lon, 1e-12)
        assertEquals(0.0, m.bearingDeg, 1e-9)
    }

    @Test fun `the fraction is clamped to the two fixes`() {
        assertEquals(a, MarkerInterpolation.lerp(a, b, -1.0))
        val end = MarkerInterpolation.lerp(a, b, 2.0)
        assertEquals(b.lat, end.lat, 1e-12); assertEquals(b.lon, end.lon, 1e-12); assertEquals(b.bearingDeg, end.bearingDeg, 1e-9)
    }

    @Test fun `a jump of more than 200 m snaps instead of gliding across the map`() {
        assertTrue(MarkerInterpolation.shouldAnimate(a, b, 1_000))
        assertFalse(MarkerInterpolation.shouldAnimate(a, MarkerPose(48.01, 2.0, 0.0), 1_000))  // ~1,1 km
        assertFalse(MarkerInterpolation.shouldAnimate(a, b, 0))
    }

    @Test fun `a fix without heading points north, headings are normalised`() {
        assertEquals(0.0, MarkerInterpolation.pose(LocationFix(GeoPoint(48.0, 2.0), null, null, 5f, 0L)).bearingDeg, 1e-9)
        assertEquals(270.0, MarkerInterpolation.pose(LocationFix(GeoPoint(48.0, 2.0), -90f, null, 5f, 0L)).bearingDeg, 1e-9)
    }
}
