package fr.roadly.core.data.repository

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripCodecTest {
    @Test fun `points round trip through the csv codec`() {
        val pts = listOf(TripPoint(0, GeoPoint(43.6045, 1.444), 0.0), TripPoint(12_000, GeoPoint(43.60512, 1.44501), 36.4), TripPoint(30_000, GeoPoint(43.606, 1.446), null))
        val back = TripPointsCodec.decode(TripPointsCodec.encode(pts))
        assertEquals(3, back.size)
        assertEquals(12_000L, back[1].tMs)
        assertEquals(43.60512, back[1].point.lat, 1e-5)
        assertEquals(36.4, back[1].speedKmh!!, 1e-9)
        assertNull(back[2].speedKmh)
    }

    @Test fun `stats sum only trips since the given instant`() {
        fun trip(start: Long, km: Double, cost: Double?) = Trip(0, start, start + 1, "x", km * 1000, 600, 50.0, cost, true, emptyList())
        val s = listOf(trip(100, 10.0, 2.0), trip(200, 5.0, null), trip(50, 99.0, 9.0)).statsSince(100)
        assertEquals(2, s.trips)
        assertEquals(15_000.0, s.distanceM, 0.0)
        assertEquals(1200L, s.durationS)
        assertEquals(2.0, s.costEur, 0.0)
    }
}
