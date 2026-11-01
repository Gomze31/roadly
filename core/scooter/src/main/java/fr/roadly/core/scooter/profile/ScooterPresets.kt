package fr.roadly.core.scooter.profile

import fr.roadly.core.model.ComfortLevel
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.SuspensionType
import fr.roadly.core.model.TireType

/** Profils préconfigurés ; chaque valeur reste modifiable dans la fiche véhicule. */
object ScooterPresets {
    /**
     * Segway Ninebot MAX G2 (version UE / France), d'après la fiche produit Segway Europe et le manuel constructeur :
     * 25 km/h, « jusqu'à 70 km » à 16 km/h avec un conducteur de 75 kg sur revêtement plat, batterie 551 Wh / 36 V,
     * moteur 450 W nominal (900 W crête), 24,3 kg, charge max. 120 kg, pneus 10" tubeless autoréparants,
     * suspension avant hydraulique et arrière à ressorts, pente max. 22 %.
     * Le freinage récupératif n'a pas été confirmé par les sources consultées : laissé inconnu.
     * L'autonomie annoncée est une valeur commerciale : elle n'est jamais utilisée comme autonomie réelle.
     */
    val NINEBOT_MAX_G2 = ElectricScooterProfile(
        name = "Segway Ninebot MAX G2",
        brand = "Segway-Ninebot",
        model = "MAX G2 (UE / France)",
        maxSpeedKmh = 25.0,
        cruiseSpeedKmh = 20.0,
        claimedRangeKm = 70.0,
        claimedRangeConditions = "Constructeur : jusqu'à 70 km à 16 km/h, conducteur de 75 kg, revêtement plat",
        batteryWh = 551.0,
        batteryVoltage = 36.0,
        scooterWeightKg = 24.3,
        riderWeightKg = null,
        maxLoadKg = 120.0,
        wheelSizeInch = 10.0,
        tireType = TireType.PNEUMATIC_TUBELESS,
        suspension = SuspensionType.DUAL,
        comfort = ComfortLevel.UNKNOWN,
        maxGradePct = 22.0,
        motorRatedW = 450.0,
        hasRegenerativeBraking = null,
        specSource = "https://eu-en.segway.com/ekickscooter/products/max-g2-e.html ; manuel Ninebot KickScooter Max G2 (support.segway.com)",
    )

    val all: List<ElectricScooterProfile> = listOf(NINEBOT_MAX_G2)
}
