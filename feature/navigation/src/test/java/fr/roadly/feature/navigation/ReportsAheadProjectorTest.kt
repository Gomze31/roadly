package fr.roadly.feature.navigation

import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.ReportKind
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsAheadProjectorTest {
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private fun report(id: String, along: Double) = CommunityReport(id, ReportKind.ACCIDENT, tracker.pointAt(along), 0L, 0L, 0, 0, mine = false)
    private val reports = listOf(report("a", 400.0), report("b", 900.0), report("c", 1_300.0))

    @Test fun `each report is projected once per route, then only filtered`() { // C3
        val p = ReportsAheadProjector()
        for (along in 0..380 step 20) p.ahead(route, along.toDouble(), reports)
        assertEquals(3, p.projectionCount)
        val ahead = p.ahead(route, 350.0, reports)
        assertEquals(listOf("a", "b", "c"), ahead.map { it.report.id })
        assertEquals(50.0, ahead[0].distanceM, 1.0)
        assertEquals(3, p.projectionCount)
    }

    @Test fun `results match a full projection`() {
        val geo = RouteGeometry(route.geometry)
        val p = ReportsAheadProjector()
        for (along in listOf(0.0, 380.0, 850.0, 1_250.0)) {
            val expected = reports.mapNotNull { r ->
                val pr = geo.project(r.point) ?: return@mapNotNull null
                val d = pr.alongM - along
                if (pr.offsetM > 60.0 || d < -50.0 || d > 5_000.0) null else r.id to d.coerceAtLeast(0.0)
            }.sortedBy { it.second }
            assertEquals(expected, p.ahead(route, along, reports).map { it.report.id to it.distanceM })
        }
    }

    @Test fun `a new route projects again and stale reports are forgotten`() {
        val p = ReportsAheadProjector()
        p.ahead(route, 0.0, reports)
        val other = route.copy(id = "autre")
        p.ahead(other, 0.0, reports)
        assertEquals(6, p.projectionCount)
        p.ahead(other, 0.0, reports.take(1))
        p.ahead(other, 0.0, reports)
        assertEquals("b et c oubliés puis reprojetés", 8, p.projectionCount)
    }
}
