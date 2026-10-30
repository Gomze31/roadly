package fr.roadly.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormattersTest {
    @Test fun `meters under 1 km rounded to 10 m`() = assertEquals("850 m", Formatters.distance(846.0))
    @Test fun `km under 10 with one decimal and french comma`() = assertEquals("1,2 km", Formatters.distance(1234.0))
    @Test fun `km over 10 without decimal`() = assertEquals("12 km", Formatters.distance(12_400.0))
    @Test fun `duration under one hour in minutes`() = assertEquals("12 min", Formatters.duration(720.0))
    @Test fun `duration over one hour padded`() = assertEquals("1 h 05", Formatters.duration(3900.0))
    @Test fun `euros with french format`() = assertEquals("4,20 €", Formatters.euros(4.2))
    @Test fun `clock time in zone`() =
        assertEquals("14:32", Formatters.clockTime(1_782_131_520_000L, ZoneId.of("Europe/Paris"))) // 2026-06-22T12:32:00Z = 14:32 à Paris
}
