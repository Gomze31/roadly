package fr.roadly.core.scooter.range

import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.Trip

/**
 * Calibration à partir de l'historique : trajets trottinette du profil, avec batterie saisie au départ et à
 * l'arrivée et énergie prévue par le modèle. Énergie observée = Δ% × capacité utilisable (20 °C supposés).
 */
object TripCalibration {
    fun samples(trips: List<Trip>, profile: ElectricScooterProfile): List<Pair<Double, Double>> {
        val usable = ScooterRangeEstimator(profile).usableCapacity(20.0)?.first ?: return emptyList()
        return trips.filter { it.travelMode == TravelMode.ELECTRIC_SCOOTER && it.scooterProfileId == profile.id }
            .mapNotNull { t ->
                val start = t.batteryStartPct ?: return@mapNotNull null
                val end = t.batteryEndPct ?: return@mapNotNull null
                val predicted = t.energyModelWh ?: return@mapNotNull null
                if (end >= start) return@mapNotNull null
                predicted to (start - end) / 100.0 * usable
            }
    }

    fun of(trips: List<Trip>, profile: ElectricScooterProfile): RangeCalibration = RangeCalibration.fromSamples(samples(trips, profile))
}
