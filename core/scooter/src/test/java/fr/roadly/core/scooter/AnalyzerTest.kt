package fr.roadly.core.scooter

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.scooter.Fixtures.edge
import fr.roadly.core.scooter.Fixtures.maneuver
import fr.roadly.core.scooter.analysis.ElevationSource
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysisBuilder
import fr.roadly.core.scooter.analysis.ScooterRouteRanking
import fr.roadly.core.scooter.analysis.RankInput
import fr.roadly.core.scooter.analysis.ScooterWarningKind
import fr.roadly.core.scooter.analysis.WarningSeverity
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.segment.WayCategory
import fr.roadly.core.scooter.valhalla.TraceAttributesResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzerTest {
    private val g2 = ScooterPresets.NINEBOT_MAX_G2

    private fun analyze(edges: List<fr.roadly.core.scooter.segment.EdgeAttributes>, tags: Map<Long, Map<String, String>>? = null, route: fr.roadly.core.model.Route = Fixtures.route(edges.sumOf { it.lengthM })) =
        ScooterRouteAnalysisBuilder.build(route, TraceAttributesResult(edges, listOf("FR")), tags, g2)

    @Test fun urbanTripWithCycleTracks() {
        val a = analyze(listOf(edge(300.0, use = "cycleway"), edge(100.0, cycleLane = "dedicated"), edge(100.0, speed = 30)))
        assertEquals(0.8, a.summary.cycleInfrastructureShare, 0.02)
        assertEquals(0.6, a.summary.dedicatedShare, 0.02)
        assertEquals(1, a.summary.sharedRoadPortions)
        assertFalse(a.osmTagsAvailable)
    }

    @Test fun tripWithoutCycleInfrastructure() {
        val a = analyze(listOf(edge(500.0, speed = 50)))
        assertEquals(0.0, a.summary.cycleInfrastructureShare, 1e-9)
        assertEquals(500.0, a.summary.sharedRoadLengthM, 1.0)
        assertTrue(a.warnings.any { it.kind == ScooterWarningKind.SHARED_ROAD })
        assertTrue(a.warnings.any { it.kind == ScooterWarningKind.FAST_TRAFFIC })
        // Agglomération inconnue (pas de tags OSM) : accès non vérifiable, signalé comme tel.
        assertTrue(a.warnings.any { it.kind == ScooterWarningKind.UNVERIFIED_ACCESS })
    }

    @Test fun forbiddenRoadIsReported() {
        val a = analyze(listOf(edge(200.0, use = "cycleway"), edge(300.0, roadClass = "trunk", speed = 90)))
        assertTrue(a.hasBlockingPortion)
        assertEquals(300.0, a.summary.forbiddenLengthM, 1.0)
        assertTrue(a.warnings.any { it.kind == ScooterWarningKind.FORBIDDEN && it.severity == WarningSeverity.DANGER })
    }

    @Test fun steepClimbAndDescent() {
        val a = analyze(listOf(edge(200.0, grade = 0.0), edge(150.0, grade = 8.0), edge(150.0, grade = -11.0), edge(100.0, grade = 25.0)))
        val climb = a.warnings.first { it.kind == ScooterWarningKind.STEEP_CLIMB }
        assertEquals(200.0, climb.startAlongM, 1.0)
        assertEquals(WarningSeverity.CAUTION, climb.severity)
        assertEquals(WarningSeverity.DANGER, a.warnings.first { it.kind == ScooterWarningKind.STEEP_DESCENT }.severity)
        // 25 % > 22 % annoncés pour la MAX G2.
        assertTrue(a.warnings.any { it.kind == ScooterWarningKind.GRADE_ABOVE_CAPACITY })
        assertEquals(ElevationSource.EDGE_GRADES, a.summary.elevationSource)
        assertEquals(12.0 + 25.0, a.summary.ascentM!!, 0.5)
        assertEquals(16.5, a.summary.descentM!!, 0.5)
    }

    @Test fun elevationProfilePreferredAndUnknownWhenAbsent() {
        val withProfile = analyze(listOf(edge(90.0, grade = null)), route = Fixtures.route(90.0, elevation = listOf(100.0, 104.0, 102.0, 110.0)))
        assertEquals(ElevationSource.ROUTE_PROFILE, withProfile.summary.elevationSource)
        assertEquals(12.0, withProfile.summary.ascentM!!, 1e-9)
        assertEquals(2.0, withProfile.summary.descentM!!, 1e-9)
        val none = analyze(listOf(edge(90.0, grade = null)))
        assertNull(none.summary.ascentM)
        assertEquals(ElevationSource.NONE, none.summary.elevationSource)
    }

    @Test fun severalLaneChanges() {
        val a = analyze(listOf(edge(100.0, use = "cycleway"), edge(100.0), edge(100.0, cycleLane = "dedicated"), edge(100.0), edge(100.0, use = "cycleway")))
        assertEquals(listOf(WayCategory.CYCLE_TRACK, WayCategory.SHARED_ROAD, WayCategory.CYCLE_LANE, WayCategory.SHARED_ROAD, WayCategory.CYCLE_TRACK), a.segments.map { it.category })
        assertEquals(2, a.summary.sharedRoadPortions)
    }

    @Test fun complexIntersectionOnMajorRoad() {
        val route = Fixtures.route(400.0, listOf(maneuver(ManeuverType.DEPART, 0.0), maneuver(ManeuverType.TURN_LEFT, 200.0), maneuver(ManeuverType.ARRIVE, 400.0)))
        val a = analyze(listOf(edge(200.0, roadClass = "primary", speed = 50), edge(200.0, use = "cycleway")), route = route)
        assertTrue(a.warnings.any { it.kind == ScooterWarningKind.COMPLEX_INTERSECTION })
    }

    @Test fun ranksSaferRouteFromKnownData() {
        val safe = analyze(listOf(edge(1000.0, use = "cycleway")))
        val busy = analyze(listOf(edge(800.0, speed = 50)))
        val labels = ScooterRouteRanking.labels(listOf(
            RankInput("safe", null, 1000.0, 200.0, safe),
            RankInput("busy", null, 800.0, 150.0, busy),
        ))
        assertTrue("Le plus sécurisé" in labels.getValue("safe"))
        assertTrue("Le plus rapide" in labels.getValue("busy"))
        assertTrue("Le plus court" in labels.getValue("busy"))
    }

    @Test fun coverageReflectsMissingData() {
        val a = analyze(listOf(edge(100.0, use = "cycleway", grade = null, wayId = 1), edge(100.0, grade = null)), tags = mapOf(1L to mapOf("highway" to "cycleway", "surface" to "asphalt", "lit" to "yes")))
        assertEquals(0.5, a.coverage.osmTags, 1e-6)
        assertEquals(0.5, a.coverage.surfaceFromOsm, 1e-6)
        assertEquals(0.5, a.coverage.lighting, 1e-6)
        assertEquals(0.0, a.coverage.grade, 1e-6)
    }
}
