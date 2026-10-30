package fr.roadly.core.data.repository

import fr.roadly.core.data.db.VehicleProfileDao
import fr.roadly.core.data.db.VehicleProfileEntity
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface VehicleProfileRepository {
    val profile: Flow<VehicleProfile?>
    suspend fun save(profile: VehicleProfile)
    suspend fun clear()
}

@Singleton
class RoomVehicleProfileRepository @Inject constructor(private val dao: VehicleProfileDao) : VehicleProfileRepository {
    override val profile: Flow<VehicleProfile?> = dao.observe().map { e ->
        e?.let { VehicleProfile(VehicleType.valueOf(it.type), FuelType.valueOf(it.fuel), it.consumptionPer100, it.fuelPriceEur) }
    }
    override suspend fun save(profile: VehicleProfile) = dao.upsert(
        VehicleProfileEntity(1, profile.type.name, profile.fuel.name, profile.consumptionPer100, profile.fuelPriceEur, System.currentTimeMillis()),
    )
    override suspend fun clear() = dao.clear()
}
