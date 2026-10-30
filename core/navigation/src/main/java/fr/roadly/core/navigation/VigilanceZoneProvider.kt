package fr.roadly.core.navigation

import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import javax.inject.Inject

/** Zones de vigilance le long d'un tracé, en abscisses curvilignes. */
interface VigilanceZoneProvider {
    suspend fun zonesAlong(route: Route): List<RouteHazard>
}

class NoopVigilanceZoneProvider @Inject constructor() : VigilanceZoneProvider {
    override suspend fun zonesAlong(route: Route): List<RouteHazard> = emptyList()
}

/**
 * Reconstitue des zones (jamais un point) : chaque enregistrement du jeu officiel à moins de [MAX_OFFSET_M] du tracé
 * devient une zone qui commence [lead] mètres avant et finit [TAIL_M] mètres après. Longueurs calées sur les
 * distances légales des « zones de danger » (300 m en ville, 2 km hors agglomération, 4 km sur autoroute).
 */
object VigilanceZones {
    const val MAX_OFFSET_M = 60.0
    const val TAIL_M = 300.0
    /** Marge (degrés) autour de la boîte englobante du tracé pour la requête locale. */
    const val BOX_MARGIN_DEG = 0.01

    fun leadM(limitKmh: Int?): Double = when {
        limitKmh == null -> 2_000.0
        limitKmh >= 110 -> 4_000.0
        limitKmh >= 70 -> 2_000.0
        else -> 300.0
    }

    fun build(route: Route, records: List<VigilanceZoneEntity>): List<RouteHazard> {
        val geo = RouteGeometry(route.geometry)
        val zones = records.mapNotNull { r ->
            val p = geo.project(GeoPoint(r.lat, r.lon)) ?: return@mapNotNull null
            if (p.offsetM > MAX_OFFSET_M) return@mapNotNull null
            RouteHazard(
                HazardKind.VIGILANCE_ZONE,
                startAlongM = (p.alongM - leadM(r.limitKmh)).coerceAtLeast(0.0),
                endAlongM = (p.alongM + TAIL_M).coerceAtMost(geo.totalM),
                speedLimitKmh = r.limitKmh,
            )
        }.sortedBy { it.startAlongM }
        // Zones qui se chevauchent : fusionnées (la limitation la plus basse l'emporte).
        val merged = ArrayList<RouteHazard>()
        for (z in zones) {
            val last = merged.lastOrNull()
            if (last != null && z.startAlongM <= last.endAlongM) {
                merged[merged.lastIndex] = last.copy(
                    endAlongM = maxOf(last.endAlongM, z.endAlongM),
                    speedLimitKmh = listOfNotNull(last.speedLimitKmh, z.speedLimitKmh).minOrNull(),
                )
            } else merged += z
        }
        return merged
    }
}

class RepositoryVigilanceZoneProvider @Inject constructor(private val repository: VigilanceRepository) : VigilanceZoneProvider {
    override suspend fun zonesAlong(route: Route): List<RouteHazard> {
        val box = BoundingBox.of(route.geometry) ?: return emptyList()
        val m = VigilanceZones.BOX_MARGIN_DEG
        val records = repository.zonesIn(BoundingBox(box.south - m, box.west - m, box.north + m, box.east + m))
        return VigilanceZones.build(route, records)
    }
}
