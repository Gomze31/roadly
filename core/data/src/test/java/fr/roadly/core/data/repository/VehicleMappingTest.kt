package fr.roadly.core.data.repository

import fr.roadly.core.data.db.VehicleProfileEntity
import fr.roadly.core.model.CritAir
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleDimensions
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleMappingTest {
    @Test fun `round trip keeps dimensions and crit air`() {
        val p = VehicleProfile(VehicleType.TRUCK, FuelType.DIESEL, 28.0, 1.70, VehicleDimensions(3.9, 2.5, 16.5, 38.0, 5), CritAir.C2)
        val back = p.toEntity().toModel()
        assertEquals(p.copy(), back)
    }

    @Test fun `legacy rows without the new columns and unknown enums fall back safely`() {
        val e = VehicleProfileEntity(1, "HOVERCRAFT", "SP95", 6.0, null, 0L)
        val m = e.toModel()
        assertEquals(VehicleType.SEDAN, m.type)
        assertEquals(VehicleDimensions(), m.dimensions)
        assertNull(m.critAir)
    }
}
