package fr.roadly.core.scooter.range

import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.TireType
import fr.roadly.core.scooter.segment.ScooterRouteSegment
import fr.roadly.core.scooter.segment.SurfaceQuality
import kotlin.math.max
import kotlin.math.sqrt

/** Portion de trajet pour le calcul d'énergie : longueur, pente (%), facteur de roulement selon le revêtement. */
data class EnergyLeg(val lengthM: Double, val gradePct: Double = 0.0, val rollingFactor: Double = 1.0)

enum class RangeRisk(val label: String) {
    OK("Autonomie suffisante"),
    TIGHT("Autonomie juste"),
    INSUFFICIENT("Autonomie insuffisante"),
    UNKNOWN("Batterie inconnue"),
}

/** Calibration issue des trajets réels : énergie observée / énergie prévue par le modèle (moyenne géométrique pondérée). */
data class RangeCalibration(val factor: Double = 1.0, val trips: Int = 0) {
    val calibrated: Boolean get() = trips > 0

    companion object {
        const val MIN_OBSERVED_WH = 20.0

        /** [samples] = (énergie prévue par le modèle non calibré, énergie observée d'après la batterie saisie) en Wh. */
        fun fromSamples(samples: List<Pair<Double, Double>>): RangeCalibration {
            val valid = samples.filter { (pred, obs) -> pred > 0 && obs >= MIN_OBSERVED_WH }
            if (valid.isEmpty()) return RangeCalibration()
            val weight = valid.sumOf { it.first }
            val logMean = valid.sumOf { (pred, obs) -> kotlin.math.ln(obs / pred) * pred } / weight
            return RangeCalibration(kotlin.math.exp(logMean).coerceIn(0.5, 2.5), valid.size)
        }
    }
}

data class RangeEstimate(
    val risk: RangeRisk,
    /** Énergie nécessaire au trajet (Wh), valeur centrale puis borne pessimiste. */
    val routeEnergyWh: Double,
    val routeEnergyHighWh: Double,
    val usableWh: Double?,
    val startBatteryPct: Double?,
    val arrivalBatteryPct: Double?,
    val arrivalBatteryPctPessimistic: Double?,
    /** Distance encore possible sur le plat à la vitesse de croisière avec la batterie restante (km). */
    val remainingRangeKm: Double?,
    val calibration: RangeCalibration,
    /** Hypothèses utilisées, toujours affichées avec l'estimation. */
    val assumptions: List<String>,
)

/**
 * Modèle physique de consommation (roulement + traînée + pente, rendement global) :
 * E = (Crr·m·g·cosθ + m·g·sinθ + ½·ρ·CdA·v²)·d / η ; en descente, aucune énergie récupérée sauf freinage
 * récupératif déclaré (15 %). Paramètres par défaut issus d'ordres de grandeur publiés, tous listés dans
 * [RangeEstimate.assumptions] et corrigés par la [calibration] dès qu'un trajet réel a été renseigné.
 * Le niveau de batterie n'est jamais lu sur la trottinette : il est saisi par l'utilisateur.
 */
