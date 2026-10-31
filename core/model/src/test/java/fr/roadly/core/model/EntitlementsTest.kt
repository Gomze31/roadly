package fr.roadly.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    @Test fun `without a store everything is included, even plus features`() {
        val e = Entitlements.NO_STORE
        assertTrue(e.allIncluded)
        assertTrue(e.includes(Feature.REPLAY))
        assertFalse(e.locked(Feature.COCKPIT_TINT))
    }

    @Test fun `with a store, free tier locks only plus features`() {
        val e = Entitlements(Tier.FREE, storeConfigured = true)
        assertTrue(e.locked(Feature.REPLAY))
        assertTrue(e.locked(Feature.VEHICLE_ADVANCED))
        assertTrue(e.includes(Feature.NAVIGATION))
        assertTrue(e.includes(Feature.FUEL_PRICES))
        assertFalse(e.allIncluded)
    }

    @Test fun `plus tier includes everything`() {
        assertTrue(Entitlements(Tier.PLUS, storeConfigured = true).allIncluded)
    }
}
