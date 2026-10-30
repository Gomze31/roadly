package fr.roadly.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FavoritePlaceEntity::class, RecentSearchEntity::class, VehicleProfileEntity::class, SavedRouteEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RoadlyDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
}
