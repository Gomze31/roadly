package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.charging.ChargingStation
import fr.roadly.core.data.charging.ChargingStationProvider
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.math.cos

/** Borne devant soi : [aheadM] le long du tracé, [offsetM] d'écart au tracé (détour ≈ 2 × l'écart), [status] = dernier état publié. */
data class ChargerAhead(val station: ChargingStation, val aheadM: Double, val offsetM: Double, val status: StationStatus? = null) {
    val detourM: Double get() = 2 * offsetM
}

/**
 * Dernier état publié des points de charge d'une station (base dynamique nationale, relevés d'âge variable) : [outOfService] hors
 * service ; [free] / [occupied] seulement pour les relevés de moins de [StationStatus.FRESH_MS] ; [latestAtMs] = relevé le plus récent.
 */
data class StationStatus(val known: Int, val outOfService: Int, val free: Int?, val occupied: Int?, val latestAtMs: Long?) {
    /** Tous les points connus sont hors service. */
    val allDown: Boolean get() = known > 0 && outOfService == known

    /**
     * « 2 libres sur 4 · il y a 12 min », « 1 hors service sur 4 · relevé du 15/11 » ; null si rien d'utile n'est publié.
     * (Texte composé ici, comme les phrases parlées : exception assumée à la règle « textes en ressources ».)
     */
    fun describe(nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
        val parts = buildList {
            if (free != null) add(if (free == 0) "aucun point libre" else "$free libre${if (free > 1) "s" else ""} sur $known")
            if (outOfService > 0) add(if (allDown) "hors service" else "$outOfService hors service sur $known")
        }
        if (parts.isEmpty()) return null
        val at = latestAtMs ?: return parts.joinToString(" · ")
        val ageMin = (nowMs - at) / 60_000
        val whenText = if (ageMin in 0 until 60) "il y a $ageMin min"
            else "relevé du " + java.time.Instant.ofEpochMilli(at).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
        return (parts + whenText).joinToString(" · ")
    }

    companion object {
        const val FRESH_MS = 30 * 60_000L

        fun of(statuses: List<fr.roadly.core.data.charging.PdcStatus>, nowMs: Long): StationStatus? {
            if (statuses.isEmpty()) return null
            val fresh = statuses.filter { s -> s.atEpochMs?.let { nowMs - it <= FRESH_MS } == true }
            return StationStatus(
                known = statuses.size,
                outOfService = statuses.count { it.inService == false },
                free = fresh.takeIf { it.isNotEmpty() }?.count { it.inService != false && it.occupancy == fr.roadly.core.data.charging.Occupancy.FREE },
                occupied = fresh.takeIf { it.isNotEmpty() }?.count { it.occupancy == fr.roadly.core.data.charging.Occupancy.OCCUPIED },
                latestAtMs = statuses.mapNotNull { it.atEpochMs }.maxOrNull(),
            )
        }
    }
}

/**
 * Bornes rapides (base nationale IRVE) à moins de [MAX_OFFSET_M] du tracé, devant la position, sur les [RANGE_M] prochains
 * mètres, dans l'ordre du trajet. Une zone carrée tous les [SAMPLE_STEP_M], interrogées en parallèle.
 */
class ChargersAlongRoute(private val stations: ChargingStationProvider, private val nowMs: () -> Long) {
    @Inject constructor(stations: ChargingStationProvider) : this(stations, System::currentTimeMillis)

