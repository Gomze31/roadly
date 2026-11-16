package fr.roadly.core.routing

import fr.roadly.core.model.FuelType
import fr.roadly.core.model.Route
import kotlin.math.roundToInt

/** Batterie estimée à l'arrivée : [usedKwh] consommés, [arrivalPct] restant (peut être négatif : l'autonomie ne suffit pas). */
data class EvArrival(val usedKwh: Double, val arrivalPct: Int) {
    val level: Level get() = when {
        arrivalPct < EvRange.CRITICAL_PCT -> Level.CRITICAL
        arrivalPct < EvRange.LOW_PCT -> Level.LOW
        else -> Level.OK
    }
    enum class Level { OK, LOW, CRITICAL }
}

/**
 * Autonomie d'un véhicule électrique sur un tracé : consommation du profil (kWh/100 km) pondérée par la vitesse de chaque tronçon
 * ([FuelSpeedModel], courbe électrique). Estimation : le froid, le relief, la charge et le style de conduite la font varier.
 */
object EvRange {
    const val LOW_PCT = 20
    const val CRITICAL_PCT = 10

    fun arrival(route: Route, consumptionKwhPer100: Double, batteryKwh: Double, startPct: Int): EvArrival? {
        if (consumptionKwhPer100 <= 0.0 || batteryKwh <= 0.0 || startPct !in 0..100) return null
        val used = FuelSpeedModel.equivalentMeters(route, FuelType.ELECTRIC) / 100_000.0 * consumptionKwhPer100
        val left = batteryKwh * startPct / 100.0 - used
        return EvArrival(used, (left / batteryKwh * 100).roundToInt())
    }
}
