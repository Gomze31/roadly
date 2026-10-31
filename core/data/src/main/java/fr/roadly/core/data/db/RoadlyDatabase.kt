package fr.roadly.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoritePlaceEntity::class, RecentSearchEntity::class, VehicleProfileEntity::class, SavedRouteEntity::class, VigilanceZoneEntity::class, TripEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class RoadlyDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun vigilanceZoneDao(): VigilanceZoneDao
    abstract fun tripDao(): TripDao

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

        /** v2 → v3 : gabarit et Crit'Air du profil véhicule (LOT 7). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("heightM REAL", "widthM REAL", "lengthM REAL", "weightT REAL", "axles INTEGER", "critAir TEXT").forEach { column -> db.execSQL("ALTER TABLE `vehicle_profile` ADD COLUMN $column") }
            }
        }

        /** v3 → v4 : historique des trajets (LOT 9). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `destinationName` TEXT NOT NULL, `distanceM` REAL NOT NULL, `durationS` INTEGER NOT NULL, `maxSpeedKmh` REAL NOT NULL, `costEur` REAL, `arrived` INTEGER NOT NULL, `pointsCsv` TEXT NOT NULL)")
            }
        }
    }
}
