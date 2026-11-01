package fr.roadly.core.scooter

import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.range.EnergyLeg
import fr.roadly.core.scooter.range.RangeCalibration
import fr.roadly.core.scooter.range.RangeRisk
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeEstimatorTest {
    private val g2 = ScooterPresets.NINEBOT_MAX_G2.copy(riderWeightKg = 75.0)

    @Test fun modelIsConsistentWithManufacturerTestConditions() {
        // Constructeur : jusqu'à 70 km à 16 km/h, 75 kg, plat. Le modèle doit rester dans ±15 % sans y être forcé.
        val est = ScooterRangeEstimator(g2)
        val whPerKm = est.modelEnergyWh(listOf(EnergyLeg(1000.0)), 16.0, 25.0)
        val km = 551.0 * ScooterRangeEstimator.USABLE_FRACTION / whPerKm
        assertTrue("autonomie modèle $km km", km in 59.5..80.5)
    }

    @Test fun climbingAndSpeedCostMoreEnergy() {
        val est = ScooterRangeEstimator(g2)
        val flat = est.modelEnergyWh(listOf(EnergyLeg(1000.0)), 20.0)
        assertTrue(est.modelEnergyWh(listOf(EnergyLeg(1000.0, gradePct = 6.0)), 20.0) > flat * 2)
        assertTrue(est.modelEnergyWh(listOf(EnergyLeg(1000.0)), 25.0) > flat)
        assertTrue(est.modelEnergyWh(listOf(EnergyLeg(1000.0, rollingFactor = 1.8)), 20.0) > flat)
        // Descente sans freinage récupératif déclaré : aucune énergie « gagnée ».
        assertEquals(0.0, est.modelEnergyWh(listOf(EnergyLeg(1000.0, gradePct = -8.0)), 20.0), 1e-9)
    }

    @Test fun insufficientBatteryIsDetected() {
        val est = ScooterRangeEstimator(g2)
        val r = est.estimate(listOf(EnergyLeg(20_000.0)), startBatteryPct = 15.0, temperatureC = 20.0)
        assertEquals(RangeRisk.INSUFFICIENT, r.risk)
        assertTrue(r.arrivalBatteryPct!! < 0)
    }

    @Test fun okAndTightBands() {
        val est = ScooterRangeEstimator(g2)
        assertEquals(RangeRisk.OK, est.estimate(listOf(EnergyLeg(5_000.0)), 90.0, 20.0).risk)
        assertEquals(RangeRisk.TIGHT, est.estimate(listOf(EnergyLeg(30_000.0)), 60.0, 20.0).risk)
    }

    @Test fun unknownBatteryNeverClaimsALevel() {
        val r = ScooterRangeEstimator(g2).estimate(listOf(EnergyLeg(5_000.0)), startBatteryPct = null, temperatureC = null)
        assertEquals(RangeRisk.UNKNOWN, r.risk)
        assertNull(r.arrivalBatteryPct)
        assertTrue(r.assumptions.any { it.contains("non saisi") })
    }

    @Test fun coldReducesUsableCapacity() {
        val est = ScooterRangeEstimator(g2)
        assertTrue(est.usableCapacity(0.0)!!.first < est.usableCapacity(20.0)!!.first)
        assertEquals(0.85, ScooterRangeEstimator.temperatureFactor(0.0), 1e-9)
        assertEquals(0.9, ScooterRangeEstimator.temperatureFactor(5.0), 1e-9)
    }

    @Test fun capacityFromClaimedRangeIsFlagged() {
        val est = ScooterRangeEstimator(g2.copy(batteryWh = null))
        val (wh, note) = est.usableCapacity(20.0)!!
        assertTrue(wh > 300)
        assertTrue(note.contains("optimiste"))
        assertNull(ScooterRangeEstimator(g2.copy(batteryWh = null, claimedRangeKm = null)).usableCapacity(20.0))
    }

    @Test fun calibrationFromRealTrips() {
        val c = RangeCalibration.fromSamples(listOf(100.0 to 130.0, 200.0 to 260.0, 50.0 to 5.0))
        assertEquals(2, c.trips) // l'échantillon de 5 Wh observés est trop faible et ignoré
        assertEquals(1.3, c.factor, 1e-6)
        val calibrated = ScooterRangeEstimator(g2, c)
        assertEquals(calibrated.modelEnergyWh(listOf(EnergyLeg(1000.0)), 20.0) * 1.3, calibrated.energyWh(listOf(EnergyLeg(1000.0)), 20.0), 1e-9)
        assertTrue(calibrated.uncertainty < ScooterRangeEstimator(g2).uncertainty)
    }
}
