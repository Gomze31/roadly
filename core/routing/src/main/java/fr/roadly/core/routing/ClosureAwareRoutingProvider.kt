package fr.roadly.core.routing

import fr.roadly.core.common.Outcome
import fr.roadly.core.data.traffic.TrafficEvent
import fr.roadly.core.data.traffic.TrafficKind
import fr.roadly.core.data.traffic.TrafficOnRoute
import fr.roadly.core.data.traffic.TrafficSource
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

/**
 * Contournement automatique des routes fermées publiées par Bison Futé, pour tout calcul en voiture (choix d'itinéraire, recalcul
 * en guidage, Android Auto, commandes vocales). Le tracé est d'abord calculé normalement ; s'il emprunte une fermeture en cours
 * dans son sens de circulation, il est recalculé en excluant de petites zones autour des points de la fermeture
 * (`exclude_polygons` Valhalla). Sans autre chemin, le tracé d'origine est gardé (l'incident reste signalé à l'écran).
 */
@Singleton
class ClosureAwareRoutingProvider internal constructor(
    private val inner: RoutingProvider,
    private val traffic: TrafficSource,
    private val nowMs: () -> Long,
) : RoutingProvider {
    @Inject constructor(inner: ValhallaRoutingProvider, traffic: TrafficSource) : this(inner as RoutingProvider, traffic, System::currentTimeMillis)

    override val id: String get() = inner.id

    override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
        val first = inner.route(request)
        if (request.scooter != null || request.pedestrian) return first
        val best = (first as? Outcome.Success)?.value?.firstOrNull() ?: return first
        val closures = activeClosures()
        if (closures.isEmpty()) return first
        var hits = TrafficOnRoute.along(best.geometry, closures)
        if (hits.isEmpty()) return first
        // Par fermeture : ses points publiés et le point où le tracé la rejoint (entrée au milieu d'un tronçon fermé).
        val avoided = LinkedHashMap<String, List<GeoPoint>>()
        var current: Outcome<List<Route>> = first
        repeat(MAX_ROUNDS) {
            hits.forEach { h -> avoided.putIfAbsent(h.event.id, (h.event.points + h.point).distinctBy { it.lat to it.lon }) }
            val zones = avoided.values.take(MAX_CLOSURES).flatMap { pts -> pts.map { box(it) } }
            val retry = inner.route(request.copy(excludePolygons = request.excludePolygons + zones))
            val routes = (retry as? Outcome.Success)?.value?.takeIf { it.isNotEmpty() } ?: return current
            current = Outcome.Success(routes.map { it.copy(avoidedClosures = avoided.size) })
            hits = TrafficOnRoute.along(routes.first().geometry, closures).filter { it.event.id !in avoided }
            if (hits.isEmpty()) return current
        }
        return current
    }

    /** Fermetures en cours (pas les fermetures programmées plus tard) ; flux indisponible = aucune. */
    private suspend fun activeClosures(): List<TrafficEvent> {
        val events = try {
            withTimeoutOrNull(EVENTS_TIMEOUT_MS) { traffic.events() }.orEmpty()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
        val now = nowMs()
        return events.filter { e ->
            e.kind == TrafficKind.CLOSURE && (e.startEpochMs == null || e.startEpochMs!! <= now) && (e.endEpochMs == null || e.endEpochMs!! > now)
        }
    }

    companion object {
        /** Demi-côté (m) des carrés exclus : la chaussée fermée, sans déborder sur un carrefour voisin. */
        const val BOX_HALF_M = 40.0
        /** Valhalla limite le périmètre total des zones exclues (10 km par défaut) : au plus 8 fermetures × 3 points × 320 m. */
        const val MAX_CLOSURES = 8
        const val MAX_ROUNDS = 2
        const val EVENTS_TIMEOUT_MS = 5_000L

        internal fun box(p: GeoPoint, halfM: Double = BOX_HALF_M): List<GeoPoint> {
            val dLat = halfM / 111_320.0
            val dLon = halfM / (111_320.0 * cos(Math.toRadians(p.lat)).coerceAtLeast(0.01))
            return listOf(
                GeoPoint(p.lat - dLat, p.lon - dLon), GeoPoint(p.lat - dLat, p.lon + dLon),
                GeoPoint(p.lat + dLat, p.lon + dLon), GeoPoint(p.lat + dLat, p.lon - dLon), GeoPoint(p.lat - dLat, p.lon - dLon),
            )
        }
    }
}
