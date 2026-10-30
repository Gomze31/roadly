package fr.roadly.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitFormattersTest {
    @Test fun duration_under_an_hour_is_minutes() {
        assertEquals(SplitValue("6", "min"), splitDuration(6 * 60.0))
        assertEquals(SplitValue("0", "min"), splitDuration(20.0))
        assertEquals(SplitValue("59", "min"), splitDuration(59 * 60.0 + 20))
    }

    @Test fun duration_over_an_hour_keeps_the_full_text() {
        assertEquals(SplitValue("1 h 12", "restant"), splitDuration(72 * 60.0))
    }

    @Test fun distance_units_follow_the_formatter_thresholds() {
        assertEquals(SplitValue("850", "m"), splitDistance(846.0))
        assertEquals(SplitValue("2,1", "km"), splitDistance(2_140.0))
        assertEquals(SplitValue("98", "km"), splitDistance(98_400.0))
    }
}
