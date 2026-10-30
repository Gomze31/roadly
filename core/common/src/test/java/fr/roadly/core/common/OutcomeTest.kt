package fr.roadly.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTest {
    @Test fun `map transforms success`() = assertEquals(4, Outcome.Success(2).map { it * 2 }.getOrNull())
    @Test fun `map keeps failure`() {
        val f: Outcome<Int> = Outcome.Failure(AppError.Network)
        assertNull(f.map { it * 2 }.getOrNull())
        assertEquals(AppError.Network, f.errorOrNull())
    }
}
