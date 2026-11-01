package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenPhrasesTest {
    @Test fun `distances are rounded for speech`() {
        assertEquals("10 mètres", SpokenPhrases.distance(8.0))
        assertEquals("50 mètres", SpokenPhrases.distance(47.0))
        assertEquals("70 mètres", SpokenPhrases.distance(70.0))
        assertEquals("250 mètres", SpokenPhrases.distance(260.0))
        assertEquals("1 kilomètre", SpokenPhrases.distance(980.0))
        assertEquals("1 kilomètre", SpokenPhrases.distance(1000.0))
        assertEquals("1,5 kilomètre", SpokenPhrases.distance(1540.0))
        assertEquals("2 kilomètres", SpokenPhrases.distance(2000.0))
        assertEquals("12,3 kilomètres", SpokenPhrases.distance(12_340.0))
    }

    @Test fun `in distance lowercases the instruction`() =
        assertEquals("Dans 300 mètres, tournez à droite.", SpokenPhrases.inDistance(300.0, "Tournez à droite."))

    @Test fun `430 m is spoken 450 meters like the banner shows 450 m`() {
        assertEquals("450 mètres", SpokenPhrases.distance(430.0))
        assertEquals("450 m", fr.roadly.core.common.Formatters.maneuverDistance(430.0))
    }
}
