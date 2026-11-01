package fr.roadly.core.data.repository

import fr.roadly.core.data.db.ScooterProfileDao
import fr.roadly.core.data.db.ScooterProfileEntity
import fr.roadly.core.model.ComfortLevel
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.SuspensionType
import fr.roadly.core.model.TireType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Fiches trottinette (plusieurs profils) ; totalement indépendantes du profil voiture / moto. */
interface ScooterProfileRepository {
    val profiles: Flow<List<ElectricScooterProfile>>
    /** Crée ([ElectricScooterProfile.id] = 0) ou remplace ; renvoie l'identifiant. */
    suspend fun save(profile: ElectricScooterProfile): Long
    suspend fun delete(id: Long)
    /** Insère [preset] si aucune fiche n'existe ; renvoie son identifiant, ou null si des fiches existaient déjà. */
    suspend fun seedIfEmpty(preset: ElectricScooterProfile): Long?
}

@Singleton
class RoomScooterProfileRepository @Inject constructor(private val dao: ScooterProfileDao) : ScooterProfileRepository {
    override val profiles: Flow<List<ElectricScooterProfile>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    override suspend fun save(profile: ElectricScooterProfile): Long = dao.upsert(profile.toEntity(System.currentTimeMillis()))
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun seedIfEmpty(preset: ElectricScooterProfile): Long? = if (dao.count() == 0) save(preset.copy(id = 0)) else null
}

/** Valeurs d'énumération inconnues → UNKNOWN, jamais une exception. */
internal fun ScooterProfileEntity.toModel() = ElectricScooterProfile(
    id = id, name = name, brand = brand, model = model, maxSpeedKmh = maxSpeedKmh, cruiseSpeedKmh = cruiseSpeedKmh,
    claimedRangeKm = claimedRangeKm, claimedRangeConditions = claimedRangeConditions, batteryWh = batteryWh, batteryVoltage = batteryVoltage,
    scooterWeightKg = scooterWeightKg, riderWeightKg = riderWeightKg, maxLoadKg = maxLoadKg, wheelSizeInch = wheelSizeInch,
    tireType = runCatching { TireType.valueOf(tireType) }.getOrDefault(TireType.UNKNOWN),
    suspension = runCatching { SuspensionType.valueOf(suspension) }.getOrDefault(SuspensionType.UNKNOWN),
    comfort = runCatching { ComfortLevel.valueOf(comfort) }.getOrDefault(ComfortLevel.UNKNOWN),
    maxGradePct = maxGradePct, motorRatedW = motorRatedW, hasRegenerativeBraking = hasRegenerativeBraking, specSource = specSource,
)

internal fun ElectricScooterProfile.toEntity(nowMs: Long) = ScooterProfileEntity(
    id = id, name = name, brand = brand, model = model, maxSpeedKmh = maxSpeedKmh, cruiseSpeedKmh = cruiseSpeedKmh,
    claimedRangeKm = claimedRangeKm, claimedRangeConditions = claimedRangeConditions, batteryWh = batteryWh, batteryVoltage = batteryVoltage,
    scooterWeightKg = scooterWeightKg, riderWeightKg = riderWeightKg, maxLoadKg = maxLoadKg, wheelSizeInch = wheelSizeInch,
    tireType = tireType.name, suspension = suspension.name, comfort = comfort.name,
    maxGradePct = maxGradePct, motorRatedW = motorRatedW, hasRegenerativeBraking = hasRegenerativeBraking, specSource = specSource, updatedAt = nowMs,
)
