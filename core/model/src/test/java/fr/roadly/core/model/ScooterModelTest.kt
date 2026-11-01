package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class ScooterModelTest {
    private val car = VehicleProfile(VehicleType.SUV, FuelType.DIESEL, 6.5, 1.8)

    @Test fun travelModeDerivedFromExistingProfile() {
        assertEquals(TravelMode.CAR, TravelMode.derivedFrom(null))
        assertEquals(TravelMode.CAR, TravelMode.derivedFrom(car))
        assertEquals(TravelMode.MOTORCYCLE, TravelMode.derivedFrom(car.copy(type = VehicleType.MOTORCYCLE)))
    }

    @Test fun modesNeverMutateTheSavedProfile() {
        assertEquals(car, TravelMode.CAR.motorVehicle(car))
        assertEquals(car.copy(type = VehicleType.MOTORCYCLE), TravelMode.MOTORCYCLE.motorVehicle(car))
        assertEquals(VehicleType.SEDAN, TravelMode.CAR.motorVehicle(car.copy(type = VehicleType.MOTORCYCLE))!!.type)
        assertNull(TravelMode.ELECTRIC_SCOOTER.motorVehicle(car))
        // Moto sans profil saisi : aucun prix inventé (pas de coût estimable).
        assertNull(TravelMode.MOTORCYCLE.motorVehicle(null)!!.fuelPriceEur)
    }

    @Test fun cruiseSpeedDefaultsAndBounds() {
        val p = ElectricScooterProfile(name = "x", maxSpeedKmh = 25.0)
        assertEquals(20.0, p.effectiveCruiseKmh, 1e-9)
        assertEquals(25.0, p.copy(cruiseSpeedKmh = 40.0).effectiveCruiseKmh, 1e-9)
    }

    @Test fun weeklyAndMonthlyStats() {
        val zone = ZoneId.of("Europe/Paris")
        // Mercredi 2026-10-28 12:00 Paris.
        val now = java.time.ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        fun trip(daysAgo: Long, km: Double) = Trip(0, now - daysAgo * 86_400_000, now - daysAgo * 86_400_000 + 1_800_000, "x", km * 1000, 1800, 25.0, null, true, emptyList(), TravelMode.ELECTRIC_SCOOTER, ascentM = 10.0, energyModelWh = 50.0)
        val (week, month) = TripPeriodStats.weekAndMonth(listOf(trip(0, 5.0), trip(2, 3.0), trip(10, 7.0), trip(40, 9.0)), now, zone)
        assertEquals(2, week.trips)
        assertEquals(8000.0, week.distanceM, 1e-9)
        assertEquals(3, month.trips)
        assertEquals(30.0, month.ascentM!!, 1e-9)
        assertEquals(8.0, week.averageSpeedKmh, 1e-9)
    }
}
