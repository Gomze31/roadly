package fr.roadly.core.routing

import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCostCalculatorTest {
    @Test fun `100 km at 6 L per 100 and 1,80 euro gives 10,80`() {
        val c = FuelCostCalculator.estimate(100_000.0, VehicleProfile(VehicleType.SEDAN, FuelType.SP95, 6.0, 1.80))!!
        assertEquals(10.80, c.fuelEur, 0.001); assertEquals(10.80, c.totalEur, 0.001); assertEquals(0.108, c.perKmEur, 0.0001); assertNull(c.tollEur)
    }
    @Test fun `no price gives null`() = assertNull(FuelCostCalculator.estimate(1000.0, VehicleProfile(VehicleType.SEDAN, FuelType.DIESEL, 5.0, null)))
    @Test fun `zero consumption gives null`() = assertNull(FuelCostCalculator.estimate(1000.0, VehicleProfile(VehicleType.SEDAN, FuelType.DIESEL, 0.0, 1.7)))
}
