package fr.roadly.feature.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarTemplatesTest {
    @Test
    fun `niveau 1 - gabarits de navigation sans en-tete, bandeau de carte ni alerte`() {
        val c = CarTemplates.level(1)
        assertEquals(TemplateTier.NAVIGATION, c.tier)
        assertFalse(c.header)
        assertFalse(c.messageHeader)
        assertFalse(c.navigationMapStrip)
        assertFalse(c.listMapStrip)
        assertFalse(c.contentLimit)
        assertFalse(c.alerts)
    }

    @Test
    fun `paliers intermediaires`() {
        CarTemplates.level(2).let { assertTrue(it.navigationMapStrip); assertTrue(it.contentLimit); assertFalse(it.listMapStrip) }
        CarTemplates.level(4).let { assertTrue(it.listMapStrip); assertFalse(it.header) }
        CarTemplates.level(5).let { assertTrue(it.header); assertTrue(it.alerts); assertFalse(it.messageHeader) }
        CarTemplates.level(6).let { assertEquals(TemplateTier.NAVIGATION, it.tier) }
    }

    @Test
    fun `niveau 7 et plus - carte et contenu, tout disponible`() {
        for (level in listOf(7, 8, 12)) {
            val c = CarTemplates.level(level)
            assertEquals(TemplateTier.MAP_WITH_CONTENT, c.tier)
            assertTrue(c.header && c.messageHeader && c.navigationMapStrip && c.listMapStrip && c.contentLimit && c.alerts)
        }
    }

    @Test
    fun `le manifeste vise tous les hotes`() {
        assertEquals(1, CarTemplates.MIN_CAR_API_LEVEL)
    }
}
