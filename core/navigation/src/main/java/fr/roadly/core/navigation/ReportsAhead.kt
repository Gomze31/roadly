package fr.roadly.core.navigation

import fr.roadly.core.model.CommunityReport

/** Signalement devant soi : [distanceM] le long du tracé depuis la position projetée. */
data class ReportAhead(val report: CommunityReport, val distanceM: Double)

/** Signalements à ≤ 60 m du tracé et entre −50 m et 5 km devant la position projetée, du plus proche au plus loin. */
fun reportsAhead(g: NavigationState.Guiding, reports: List<CommunityReport>): List<ReportAhead> {
    val geo = RouteGeometry(g.route.geometry)
    return reports.mapNotNull { r ->
        val p = geo.project(r.point) ?: return@mapNotNull null
        val d = p.alongM - g.progress.distanceAlongM
        if (p.offsetM > 60.0 || d < -50.0 || d > 5_000.0) null else ReportAhead(r, d.coerceAtLeast(0.0))
    }.sortedBy { it.distanceM }
}
