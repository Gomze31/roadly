package fr.roadly.core.data.repository

import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPreferencesTest {
    @Test fun `trip options win over saved ones until cleared`() = runTest {
        val trip = TripPreferences()
        val saved = flowOf(RoutePreferences(avoidHighways = true))
        assertEquals(RoutePreferences(avoidHighways = true), trip.effective(saved).first())
        trip.set(RoutePreferences(avoidTolls = true))
        assertEquals(RoutePreferences(avoidTolls = true), trip.effective(saved).first())
        trip.clear()
        assertEquals(RoutePreferences(avoidHighways = true), trip.effective(saved).first())
    }

    @Test fun `any tells whether an avoid option is on`() {
        assertFalse(RoutePreferences().any)
        assertTrue(RoutePreferences(avoidUnpaved = true).any)
    }
}
