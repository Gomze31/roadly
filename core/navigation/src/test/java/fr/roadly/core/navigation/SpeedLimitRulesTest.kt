package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedLimitRulesTest {
    @Test fun `rain lowers 130 110 and 90 only`() {
        assertEquals(110, SpeedLimitRules.rainReduced(130))
        assertEquals(100, SpeedLimitRules.rainReduced(110))
        assertEquals(80, SpeedLimitRules.rainReduced(90))
        assertEquals(80, SpeedLimitRules.rainReduced(80))
        assertEquals(50, SpeedLimitRules.rainReduced(50))
    }

    @Test fun `vigilance phrase names the zone and the limit when known`() {
        assertEquals("Zone de danger, limitation à 90.", SpokenPhrases.vigilanceZone(90))
        assertEquals("Zone de danger.", SpokenPhrases.vigilanceZone(null))
    }
}
