package fr.roadly.core.assistant.settings

import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageMeterTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val day1 = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day2 = day1 + 24 * 3_600_000L

    @Test fun `usage accumulates per day and the daily cap blocks new sessions`() = runTest {
        val repo = FakeAssistantSettingsRepository()
        repo.setDailyCap(1)
        val meter = UsageMeter(repo)
        assertTrue(meter.canStart(day1, zone))
        meter.record(day1, zone, tokens = 500, micSeconds = 40)
        meter.record(day1, zone, tokens = 100, micSeconds = 30)
        assertEquals(AssistantUsage("2026-10-31", 600, 70), repo.currentUsage)
        assertFalse(meter.canStart(day1, zone))
        assertTrue("nouveau jour, compteur remis à zéro", meter.canStart(day2, zone))
        meter.record(day2, zone, tokens = 1, micSeconds = 1)
        assertEquals(AssistantUsage("2026-11-01", 1, 1), repo.currentUsage)
    }
}
