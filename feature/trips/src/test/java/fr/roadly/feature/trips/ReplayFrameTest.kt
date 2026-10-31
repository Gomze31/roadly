package fr.roadly.feature.trips

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.TripPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayFrameTest {
    private val pts = listOf(TripPoint(0, GeoPoint(0.0, 0.0), 10.0), TripPoint(10_000, GeoPoint(0.0, 0.001), 20.0), TripPoint(20_000, GeoPoint(0.001, 0.001), 30.0))

    @Test fun `frame interpolates between the surrounding points and keeps the recorded speed`() {
        val f = ReplayViewModel.frameAt(pts, 5_000)!!
        assertEquals(0.0005, f.point.lon, 1e-9)
        assertEquals(0.0, f.point.lat, 1e-9)
        assertEquals(10.0, f.speedKmh!!, 1e-9)
        assertEquals(90f, f.bearingDeg!!, 0.5f)
    }

    @Test fun `last point is held after the end and empty trips give no frame`() {
        assertEquals(0.001, ReplayViewModel.frameAt(pts, 99_000)!!.point.lat, 1e-9)
        assertNull(ReplayViewModel.frameAt(emptyList(), 0))
    }
}
