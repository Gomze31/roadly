package fr.roadly.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String?,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val source: String,
    val createdAt: Long,
    val isHome: Boolean = false,
    val isWork: Boolean = false,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val placeId: String,
    val placeJson: String,
    val searchedAt: Long,
)

@Entity(tableName = "vehicle_profile")
data class VehicleProfileEntity(
    @PrimaryKey val id: Int = 1,
    val type: String,
    val fuel: String,
    val consumptionPer100: Double,
    val fuelPriceEur: Double?,
    val updatedAt: Long,
    val heightM: Double? = null,
    val widthM: Double? = null,
    val lengthM: Double? = null,
    val weightT: Double? = null,
    val axles: Int? = null,
    val critAir: String? = null,
)

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originJson: String,
    val destinationJson: String,
    val waypointsJson: String,
    val costing: String,
    val createdAt: Long,
)

/** Zone de vigilance (France) : point source du jeu officiel ; la zone (jamais le point) est reconstituée au guidage. */
@Entity(tableName = "vigilance_zones")
data class VigilanceZoneEntity(
    @PrimaryKey val id: String,
    val lat: Double,
    val lon: Double,
    val direction: String?,
    val type: String?,
    val road: String?,
    val limitKmh: Int?,
    val updatedAt: Long,
)

/** Trajet guidé terminé (historique, Replay) ; coût estimé d'après le profil véhicule au moment du trajet, null sans profil. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val destinationName: String,
    val distanceM: Double,
    val durationS: Long,
    val maxSpeedKmh: Double,
    val costEur: Double?,
    val arrived: Boolean,
    /** Points `t,lat,lon,speedKmh` séparés par `;` (échantillonnés tous les ≥ 20 m). */
    val pointsCsv: String,
    /** v5 : mode de déplacement (nom de `TravelMode`) ; null = trajet antérieur, voiture. */
    val travelMode: String? = null,
    val ascentM: Double? = null,
    val descentM: Double? = null,
    val energyModelWh: Double? = null,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
    val scooterProfileId: Long? = null,
)

/** Fiche trottinette (v5) ; plusieurs profils possibles, le profil actif est mémorisé dans DataStore. */
@Entity(tableName = "scooter_profiles")
data class ScooterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val model: String?,
    val maxSpeedKmh: Double,
    val cruiseSpeedKmh: Double?,
    val claimedRangeKm: Double?,
    val claimedRangeConditions: String?,
    val batteryWh: Double?,
    val batteryVoltage: Double?,
    val scooterWeightKg: Double?,
    val riderWeightKg: Double?,
    val maxLoadKg: Double?,
    val wheelSizeInch: Double?,
    val tireType: String,
    val suspension: String,
    val comfort: String,
    val maxGradePct: Double?,
    val motorRatedW: Double?,
    val hasRegenerativeBraking: Boolean?,
    val specSource: String?,
    val updatedAt: Long,
)
