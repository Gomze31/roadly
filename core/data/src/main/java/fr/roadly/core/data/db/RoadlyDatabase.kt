package fr.roadly.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoritePlaceEntity::class, RecentSearchEntity::class, VehicleProfileEntity::class, SavedRouteEntity::class, VigilanceZoneEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class RoadlyDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun vigilanceZoneDao(): VigilanceZoneDao

    companion object {
        /** v1 → v2 : table des zones de vigilance (LOT 4) ; les favoris et récents sont conservés. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vigilance_zones` (`id` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, " +
                        "`direction` TEXT, `type` TEXT, `road` TEXT, `limitKmh` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
