package fr.roadly.core.data.community

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** Boîte de ±[halfDeg] degrés autour d'un point (≈ 5 km avec 0,05°). */
fun GeoPoint.box(halfDeg: Double = 0.05): BoundingBox = BoundingBox(lat - halfDeg, lon - halfDeg, lat + halfDeg, lon + halfDeg)

/**
 * Signalements autour d'un point, rafraîchis toutes les [intervalMs] tant que le service est configuré ;
 * sans service : une seule émission vide, aucun appel réseau. Un échec garde la dernière liste connue.
 */
fun CommunityRepository.pollNearby(center: suspend () -> GeoPoint?, intervalMs: Long = 60_000L): Flow<List<CommunityReport>> = flow {
    if (!isConfigured.first()) { emit(emptyList()); return@flow }
    var last: List<CommunityReport> = emptyList()
    while (true) {
        val at = center()
        if (at != null) {
            when (val r = nearby(at.box())) {
                is Outcome.Success -> last = r.value
                is Outcome.Failure -> Unit
            }
        }
        emit(last)
        delay(intervalMs)
    }
}
