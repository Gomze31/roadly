package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/** Station devant soi : [aheadM] le long du tracé, [offsetM] d'écart au tracé (détour ≈ 2 × l'écart), [priceEur] du carburant choisi. */
data class StationAhead(val station: FuelStation, val aheadM: Double, val offsetM: Double, val priceEur: Double?) {
    /** Détour aller-retour estimé (à vol d'oiseau ; la route réelle peut être un peu plus longue). */
    val detourM: Double get() = 2 * offsetM
}

/**
 * Recherche « le long du trajet » appliquée aux prix officiels des carburants : stations à moins de [MAX_OFFSET_M] du tracé,
 * devant la position, sur les [RANGE_M] prochains mètres, de la moins chère à la plus chère pour le carburant du véhicule.
 * Quelques requêtes au flux public (un cercle tous les [SAMPLE_STEP_M]), seulement quand l'utilisateur le demande.
 */
class FuelAlongRoute @Inject constructor(private val stations: FuelStationProvider) {

    suspend fun ahead(geometry: List<GeoPoint>, fromAlongM: Double, fuel: FuelType, rangeM: Double = RANGE_M): Outcome<List<StationAhead>> {
        if (geometry.size < 2) return Outcome.Failure(AppError.NoResult)
        val geo = RouteGeometry(geometry)
        val end = minOf(geo.totalM, fromAlongM + rangeM)
        val samples = generateSequence(fromAlongM + SAMPLE_STEP_M / 2) { it + SAMPLE_STEP_M }.takeWhile { it < end + SAMPLE_STEP_M / 2 }
            .map { geo.pointAt(minOf(it, end)) }.toList().ifEmpty { listOf(geo.pointAt(end)) }
        val results = coroutineScope {
            samples.map { p ->
                async {
                    try { stations.nearby(p, radiusKm = SAMPLE_RADIUS_KM, limit = PER_SAMPLE) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        Outcome.Failure(AppError.Unknown(e.message))
                    }
                }
            }.awaitAll()
        }
        val found = results.flatMap { (it as? Outcome.Success)?.value.orEmpty() }
        if (found.isEmpty()) {
            return results.firstNotNullOfOrNull { (it as? Outcome.Failure)?.error }?.let { Outcome.Failure(it) } ?: Outcome.Success(emptyList())
        }
        val out = found.distinctBy { it.id }.mapNotNull { s ->
            val proj = geo.project(s.point) ?: return@mapNotNull null
            val ahead = proj.alongM - fromAlongM
            if (proj.offsetM > MAX_OFFSET_M || ahead < MIN_AHEAD_M || ahead > rangeM) return@mapNotNull null
            StationAhead(s, ahead, proj.offsetM, s.price(fuel)?.eurPerLitre)
        }
        // Prix connus d'abord (du moins cher au plus cher, puis le plus proche), stations sans ce carburant à la fin.
        return Outcome.Success(out.sortedWith(compareBy<StationAhead>({ it.priceEur == null }, { it.priceEur ?: 0.0 }, { it.aheadM })).take(MAX_RESULTS))
    }

    companion object {
        const val RANGE_M = 80_000.0
        const val SAMPLE_STEP_M = 15_000.0
        const val SAMPLE_RADIUS_KM = 9
        const val PER_SAMPLE = 60
        /** Écart maximal au tracé : au-delà, le détour n'en vaut plus la peine. */
        const val MAX_OFFSET_M = 1_500.0
        /** Une station à moins de 500 m devant ne laisse pas le temps de s'y arrêter. */
        const val MIN_AHEAD_M = 500.0
        const val MAX_RESULTS = 12
    }
}
