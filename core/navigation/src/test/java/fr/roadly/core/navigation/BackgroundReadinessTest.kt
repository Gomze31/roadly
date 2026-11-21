package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundReadinessTest {
    @Test fun `nothing to report when notifications are on and the app is exempt from battery optimisation`() {
        assertEquals(emptyList<BackgroundIssue>(), BackgroundReadiness.issues(notificationsEnabled = true, batteryExempt = true, backgroundRestricted = false))
    }

    @Test fun `state read on the HONOR before the field test - notifications denied and battery optimised are both reported`() {
        assertEquals(
            listOf(BackgroundIssue.BATTERY_OPTIMIZED, BackgroundIssue.NOTIFICATIONS_OFF),
            BackgroundReadiness.issues(notificationsEnabled = false, batteryExempt = false, backgroundRestricted = false),
        )
    }

    @Test fun `a background restriction comes first and replaces the battery message`() {
        assertEquals(
            listOf(BackgroundIssue.BACKGROUND_RESTRICTED),
            BackgroundReadiness.issues(notificationsEnabled = true, batteryExempt = false, backgroundRestricted = true),
        )
        // Exemptée de l'optimisation mais restreinte en arrière-plan : la restriction reste signalée.
        assertEquals(
            listOf(BackgroundIssue.BACKGROUND_RESTRICTED, BackgroundIssue.NOTIFICATIONS_OFF),
            BackgroundReadiness.issues(notificationsEnabled = false, batteryExempt = true, backgroundRestricted = true),
        )
    }
}
