package fr.roadly.feature.navigation

import fr.roadly.core.common.Formatters
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.RouteGeometry

/**
 * C3 : signalements devant soi sans reprojection globale à chaque position. Chaque signalement est projeté une fois par tracé
 * (abscisse et écart mis en cache par identifiant), puis seulement filtré selon l'abscisse courante.
 */
internal class ReportsAheadProjector {
    private var routeId: String? = null
    private var geometry: RouteGeometry? = null
    private val projections = HashMap<String, RouteGeometry.Projection?>()

    /** Nombre de projections calculées depuis la création (contrôle du cache). */
    var projectionCount = 0
        private set

    fun ahead(route: Route, alongM: Double, reports: List<CommunityReport>): List<ReportAheadUi> {
        if (route.id != routeId) {
            routeId = route.id
            geometry = RouteGeometry(route.geometry)
            projections.clear()
        }
        val geo = geometry ?: return emptyList()
        if (projections.size > reports.size) {
            val ids = reports.mapTo(HashSet()) { it.id }
            projections.keys.retainAll(ids)
        }
        return reports.mapNotNull { r ->
            val p = if (r.id in projections) projections[r.id] else geo.project(r.point).also { projections[r.id] = it; projectionCount++ }
            p ?: return@mapNotNull null
            val d = p.alongM - alongM
            if (p.offsetM > MAX_OFFSET_M || d < -BEHIND_M || d > AHEAD_M) null
            else ReportAheadUi(r, d.coerceAtLeast(0.0), Formatters.distance(d.coerceAtLeast(0.0)))
        }.sortedBy { it.distanceM }
    }

    companion object {
        /** Écart maximal (m) au tracé pour qu'un signalement concerne l'itinéraire. */
        const val MAX_OFFSET_M = 60.0
        /** Tolérance (m) derrière la position (signalement que l'on est en train de passer). */
        const val BEHIND_M = 50.0
        /** Portée (m) devant la position, celle de l'Horizon. */
        const val AHEAD_M = 5_000.0
    }
}
