package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophiesTest {
    private fun trip(km: Double) = Trip(0, 0, 1, "x", km * 1000, 60, 50.0, null, true, emptyList())

    @Test fun `trophies unlock from cumulative km, trip count and longest trip`() {
        val t = Trophies.compute(listOf(trip(60.0), trip(45.0))).associateBy { it.id }
        assertTrue(t.getValue(Trophies.TRIPS_1).unlocked)
        assertTrue(t.getValue(Trophies.KM_100).unlocked)
        assertTrue(t.getValue(Trophies.LONGEST_50).unlocked)
        assertFalse(t.getValue(Trophies.TRIPS_10).unlocked)
        assertEquals(2.0, t.getValue(Trophies.TRIPS_10).progress, 0.0)
        assertFalse(t.getValue(Trophies.LONGEST_200).unlocked)
        assertEquals(8, t.size)
    }

    @Test fun `no trips means nothing unlocked`() {
        assertTrue(Trophies.compute(emptyList()).none { it.unlocked })
    }
}
