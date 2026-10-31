package fr.roadly.core.model

/** Types de véhicule ; [isHeavy] = gabarit pris en compte par le routage (costing poids lourd). */
enum class VehicleType(val isHeavy: Boolean = false) { CITY, SEDAN, SUV, VAN, MOTORCYCLE, TAXI, TRUCK(isHeavy = true) }
enum class FuelType { SP95, SP98, E10, E85, DIESEL, LPG, ELECTRIC }

/** Vignette Crit'Air (France) ; ZERO = électrique / hydrogène. */
enum class CritAir { ZERO, C1, C2, C3, C4, C5, NONE }

/**
 * Gabarit (m, t) et essieux : utilisés par le routage poids lourd (Valhalla `truck`) quand les données OSM existent ;
 * null = non renseigné (valeurs Valhalla par défaut, jamais inventées côté Roadly).
 */
data class VehicleDimensions(
    val heightM: Double? = null,
    val widthM: Double? = null,
    val lengthM: Double? = null,
    val weightT: Double? = null,
    val axles: Int? = null,
)

/** [consumptionPer100] : L/100 km, ou kWh/100 km si [fuel] == ELECTRIC. [fuelPriceEur] : €/L ou €/kWh, saisi par l'utilisateur. */
data class VehicleProfile(
    val type: VehicleType,
    val fuel: FuelType,
    val consumptionPer100: Double,
    val fuelPriceEur: Double?,
    val dimensions: VehicleDimensions = VehicleDimensions(),
    val critAir: CritAir? = null,
)

data class CostEstimate(val fuelEur: Double, val tollEur: Double?, val totalEur: Double, val perKmEur: Double)
