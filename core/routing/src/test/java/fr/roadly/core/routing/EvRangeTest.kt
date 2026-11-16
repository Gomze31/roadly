package fr.roadly.core.routing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvRangeTest {
    private fun route(km: Double, kmh: Double) = Route(
        "r", RouteCosting.FASTEST, km * 1000, km / kmh * 3600, listOf(GeoPoint(43.6, 1.4), GeoPoint(43.2, 2.3)),
        listOf(Maneuver(ManeuverType.CONTINUE, "x", null, km * 1000, km / kmh * 3600, 0, null)), false, "fake",
    )

    @Test fun `motorway costs more than a national road and the level follows the thresholds`() {
        // 200 km, 18 kWh/100 km, 60 kWh, départ à 80 % (48 kWh).
        val motorway = EvRange.arrival(route(200.0, 130.0), 18.0, 60.0, 80)!!
        val national = EvRange.arrival(route(200.0, 80.0), 18.0, 60.0, 80)!!
        assertEquals(200 * 0.18 * 1.45, motorway.usedKwh, 0.01) // facteur 1,45 à 130 km/h
        assertEquals(EvArrival.Level.CRITICAL, motorway.level)   // 48 − 52,2 kWh → −7 %
        assertEquals(-7, motorway.arrivalPct)
        assertEquals(EvArrival.Level.OK, national.level)
        assertEquals(EvArrival.Level.LOW, EvRange.arrival(route(100.0, 130.0), 18.0, 60.0, 60)!!.level) // 36 − 26,1 → 16 %
    }

    @Test fun `unknown or absurd inputs give no estimate`() {
        assertNull(EvRange.arrival(route(10.0, 50.0), 0.0, 60.0, 80))
        assertNull(EvRange.arrival(route(10.0, 50.0), 18.0, 0.0, 80))
        assertNull(EvRange.arrival(route(10.0, 50.0), 18.0, 60.0, 120))
    }
}
