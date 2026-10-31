package fr.roadly.core.map

import fr.roadly.core.model.MapDetail

/** C2 : niveau appliqué à la carte — le réglage, ou réduit automatiquement à grande vitesse (spec : « réduis les détails à grande vitesse »). */
object MapDetailPolicy {
    /** Au-delà (km/h), le niveau NORMAL passe en REDUCED. */
    const val AUTO_REDUCE_ABOVE_KMH = 90.0
    /** Sous ce seuil (km/h), le niveau réduit automatiquement revient à NORMAL (hystérésis : pas de clignotement vers 90 km/h). */
    const val AUTO_RESTORE_BELOW_KMH = 80.0

    fun effective(setting: MapDetail, speedKmh: Double?, current: MapDetail): MapDetail = when {
        setting == MapDetail.REDUCED -> MapDetail.REDUCED
        speedKmh == null -> current
        speedKmh > AUTO_REDUCE_ABOVE_KMH -> MapDetail.REDUCED
        current == MapDetail.REDUCED && speedKmh >= AUTO_RESTORE_BELOW_KMH -> MapDetail.REDUCED
        else -> MapDetail.NORMAL
    }
}
