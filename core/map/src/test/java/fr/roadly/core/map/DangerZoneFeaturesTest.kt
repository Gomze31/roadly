package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.RouteHazard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerZoneFeaturesTest {
    // Ligne droite vers l'est, un point tous les ~1 000 m (latitude 45°).
    private val geometry = (0..10).map { GeoPoint(45.0, 4.0 + it * 0.0127) }

    @Test fun `zone is cut out of the route with interpolated ends`() {
        val cumulative = DoubleArray(geometry.size).also { c -> for (i in 1 until geometry.size) c[i] = c[i - 1] + geometry[i - 1].distanceTo(geometry[i]) }
        val line = DangerZoneFeatures.slice(geometry, cumulative, 1_500.0, 3_200.0)
        assertEquals(1_500.0, cumulative.let { geometry[0].distanceTo(line.first()) }, 2.0)
        assertEquals(3_200.0, geometry[0].distanceTo(line.last()), 2.0)
        // Sommets intermédiaires conservés (2e et 3e points du tracé).
        assertEquals(4, line.size)
    }

    @Test fun `geojson marks inside and ahead`() {
        val json = DangerZoneFeatures.build(geometry, listOf(DangerZoneSpan(0.0, 800.0, true), DangerZoneSpan(4_000.0, 6_000.0, false)))
        assertEquals(2, Regex("\"LineString\"").findAll(json).count())
        assertTrue(json.contains("\"state\":\"inside\""))
        assertTrue(json.contains("\"state\":\"ahead\""))
        assertEquals(DangerZoneFeatures.EMPTY, DangerZoneFeatures.build(geometry, emptyList()))
        assertEquals(DangerZoneFeatures.EMPTY, DangerZoneFeatures.build(geometry, listOf(DangerZoneSpan(20_000.0, 21_000.0, false))))
    }

    @Test fun `spans keep only zones ahead and the crossed one, drop hidden ones`() {
        val hazards = listOf(
            RouteHazard(HazardKind.VIGILANCE_ZONE, 0.0, 900.0, key = "passed"),
            RouteHazard(HazardKind.VIGILANCE_ZONE, 1_000.0, 3_000.0, key = "inside"),
            RouteHazard(HazardKind.CURVE, 2_000.0, 2_100.0),
            RouteHazard(HazardKind.VIGILANCE_ZONE, 5_000.0, 7_000.0, key = "ahead"),
            RouteHazard(HazardKind.VIGILANCE_ZONE, 6_000.0, 8_000.0, key = "hidden"),
            RouteHazard(HazardKind.VIGILANCE_ZONE, 40_000.0, 42_000.0, key = "far"),
        )
        val spans = DangerZoneSpans.from(hazards, alongM = 1_200.0, activeKey = "inside", hidden = setOf("hidden"))
        assertEquals(listOf(DangerZoneSpan(1_000.0, 3_000.0, true), DangerZoneSpan(5_000.0, 7_000.0, false)), spans)
    }
}
