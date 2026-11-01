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
    val travelMode: TravelMode = TravelMode.CAR,
    /** Trottinette : dénivelé parcouru d'après l'analyse de l'itinéraire (null = inconnu). */
    val ascentM: Double? = null,
    val descentM: Double? = null,
    /** Énergie estimée par le modèle (Wh), non calibrée : sert à la calibration avec les niveaux de batterie saisis. */
    val energyModelWh: Double? = null,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
    val scooterProfileId: Long? = null,
) {
    val averageSpeedKmh: Double get() = if (durationS > 0) distanceM / 1000.0 / (durationS / 3600.0) else 0.0
}

/** Totaux d'une période (bilan). */
data class TripStats(val trips: Int, val distanceM: Double, val durationS: Long, val costEur: Double)

/** Bilan d'une période (semaine, mois) pour un mode : distances, durée, vitesse moyenne, dénivelé, énergie estimée. */
data class TripPeriodStats(
    val trips: Int,
    val distanceM: Double,
    val durationS: Long,
    val ascentM: Double?,
    val energyModelWh: Double?,
) {
    val averageSpeedKmh: Double get() = if (durationS > 0) distanceM / 1000.0 / (durationS / 3600.0) else 0.0

    companion object {
        fun of(trips: List<Trip>): TripPeriodStats = TripPeriodStats(
            trips = trips.size,
            distanceM = trips.sumOf { it.distanceM },
            durationS = trips.sumOf { it.durationS },
            ascentM = trips.mapNotNull { it.ascentM }.takeIf { it.isNotEmpty() }?.sum(),
            energyModelWh = trips.mapNotNull { it.energyModelWh }.takeIf { it.isNotEmpty() }?.sum(),
        )

        /** Trajets de la semaine ISO (lundi 00:00, fuseau [zone]) et du mois calendaire contenant [nowMs]. */
        fun weekAndMonth(trips: List<Trip>, nowMs: Long, zone: java.time.ZoneId): Pair<TripPeriodStats, TripPeriodStats> {
            val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val weekStart = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return of(trips.filter { it.startedAtEpochMs >= weekStart }) to of(trips.filter { it.startedAtEpochMs >= monthStart })
        }
    }
}
