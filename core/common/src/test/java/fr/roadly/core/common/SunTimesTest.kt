package fr.roadly.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SunTimesTest {
    private val parisLat = 48.8566
    private val parisLon = 2.3522
    @Test fun `noon in june is day`() =
        assertFalse(SunTimes.isNight(parisLat, parisLon, Instant.parse("2026-06-21T12:00:00Z").toEpochMilli()))
    @Test fun `half past midnight in june is night`() =
        assertTrue(SunTimes.isNight(parisLat, parisLon, Instant.parse("2026-06-21T23:30:00Z").toEpochMilli()))
    @Test fun `six pm in december is night`() =
        assertTrue(SunTimes.isNight(parisLat, parisLon, Instant.parse("2026-12-21T18:00:00Z").toEpochMilli()))
}
