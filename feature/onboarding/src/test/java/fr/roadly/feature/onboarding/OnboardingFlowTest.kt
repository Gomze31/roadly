package fr.roadly.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingFlowTest {
    @Test fun `seven steps when the notification permission exists, ending with thanks`() {
        val steps = OnboardingFlow.steps(notificationPermissionExists = true)
        assertEquals(7, steps.size)
        assertEquals(OnboardingStep.WELCOME, steps.first())
        assertEquals(OnboardingStep.THANKS, steps.last())
    }

    @Test fun `the notification step is dropped on Android 12 and older`() {
        val steps = OnboardingFlow.steps(notificationPermissionExists = false)
        assertEquals(6, steps.size)
        assertFalse(OnboardingStep.NOTIFICATIONS in steps)
        assertEquals(OnboardingStep.VEHICLE, OnboardingFlow.next(steps, OnboardingStep.LOCATION))
    }

    @Test fun `next walks the steps in order and ends after the last one`() {
        val steps = OnboardingFlow.steps(true)
        assertEquals(OnboardingStep.LOCATION, OnboardingFlow.next(steps, OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.THANKS, OnboardingFlow.next(steps, OnboardingStep.ROUTE))
        assertNull(OnboardingFlow.next(steps, OnboardingStep.THANKS))
        assertNull(OnboardingFlow.previous(steps, OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.ALERTS, OnboardingFlow.previous(steps, OnboardingStep.ROUTE))
    }

    @Test fun `progress counts the displayed step`() {
        val steps = OnboardingFlow.steps(true)
        assertEquals(1, OnboardingFlow.filledSegments(steps, OnboardingStep.WELCOME))
        assertEquals(6, OnboardingFlow.filledSegments(steps, OnboardingStep.ROUTE))
        assertEquals(7, OnboardingFlow.filledSegments(steps, OnboardingStep.THANKS))
    }

    @Test fun `consumption accepts french and dotted decimals within plausible bounds only`() {
        assertEquals(6.5, OnboardingFlow.parseConsumption("6,5")!!, 1e-9)
        assertEquals(6.5, OnboardingFlow.parseConsumption(" 6.5 ")!!, 1e-9)
        assertEquals(17.0, OnboardingFlow.parseConsumption("17")!!, 1e-9)
        assertNull(OnboardingFlow.parseConsumption(""))
        assertNull(OnboardingFlow.parseConsumption("abc"))
        assertNull(OnboardingFlow.parseConsumption("0"))
        assertNull(OnboardingFlow.parseConsumption("-3"))
        assertNull(OnboardingFlow.parseConsumption("75"))
    }
}
