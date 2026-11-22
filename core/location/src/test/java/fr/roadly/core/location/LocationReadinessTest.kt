package fr.roadly.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationReadinessTest {
    @Test fun `precise permission and location switched on - nothing to report`() {
        assertNull(LocationReadiness.issue(fineGranted = true, coarseGranted = true, servicesEnabled = true))
    }

    @Test fun `location switched off on the phone is reported instead of waiting for a fix forever`() {
        assertEquals(LocationIssue.SERVICES_OFF, LocationReadiness.issue(fineGranted = true, coarseGranted = true, servicesEnabled = false))
        // Coupée et approximative à la fois : l'interrupteur d'abord, la précision n'a pas de sens sans position.
        assertEquals(LocationIssue.SERVICES_OFF, LocationReadiness.issue(fineGranted = false, coarseGranted = true, servicesEnabled = false))
    }

    @Test fun `approximate-only permission is reported - guidance needs 30 m or better`() {
        assertEquals(LocationIssue.APPROXIMATE_ONLY, LocationReadiness.issue(fineGranted = false, coarseGranted = true, servicesEnabled = true))
    }

    @Test fun `recovery - the issue disappears as soon as the switch is back on or precise location is granted`() {
        assertEquals(LocationIssue.SERVICES_OFF, LocationReadiness.issue(true, true, servicesEnabled = false))
        assertNull(LocationReadiness.issue(true, true, servicesEnabled = true))
        assertEquals(LocationIssue.APPROXIMATE_ONLY, LocationReadiness.issue(fineGranted = false, coarseGranted = true, servicesEnabled = true))
        assertNull(LocationReadiness.issue(fineGranted = true, coarseGranted = true, servicesEnabled = true))
    }

    @Test fun `no permission at all is left to the permission flow`() {
        assertNull(LocationReadiness.issue(fineGranted = false, coarseGranted = false, servicesEnabled = true))
        assertNull(LocationReadiness.issue(fineGranted = false, coarseGranted = false, servicesEnabled = false))
    }
}