class ScooterRangeEstimator(
    private val profile: ElectricScooterProfile,
    private val calibration: RangeCalibration = RangeCalibration(),
) {
    companion object {
        const val G = 9.81
        const val DEFAULT_RIDER_KG = 75.0
        const val DEFAULT_SCOOTER_KG = 20.0
        const val CDA_M2 = 0.6
        const val EFFICIENCY = 0.75
        const val REGEN_EFFICIENCY = 0.15
        /** Part de la capacité nominale réellement utilisable (BMS, tension de coupure). */
        const val USABLE_FRACTION = 0.9
        const val RESERVE_PCT = 10.0
        /** Incertitude relative du modèle sans calibration. */
        const val UNCALIBRATED_UNCERTAINTY = 0.25
        const val MIN_UNCERTAINTY = 0.08

        fun rollingFactor(surface: SurfaceQuality): Double = when (surface) {
            SurfaceQuality.SMOOTH, SurfaceQuality.GOOD, SurfaceQuality.UNKNOWN -> 1.0
            SurfaceQuality.ROUGH -> 1.4
            SurfaceQuality.UNPAVED -> 1.8
            SurfaceQuality.IMPASSABLE -> 2.5
        }

        /** Capacité utilisable selon la température (Li-ion) : 100 % ≥ 20 °C, 95 % à 10 °C, 85 % à 0 °C, 70 % à −10 °C. */
        fun temperatureFactor(celsius: Double?): Double {
            val t = celsius ?: return 1.0
            val pts = listOf(-10.0 to 0.70, 0.0 to 0.85, 10.0 to 0.95, 20.0 to 1.0)
            if (t <= pts.first().first) return pts.first().second
            if (t >= pts.last().first) return 1.0
            val i = pts.indexOfFirst { it.first >= t }
            val (t0, f0) = pts[i - 1]; val (t1, f1) = pts[i]
            return f0 + (f1 - f0) * (t - t0) / (t1 - t0)
        }

        /** Portions d'énergie depuis les segments analysés (pente et revêtement connus) ; pente inconnue = plat. */
        fun legsFrom(segments: List<ScooterRouteSegment>): List<EnergyLeg> =
            segments.map { EnergyLeg(it.lengthM, it.gradePct ?: 0.0, rollingFactor(it.surface)) }

        /** Portions depuis un profil d'altitude échantillonné tous les [intervalM] (aucune info de revêtement). */
        fun legsFromElevation(elevationM: List<Double>, intervalM: Double): List<EnergyLeg> =
            (1 until elevationM.size).map { i -> EnergyLeg(intervalM, (elevationM[i] - elevationM[i - 1]) / intervalM * 100.0) }
    }

    private val massKg: Double get() = (profile.riderWeightKg ?: DEFAULT_RIDER_KG) + (profile.scooterWeightKg ?: DEFAULT_SCOOTER_KG)
    private val crr: Double get() = when (profile.tireType) {
        TireType.SOLID, TireType.HONEYCOMB -> 0.018
        TireType.PNEUMATIC_TUBELESS, TireType.PNEUMATIC_TUBE -> 0.012
        TireType.UNKNOWN -> 0.014
    }

    private fun airDensity(celsius: Double?): Double = 353.0 / ((celsius ?: 15.0) + 273.15)

    /** Énergie du modèle non calibré (Wh) à [speedKmh]. */
    fun modelEnergyWh(legs: List<EnergyLeg>, speedKmh: Double, temperatureC: Double? = null): Double {
        val v = speedKmh / 3.6
        val aero = 0.5 * airDensity(temperatureC) * CDA_M2 * v * v
        val regen = if (profile.hasRegenerativeBraking == true) REGEN_EFFICIENCY else 0.0
        var joules = 0.0
        for (l in legs) {
            val slope = l.gradePct / 100.0
            val cos = 1.0 / sqrt(1 + slope * slope)
            val sin = slope * cos
            val force = crr * l.rollingFactor * massKg * G * cos + massKg * G * sin + aero
            joules += if (force >= 0) force * l.lengthM / EFFICIENCY else force * l.lengthM * regen
        }
        return max(0.0, joules) / 3600.0
    }

    /** Énergie calibrée (Wh). */
    fun energyWh(legs: List<EnergyLeg>, speedKmh: Double, temperatureC: Double? = null): Double =
        modelEnergyWh(legs, speedKmh, temperatureC) * calibration.factor

    val uncertainty: Double get() = if (!calibration.calibrated) UNCALIBRATED_UNCERTAINTY
        else max(MIN_UNCERTAINTY, UNCALIBRATED_UNCERTAINTY / sqrt(calibration.trips + 1.0))

    /** Capacité utilisable (Wh) et l'hypothèse qui la justifie ; null si ni capacité ni autonomie annoncée ne sont connues. */
    fun usableCapacity(temperatureC: Double?): Pair<Double, String>? {
        val tf = temperatureFactor(temperatureC)
        profile.batteryWh?.let { return it * USABLE_FRACTION * tf to "Capacité utilisable : ${USABLE_FRACTION.times(100).toInt()} % des ${it.toInt()} Wh nominaux" }
        val claimed = profile.claimedRangeKm ?: return null
        // Capacité déduite de l'autonomie commerciale aux conditions usuelles d'essai (16 km/h, 75 kg, plat) : optimiste.
        val whPerKm = ScooterRangeEstimator(profile.copy(riderWeightKg = DEFAULT_RIDER_KG), RangeCalibration()).modelEnergyWh(listOf(EnergyLeg(1000.0)), 16.0, 25.0)
        return claimed * whPerKm * tf to "Capacité inconnue : déduite de l'autonomie annoncée (${claimed.toInt()} km), probablement optimiste"
    }

    fun estimate(legs: List<EnergyLeg>, startBatteryPct: Double?, temperatureC: Double?, speedKmh: Double = profile.effectiveCruiseKmh): RangeEstimate {
        val energy = energyWh(legs, speedKmh, temperatureC)
        val high = energy * (1 + uncertainty)
        val assumptions = buildList {
            add("Vitesse moyenne ${speedKmh.toInt()} km/h")
            add(if (profile.riderWeightKg == null) "Poids du conducteur inconnu : ${DEFAULT_RIDER_KG.toInt()} kg supposés" else "Conducteur ${profile.riderWeightKg!!.toInt()} kg")
            if (profile.scooterWeightKg == null) add("Poids de la trottinette inconnu : ${DEFAULT_SCOOTER_KG.toInt()} kg supposés")
            add(if (temperatureC == null) "Température inconnue : non prise en compte" else "Température ${temperatureC.toInt()} °C")
            add(if (calibration.calibrated) "Modèle calibré sur ${calibration.trips} trajet(s) réel(s) (×${String.format(java.util.Locale.FRANCE, "%.2f", calibration.factor)})" else "Modèle non calibré : marge de ±${(UNCALIBRATED_UNCERTAINTY * 100).toInt()} %")
            if (legs.all { it.gradePct == 0.0 }) add("Dénivelé inconnu : trajet supposé plat")
        }
        val capacity = usableCapacity(temperatureC)
        if (startBatteryPct == null || capacity == null) {
            return RangeEstimate(RangeRisk.UNKNOWN, energy, high, capacity?.first, startBatteryPct, null, null, null, calibration,
                assumptions + listOfNotNull(capacity?.second, if (startBatteryPct == null) "Niveau de batterie non saisi" else "Capacité de batterie inconnue"))
        }
        val (usable, capNote) = capacity
        val pct = startBatteryPct.coerceIn(0.0, 100.0)
        val availableWh = usable * pct / 100.0
        val arrival = (availableWh - energy) / usable * 100.0
        val arrivalLow = (availableWh - high) / usable * 100.0
        val flatPerKm = energyWh(listOf(EnergyLeg(1000.0)), speedKmh, temperatureC).coerceAtLeast(1e-6)
        val risk = when {
            arrival < 0 -> RangeRisk.INSUFFICIENT
            arrivalLow < RESERVE_PCT -> RangeRisk.TIGHT
            else -> RangeRisk.OK
        }
        return RangeEstimate(risk, energy, high, usable, pct, arrival, arrivalLow, availableWh / flatPerKm, calibration,
            assumptions + capNote + "Batterie saisie manuellement : ${pct.toInt()} %")
    }

    /** Pourcentage de batterie consommé pour [energyWh] (inverse de l'estimation), null si capacité inconnue. */
    fun pctFor(energyWh: Double, temperatureC: Double?): Double? = usableCapacity(temperatureC)?.first?.let { energyWh / it * 100.0 }
}
