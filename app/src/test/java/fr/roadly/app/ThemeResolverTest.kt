package fr.roadly.app

import fr.roadly.core.data.repository.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeResolverTest {
    @Test fun `system follows system flag`() { assertEquals(true, ThemeResolver.isDark(ThemeMode.SYSTEM, systemDark = true, lat = null, lon = null, nowMs = 0L)); assertEquals(false, ThemeResolver.isDark(ThemeMode.SYSTEM, false, null, null, 0L)) }
    @Test fun `forced modes ignore everything`() { assertEquals(true, ThemeResolver.isDark(ThemeMode.DARK, false, null, null, 0L)); assertEquals(false, ThemeResolver.isDark(ThemeMode.LIGHT, true, null, null, 0L)) }
    @Test fun `auto solar without position falls back to 7h-20h local`() {
        val noon = java.time.ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val late = java.time.ZonedDateTime.of(2026, 6, 1, 22, 0, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(false, ThemeResolver.isDark(ThemeMode.AUTO_SOLAR, false, null, null, noon))
        assertEquals(true, ThemeResolver.isDark(ThemeMode.AUTO_SOLAR, false, null, null, late))
    }
    @Test fun `auto solar with position uses sun elevation`() {
        val midnightParis = java.time.Instant.parse("2026-06-21T23:30:00Z").toEpochMilli()
        assertEquals(true, ThemeResolver.isDark(ThemeMode.AUTO_SOLAR, false, 48.85, 2.35, midnightParis))
    }
}
