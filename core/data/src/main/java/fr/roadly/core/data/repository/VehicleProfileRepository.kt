package fr.roadly.core.data.repository

import fr.roadly.core.data.db.VehicleProfileDao
import fr.roadly.core.data.db.VehicleProfileEntity
import fr.roadly.core.model.CritAir
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleDimensions
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
    override val profile: Flow<VehicleProfile?> = dao.observe().map { e -> e?.toModel() }
    override suspend fun save(profile: VehicleProfile) = dao.upsert(profile.toEntity())
    override suspend fun clear() = dao.clear()
}

/** Valeurs inconnues (anciens enregistrements, enum renommé) → profil par défaut sûr, jamais une exception. */
internal fun VehicleProfileEntity.toModel() = VehicleProfile(
    type = runCatching { VehicleType.valueOf(type) }.getOrDefault(VehicleType.SEDAN),
    fuel = runCatching { FuelType.valueOf(fuel) }.getOrDefault(FuelType.SP95),
    consumptionPer100 = consumptionPer100,
    fuelPriceEur = fuelPriceEur,
    dimensions = VehicleDimensions(heightM, widthM, lengthM, weightT, axles),
    critAir = critAir?.let { c -> runCatching { CritAir.valueOf(c) }.getOrNull() },
)

internal fun VehicleProfile.toEntity() = VehicleProfileEntity(
    1, type.name, fuel.name, consumptionPer100, fuelPriceEur, System.currentTimeMillis(),
    dimensions.heightM, dimensions.widthM, dimensions.lengthM, dimensions.weightT, dimensions.axles, critAir?.name,
)
