package fr.roadly.core.model

/** Point enregistré pendant un trajet : [tMs] = temps écoulé depuis le départ. */
data class TripPoint(val tMs: Long, val point: GeoPoint, val speedKmh: Double?)

/** Trajet terminé ; [costEur] estimé d'après le profil véhicule du moment (null sans profil). */
data class Trip(
    val id: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val destinationName: String,
    val distanceM: Double,
    val durationS: Long,
    val maxSpeedKmh: Double,
    val costEur: Double?,
    val arrived: Boolean,
    val points: List<TripPoint>,
) {
    val averageSpeedKmh: Double get() = if (durationS > 0) distanceM / 1000.0 / (durationS / 3600.0) else 0.0
}

/** Totaux d'une période (bilan). */
data class TripStats(val trips: Int, val distanceM: Double, val durationS: Long, val costEur: Double)
