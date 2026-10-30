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