    suspend fun ahead(geometry: List<GeoPoint>, fromAlongM: Double, minKw: Int = MIN_KW, rangeM: Double = RANGE_M): Outcome<List<ChargerAhead>> {
        if (geometry.size < 2) return Outcome.Failure(AppError.NoResult)
        val geo = RouteGeometry(geometry)
        val end = minOf(geo.totalM, fromAlongM + rangeM)
        val samples = generateSequence(fromAlongM + SAMPLE_STEP_M / 2) { it + SAMPLE_STEP_M }.takeWhile { it < end + SAMPLE_STEP_M / 2 }
            .map { geo.pointAt(minOf(it, end)) }.toList()
        val results = coroutineScope {
            samples.map { p ->
                async {
                    try { stations.fastIn(box(p), minKw) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Outcome.Failure(AppError.Unknown(e.message))
                    }
                }
            }.awaitAll()
        }
        val found = results.flatMap { (it as? Outcome.Success)?.value.orEmpty() }
        if (found.isEmpty()) {
            return results.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error }?.let { Outcome.Failure(it) } ?: Outcome.Success(emptyList())
        }
        val out = found.distinctBy { it.id to it.point }.mapNotNull { s ->
            val proj = geo.project(s.point) ?: return@mapNotNull null
            val ahead = proj.alongM - fromAlongM
            if (proj.offsetM > MAX_OFFSET_M || ahead < MIN_AHEAD_M || ahead > rangeM) return@mapNotNull null
            // Aire d'autoroute (nom ou adresse : « Aire », « A61 »…) collée au tracé, à gauche du sens de marche : elle dessert
            // l'autre chaussée (circulation à droite). La base IRVE ne dit pas « autoroute » : indice textuel + côté.
            if (proj.offsetM <= MOTORWAY_AREA_OFFSET_M && motorwayArea.containsMatchIn("${s.name} ${s.address.orEmpty()}") && geo.isLeftOf(proj.segmentIndex, s.point)) {
                return@mapNotNull null
            }
            ChargerAhead(s, ahead, proj.offsetM)
        }
        // Répartition sur tout le trajet : les 2 bornes les plus puissantes par tranche de [BUCKET_M] (en ville, des dizaines de
        // bornes se suivent ; sans cela, la liste s'arrêterait au premier quartier).
        val top = out.groupBy { (it.aheadM / BUCKET_M).toInt() }.toSortedMap().values
            .flatMap { bucket -> bucket.sortedWith(compareByDescending<ChargerAhead> { it.station.maxKw }.thenBy { it.detourM }).take(PER_BUCKET) }
            .take(MAX_RESULTS)
        // Dernier état publié : facultatif (une erreur laisse la liste sans état) ; les stations entièrement hors service passent à la fin.
        val statuses = try {
            (stations.status(top.flatMap { it.station.pdcIds }) as? Outcome.Success)?.value.orEmpty()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyMap() }
        val now = nowMs()
        val withStatus = top.map { c -> c.copy(status = StationStatus.of(c.station.pdcIds.mapNotNull { statuses[it] }, now)) }
        return Outcome.Success(withStatus.sortedBy { it.status?.allDown == true })
    }

    /** Carré de ~[SAMPLE_STEP_M] de côté centré sur [p] (les carrés successifs se recouvrent un peu). */
    private fun box(p: GeoPoint): BoundingBox {
        val half = SAMPLE_STEP_M * 0.6
        val dLat = half / 111_320.0
        val dLon = half / (111_320.0 * cos(Math.toRadians(p.lat)).coerceAtLeast(0.01))
        return BoundingBox(p.lat - dLat, p.lon - dLon, p.lat + dLat, p.lon + dLon)
    }

    companion object {
        /** Recharge rapide : 50 kW et plus. */
        const val MIN_KW = 50
        const val RANGE_M = 150_000.0
        const val SAMPLE_STEP_M = 20_000.0
        /** Écart maximal au tracé : 2 km (une borne rapide justifie un détour un peu plus long qu'une station-service). */
        const val MAX_OFFSET_M = 2_000.0
        const val MIN_AHEAD_M = 500.0
        const val MAX_RESULTS = 15
        const val MOTORWAY_AREA_OFFSET_M = 500.0
        private val motorwayArea = Regex("""(?i)\baire\b|\bA ?\d{1,3}\b""")
        const val BUCKET_M = 15_000.0
        const val PER_BUCKET = 2
    }
}
