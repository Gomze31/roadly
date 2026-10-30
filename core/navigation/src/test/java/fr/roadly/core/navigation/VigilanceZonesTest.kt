package fr.roadly.core.navigation

import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilanceZonesTest {
    private fun p(east: Double) = GeoPoint(48.85, 2.35 + east / (111_320.0 * 0.658))
    private val route = Route(
        "r", RouteCosting.FASTEST, 10_000.0, 500.0, (0..20).map { p(it * 500.0) },
        listOf(Maneuver(ManeuverType.DEPART, "", null, 10_000.0, 500.0, 0, null), Maneuver(ManeuverType.ARRIVE, "", null, 0.0, 0.0, 20, null)),
        false, "fake",
    )
    private fun record(id: String, east: Double, north: Double = 0.0, limit: Int? = 90) =
        VigilanceZoneEntity(id, 48.85 + north / 111_320.0, 2.35 + east / (111_320.0 * 0.658), null, "Radar fixe", "D1", limit, 0L)

    @Test fun `lead distance follows the legal danger zone lengths`() {
        assertEquals(4_000.0, VigilanceZones.leadM(130), 0.0)
        assertEquals(4_000.0, VigilanceZones.leadM(110), 0.0)
        assertEquals(2_000.0, VigilanceZones.leadM(90), 0.0)
        assertEquals(2_000.0, VigilanceZones.leadM(null), 0.0)
        assertEquals(300.0, VigilanceZones.leadM(50), 0.0)
    }

    @Test fun `a record near the route becomes a zone ending 300 m after it and far records are ignored`() {
        val zones = VigilanceZones.build(route, listOf(record("a", 6_000.0, limit = 90), record("far", 6_000.0, north = 500.0)))
        assertEquals(1, zones.size)
        val z = zones.single()
        assertEquals(4_000.0, z.startAlongM, 15.0)
        assertEquals(6_300.0, z.endAlongM, 15.0)
        assertEquals(90, z.speedLimitKmh)
        assertTrue(z.contains(5_000.0))
        assertTrue(!z.contains(3_000.0))
    }

    @Test fun `overlapping zones merge and keep the lowest limit`() {
        val zones = VigilanceZones.build(route, listOf(record("a", 6_000.0, limit = 90), record("b", 7_000.0, limit = 70)))
        assertEquals(1, zones.size)
        assertEquals(70, zones.single().speedLimitKmh)
        assertEquals(7_300.0, zones.single().endAlongM, 15.0)
    }

    @Test fun `zone start never goes below zero and unknown limit stays null`() {
        val zones = VigilanceZones.build(route, listOf(record("a", 500.0, limit = null)))
        assertEquals(0.0, zones.single().startAlongM, 0.0)
        assertNull(zones.single().speedLimitKmh)
    }
}
