package fr.roadly.core.model

enum class VehicleType { CITY, SEDAN, SUV, VAN }
enum class FuelType { SP95, SP98, E10, E85, DIESEL, LPG, ELECTRIC }

/** [consumptionPer100] : L/100 km, ou kWh/100 km si [fuel] == ELECTRIC. [fuelPriceEur] : €/L ou €/kWh, saisi par l'utilisateur. */
data class VehicleProfile(
    val type: VehicleType,
    val fuel: FuelType,
    val consumptionPer100: Double,
    val fuelPriceEur: Double?,
)

data class CostEstimate(val fuelEur: Double, val tollEur: Double?, val totalEur: Double, val perKmEur: Double)
