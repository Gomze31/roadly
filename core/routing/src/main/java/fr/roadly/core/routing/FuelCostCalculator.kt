package fr.roadly.core.routing

import fr.roadly.core.model.CostEstimate
import fr.roadly.core.model.VehicleProfile

object FuelCostCalculator {
    /** Null si le profil ne permet pas d'estimer (pas de prix saisi ou consommation nulle). Péages : Phase 3, toujours null ici. */
    fun estimate(distanceMeters: Double, profile: VehicleProfile): CostEstimate? {
        val price = profile.fuelPriceEur ?: return null
        if (profile.consumptionPer100 <= 0.0 || price <= 0.0) return null
        val km = distanceMeters / 1000.0
        val fuel = km * profile.consumptionPer100 / 100.0 * price
        return CostEstimate(fuelEur = fuel, tollEur = null, totalEur = fuel, perKmEur = if (km > 0) fuel / km else 0.0)
    }
}
