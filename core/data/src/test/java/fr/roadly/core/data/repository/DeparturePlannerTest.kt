package fr.roadly.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeparturePlannerTest {
    @Test fun `a future plan is active, a past one is not`() {
        val p = DeparturePlanner()
        p.set(DeparturePlan(10_000, arrive = false))
        assertEquals(DeparturePlan(10_000, false), p.active(5_000))
        assertNull(p.active(10_000))
        assertNull(p.active(20_000))
    }

    @Test fun `clear forgets the plan`() {
        val p = DeparturePlanner()
        p.set(DeparturePlan(10_000, arrive = true))
        p.clear()
        assertNull(p.plan.value)
        assertNull(p.active(0))
    }
}
