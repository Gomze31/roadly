package fr.roadly.core.scooter

import fr.roadly.core.scooter.Fixtures.edge
import fr.roadly.core.scooter.regulation.AreaType
import fr.roadly.core.scooter.regulation.FranceEdpmRegulation
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.segment.DataSource
import fr.roadly.core.scooter.segment.ScooterSegmentClassifier
import fr.roadly.core.scooter.segment.SurfaceQuality
import fr.roadly.core.scooter.segment.WayCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {
    private fun classify(edges: List<fr.roadly.core.scooter.segment.EdgeAttributes>, tags: Map<Long, Map<String, String>> = emptyMap(), length: Double = edges.sumOf { it.lengthM }) =
        ScooterSegmentClassifier.classify(edges, tags, FranceEdpmRegulation, length)

    @Test fun valhallaOnlyCategories() {
        val s = classify(listOf(edge(100.0, use = "cycleway"), edge(100.0, cycleLane = "dedicated"), edge(100.0, cycleLane = "separated"), edge(100.0), edge(20.0, use = "steps")))
        assertEquals(listOf(WayCategory.CYCLE_TRACK, WayCategory.CYCLE_LANE, WayCategory.CYCLE_TRACK, WayCategory.SHARED_ROAD, WayCategory.STEPS), s.map { it.category })
        // Sans tags OSM : agglomération inconnue → la route partagée n'est pas déclarée autorisée.
        assertEquals(LegalStatus.UNVERIFIED, s[3].legal.status)
        assertEquals(LegalStatus.NOT_RIDABLE, s[4].legal.status)
    }

    @Test fun greenwayFromFrenchTagging() {
        val tags = mapOf(
            1L to mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "traffic_sign" to "FR:C115"),
            2L to mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated"),
            3L to mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "yes"),
        )
        val s = classify(listOf(edge(100.0, use = "path", wayId = 1), edge(100.0, use = "path", wayId = 2), edge(100.0, use = "path", wayId = 3)), tags)
        // Les deux premières voies (C115, puis piétons + cycles non séparés) forment une seule voie verte.
        assertEquals(listOf(WayCategory.GREENWAY, WayCategory.CYCLE_TRACK), s.map { it.category })
        assertEquals(200.0, s[0].lengthM, 1e-6)
        assertEquals(LegalStatus.ALLOWED, s[0].legal.status)
    }

    @Test fun urbanRoadAllowedWithTags() {
        val s = classify(listOf(edge(200.0, speed = 50, wayId = 7)), mapOf(7L to Fixtures.URBAN50))
        assertEquals(AreaType.URBAN, s.single().area)
        assertEquals(LegalStatus.ALLOWED, s.single().legal.status)
    }

    @Test fun localRestrictionOnCycleway() {
        val s = classify(listOf(edge(150.0, use = "cycleway", wayId = 9)), mapOf(9L to mapOf("highway" to "cycleway", "small_electric_vehicle" to "no")))
        assertEquals(LegalStatus.FORBIDDEN, s.single().legal.status)
    }

    @Test fun surfaceSourcesAreTracked() {
        val s = classify(listOf(edge(100.0, surface = "paved", wayId = 1), edge(100.0, surface = "paved", wayId = 2), edge(100.0, surface = "paved_rough")),
            mapOf(1L to mapOf("highway" to "residential", "surface" to "sett"), 2L to mapOf("highway" to "residential")))
        assertEquals(SurfaceQuality.ROUGH, s[0].surface); assertEquals(DataSource.OSM_TAG, s[0].surfaceSource)
        assertEquals(SurfaceQuality.GOOD, s[1].surface); assertEquals(DataSource.VALHALLA, s[1].surfaceSource)
        assertEquals(SurfaceQuality.ROUGH, s[2].surface); assertEquals(DataSource.VALHALLA, s[2].surfaceSource)
    }

    @Test fun mergesAndRescalesToGeometryLength() {
        val s = classify(listOf(edge(100.0, use = "cycleway"), edge(100.0, use = "cycleway"), edge(200.0)), length = 440.0)
        assertEquals(2, s.size)
        assertEquals(0.0, s[0].startAlongM, 1e-6)
        assertEquals(220.0, s[0].endAlongM, 1e-6)
        assertEquals(440.0, s[1].endAlongM, 1e-6)
    }

    @Test fun conditionalRestrictionAndLighting() {
        val s = classify(listOf(edge(100.0, use = "cycleway", wayId = 1)), mapOf(1L to mapOf("highway" to "cycleway", "lit" to "no", "access:conditional" to "no @ (22:00-06:00)")))
        assertEquals(false, s.single().lit)
        assertTrue(s.single().conditionalRestriction!!.startsWith("access:conditional"))
    }

    @Test fun lightingUnknownWithoutTags() {
        assertNull(classify(listOf(edge(100.0, use = "cycleway"))).single().lit)
    }
}
