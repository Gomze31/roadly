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

    @Test fun `maneuver distance uses the voice rounding`() {
        assertEquals("450 m", Formatters.maneuverDistance(430.0))
        assertEquals("850 m", Formatters.maneuverDistance(846.0))
        assertEquals("50 m", Formatters.maneuverDistance(47.0))
        assertEquals("10 m", Formatters.maneuverDistance(8.0))
        assertEquals("1,0 km", Formatters.maneuverDistance(980.0))
        assertEquals("1,2 km", Formatters.maneuverDistance(1234.0))
        assertEquals("12,3 km", Formatters.maneuverDistance(12_340.0))
    }

    @Test fun `rounding table - 10 m under 100 m, 50 m under 1 km, 100 m beyond`() {
        assertEquals(70, Formatters.roundManeuverMeters(72.0))
        assertEquals(450, Formatters.roundManeuverMeters(430.0))
        assertEquals(1_500, Formatters.roundManeuverMeters(1_540.0))
    }
}
