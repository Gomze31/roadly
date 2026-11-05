package fr.roadly.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FavoritePlaceEntity::class, RecentSearchEntity::class, VehicleProfileEntity::class, SavedRouteEntity::class, VigilanceZoneEntity::class, TripEntity::class, ScooterProfileEntity::class, RadarVersionEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class RoadlyDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun vigilanceZoneDao(): VigilanceZoneDao
    abstract fun tripDao(): TripDao
    abstract fun scooterProfileDao(): ScooterProfileDao

    companion object {
        /** v5 → v6 : Radar Intelligence — catégorie, sens, provenance, précision, confiance, statut ; journal des synchronisations. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "kind TEXT", "bearingDeg REAL", "bothWays INTEGER", "sectionLengthM REAL", "heavyLimitKmh INTEGER", "commune TEXT", "department TEXT",
                    "country TEXT", "source TEXT", "sourceVersion TEXT", "enrichedFrom TEXT", "commissionedAt TEXT", "verifiedAt INTEGER", "precisionM REAL",
                    "confidence INTEGER", "status TEXT",
                ).forEach { column -> db.execSQL("ALTER TABLE `vigilance_zones` ADD COLUMN $column") }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `radar_versions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fetchedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`sourceUrl` TEXT, `sourceModified` TEXT, `total` INTEGER NOT NULL, `added` INTEGER NOT NULL, `removed` INTEGER NOT NULL, `moved` INTEGER NOT NULL, " +
                        "`limitChanged` INTEGER NOT NULL, `enriched` INTEGER NOT NULL, `withDirection` INTEGER NOT NULL, `highPrecision` INTEGER NOT NULL, " +
                        "`rejected` INTEGER NOT NULL, `message` TEXT)",
                )
            }
        }

        /** v6 → v7 : sens indicatif des signalements communautaires, sources d'enrichissement de chaque synchronisation. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vigilance_zones` ADD COLUMN directionHint INTEGER")
                db.execSQL("ALTER TABLE `radar_versions` ADD COLUMN enrichment TEXT")
            }
        }

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

        /** v4 → v5 : mode trottinette (LOT 13) — fiches trottinette, colonnes trajet (mode, dénivelé, énergie, batterie). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("travelMode TEXT", "ascentM REAL", "descentM REAL", "energyModelWh REAL", "batteryStartPct INTEGER", "batteryEndPct INTEGER", "scooterProfileId INTEGER")
                    .forEach { column -> db.execSQL("ALTER TABLE `trips` ADD COLUMN $column") }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scooter_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `model` TEXT, " +
                        "`maxSpeedKmh` REAL NOT NULL, `cruiseSpeedKmh` REAL, `claimedRangeKm` REAL, `claimedRangeConditions` TEXT, `batteryWh` REAL, `batteryVoltage` REAL, " +
                        "`scooterWeightKg` REAL, `riderWeightKg` REAL, `maxLoadKg` REAL, `wheelSizeInch` REAL, `tireType` TEXT NOT NULL, `suspension` TEXT NOT NULL, " +
                        "`comfort` TEXT NOT NULL, `maxGradePct` REAL, `motorRatedW` REAL, `hasRegenerativeBraking` INTEGER, `specSource` TEXT, `updatedAt` INTEGER NOT NULL)",
                )
            }
        }
    }
}
