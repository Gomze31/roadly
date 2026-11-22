package fr.roadly.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Chaîne de migrations v1 → v7 (TST-6). Les schémas 1 à 6 n'ont jamais été exportés : ils sont reconstitués ici d'après les
 * entités de chaque version dans l'historique git (b1e0820 v1, 4b2d995 v2, 45e7016 v3, b3156c7 v4, ae84875 v5, 52f1af3 v6),
 * tels que Room les créait à l'installation. Une base est créée à chaque version, remplie, puis ouverte par Room en version
 * courante : Room valide alors lui-même le schéma migré contre les entités réelles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationChainTest {
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private companion object {
        const val FAVORITES = "CREATE TABLE IF NOT EXISTS `favorite_places` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `lat` REAL NOT NULL, " +
            "`lon` REAL NOT NULL, `kind` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isHome` INTEGER NOT NULL, `isWork` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        const val RECENTS = "CREATE TABLE IF NOT EXISTS `recent_searches` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `placeId` TEXT NOT NULL, " +
            "`placeJson` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL)"
        const val VEHICLE_V1 = "CREATE TABLE IF NOT EXISTS `vehicle_profile` (`id` INTEGER NOT NULL, `type` TEXT NOT NULL, `fuel` TEXT NOT NULL, " +
            "`consumptionPer100` REAL NOT NULL, `fuelPriceEur` REAL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        const val VEHICLE_V3 = "CREATE TABLE IF NOT EXISTS `vehicle_profile` (`id` INTEGER NOT NULL, `type` TEXT NOT NULL, `fuel` TEXT NOT NULL, " +
            "`consumptionPer100` REAL NOT NULL, `fuelPriceEur` REAL, `updatedAt` INTEGER NOT NULL, `heightM` REAL, `widthM` REAL, `lengthM` REAL, `weightT` REAL, " +
            "`axles` INTEGER, `critAir` TEXT, PRIMARY KEY(`id`))"
        const val SAVED_ROUTES = "CREATE TABLE IF NOT EXISTS `saved_routes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`originJson` TEXT NOT NULL, `destinationJson` TEXT NOT NULL, `waypointsJson` TEXT NOT NULL, `costing` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        const val ZONES_V2 = "CREATE TABLE IF NOT EXISTS `vigilance_zones` (`id` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `direction` TEXT, " +
            "`type` TEXT, `road` TEXT, `limitKmh` INTEGER, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        const val ZONES_V6 = "CREATE TABLE IF NOT EXISTS `vigilance_zones` (`id` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `direction` TEXT, " +
            "`type` TEXT, `road` TEXT, `limitKmh` INTEGER, `updatedAt` INTEGER NOT NULL, `kind` TEXT, `bearingDeg` REAL, `bothWays` INTEGER, `sectionLengthM` REAL, " +
            "`heavyLimitKmh` INTEGER, `commune` TEXT, `department` TEXT, `country` TEXT, `source` TEXT, `sourceVersion` TEXT, `enrichedFrom` TEXT, `commissionedAt` TEXT, " +
            "`verifiedAt` INTEGER, `precisionM` REAL, `confidence` INTEGER, `status` TEXT, PRIMARY KEY(`id`))"
        const val TRIPS_V4 = "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, " +
            "`destinationName` TEXT NOT NULL, `distanceM` REAL NOT NULL, `durationS` INTEGER NOT NULL, `maxSpeedKmh` REAL NOT NULL, `costEur` REAL, `arrived` INTEGER NOT NULL, " +
            "`pointsCsv` TEXT NOT NULL)"
        const val TRIPS_V5 = "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, " +
            "`destinationName` TEXT NOT NULL, `distanceM` REAL NOT NULL, `durationS` INTEGER NOT NULL, `maxSpeedKmh` REAL NOT NULL, `costEur` REAL, `arrived` INTEGER NOT NULL, " +
            "`pointsCsv` TEXT NOT NULL, `travelMode` TEXT, `ascentM` REAL, `descentM` REAL, `energyModelWh` REAL, `batteryStartPct` INTEGER, `batteryEndPct` INTEGER, " +
            "`scooterProfileId` INTEGER)"
        const val SCOOTERS_V5 = "CREATE TABLE IF NOT EXISTS `scooter_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, " +
            "`model` TEXT, `maxSpeedKmh` REAL NOT NULL, `cruiseSpeedKmh` REAL, `claimedRangeKm` REAL, `claimedRangeConditions` TEXT, `batteryWh` REAL, `batteryVoltage` REAL, " +
            "`scooterWeightKg` REAL, `riderWeightKg` REAL, `maxLoadKg` REAL, `wheelSizeInch` REAL, `tireType` TEXT NOT NULL, `suspension` TEXT NOT NULL, `comfort` TEXT NOT NULL, " +
            "`maxGradePct` REAL, `motorRatedW` REAL, `hasRegenerativeBraking` INTEGER, `specSource` TEXT, `updatedAt` INTEGER NOT NULL)"
        const val RADAR_VERSIONS_V6 = "CREATE TABLE IF NOT EXISTS `radar_versions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fetchedAt` INTEGER NOT NULL, " +
            "`status` TEXT NOT NULL, `sourceUrl` TEXT, `sourceModified` TEXT, `total` INTEGER NOT NULL, `added` INTEGER NOT NULL, `removed` INTEGER NOT NULL, " +
            "`moved` INTEGER NOT NULL, `limitChanged` INTEGER NOT NULL, `enriched` INTEGER NOT NULL, `withDirection` INTEGER NOT NULL, `highPrecision` INTEGER NOT NULL, " +
            "`rejected` INTEGER NOT NULL, `message` TEXT)"

        /** Schéma complet tel qu'une installation neuve le créait à la version [version] (1 à 6). */
        fun historicSchema(version: Int): List<String> = buildList {
            add(FAVORITES); add(RECENTS); add(SAVED_ROUTES)
            add(if (version >= 3) VEHICLE_V3 else VEHICLE_V1)
            if (version >= 2) add(if (version >= 6) ZONES_V6 else ZONES_V2)
            if (version >= 4) add(if (version >= 5) TRIPS_V5 else TRIPS_V4)
            if (version >= 5) add(SCOOTERS_V5)
            if (version >= 6) add(RADAR_VERSIONS_V6)
        }

        /** Lignes représentatives de ce qu'une base de la version [version] pouvait contenir. */
        fun historicRows(version: Int): List<String> = buildList {
            add("INSERT INTO favorite_places (id, name, address, lat, lon, kind, source, createdAt, isHome, isWork) VALUES ('fav-home', 'Maison', '1 rue de la Paix, Paris', 48.8686, 2.3314, 'ADDRESS', 'ban', 1000, 1, 0)")
            add("INSERT INTO favorite_places (id, name, address, lat, lon, kind, source, createdAt, isHome, isWork) VALUES ('fav-2', 'Salle d''escalade', NULL, 43.6, 1.44, 'POI', 'photon', 2000, 0, 0)")
            add("INSERT INTO recent_searches (placeId, placeJson, searchedAt) VALUES ('p1', '{\"id\":\"p1\"}', 3000)")
            add("INSERT INTO saved_routes (name, originJson, destinationJson, waypointsJson, costing, createdAt) VALUES ('Trajet travail', '{}', '{}', '[]', 'auto', 4000)")
            if (version >= 3) {
                add("INSERT INTO vehicle_profile (id, type, fuel, consumptionPer100, fuelPriceEur, updatedAt, heightM, widthM, lengthM, weightT, axles, critAir) VALUES (1, 'TRUCK', 'DIESEL', 28.5, 1.72, 5000, 3.8, 2.5, 12.0, 19.0, 3, '2')")
            } else {
                add("INSERT INTO vehicle_profile (id, type, fuel, consumptionPer100, fuelPriceEur, updatedAt) VALUES (1, 'CAR', 'DIESEL', 5.4, NULL, 5000)")
            }
            if (version >= 6) {
                add("INSERT INTO vigilance_zones (id, lat, lon, direction, type, road, limitKmh, updatedAt, kind, bearingDeg, bothWays, source, confidence, status) VALUES ('z1', 45.0, 4.0, 'Nord vers Sud', 'Radar fixe', 'A7', 130, 6000, 'FIXED', 180.0, 0, 'dsr', 85, 'ACTIVE')")
                add("INSERT INTO radar_versions (fetchedAt, status, sourceUrl, sourceModified, total, added, removed, moved, limitChanged, enriched, withDirection, highPrecision, rejected, message) VALUES (7000, 'APPLIED', 'https://example.invalid/radars.csv', NULL, 1, 1, 0, 0, 0, 0, 1, 0, 0, NULL)")
            } else if (version >= 2) {
                add("INSERT INTO vigilance_zones (id, lat, lon, direction, type, road, limitKmh, updatedAt) VALUES ('z1', 45.0, 4.0, 'Nord vers Sud', 'Radar fixe', 'A7', 130, 6000)")
            }
            if (version >= 5) {
                add("INSERT INTO scooter_profiles (name, brand, model, maxSpeedKmh, tireType, suspension, comfort, batteryWh, hasRegenerativeBraking, updatedAt) VALUES ('G2', 'Segway', 'G2 Max', 25.0, 'PNEUMATIC_TUBELESS', 'DUAL', 'UNKNOWN', 551.0, 1, 8000)")
                add("INSERT INTO trips (startedAt, endedAt, destinationName, distanceM, durationS, maxSpeedKmh, costEur, arrived, pointsCsv, travelMode, ascentM, batteryStartPct, scooterProfileId) VALUES (9000, 9600, 'Gare', 4200.5, 600, 24.0, NULL, 1, '9000,48.1,2.1,20.0;9300,48.2,2.2,22.0', 'SCOOTER', 35.0, 90, 1)")
            } else if (version >= 4) {
                add("INSERT INTO trips (startedAt, endedAt, destinationName, distanceM, durationS, maxSpeedKmh, costEur, arrived, pointsCsv) VALUES (9000, 9600, 'Gare', 4200.5, 600, 87.0, 0.42, 1, '9000,48.1,2.1,50.0;9300,48.2,2.2,80.0')")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            RoadlyDatabase.MIGRATION_1_2, RoadlyDatabase.MIGRATION_2_3, RoadlyDatabase.MIGRATION_3_4,
            RoadlyDatabase.MIGRATION_4_5, RoadlyDatabase.MIGRATION_5_6, RoadlyDatabase.MIGRATION_6_7,
        )
        const val CURRENT_VERSION = 7
    }

    private fun openHelper(name: String?, version: Int, statements: List<String>): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )

    /** Tables utilisateur → colonnes `nom|type|NOT NULL|rang dans la clé`. */
    private fun tables(db: SupportSQLiteDatabase): Map<String, Set<String>> {
        val names = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c -> while (c.moveToNext()) names += c.getString(0) }
        return names.filter { it !in setOf("android_metadata", "sqlite_sequence", "room_master_table") }.associateWith { table ->
            val out = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) out += "${c.getString(1)}|${c.getString(2)}|${c.getInt(3)}|${c.getInt(5)}" }
            out
        }
    }

    private fun tablesOf(statements: List<String>): Map<String, Set<String>> {
        val helper = openHelper(null, 1, statements)
        return try { tables(helper.writableDatabase) } finally { helper.close() }
    }

    /** Base de la version [version], créée et remplie comme à l'époque, puis fermée. */
    private fun createHistoricDatabase(name: String, version: Int) {
        ctx.deleteDatabase(name)
        val helper = openHelper(name, version, historicSchema(version))
        historicRows(version).forEach(helper.writableDatabase::execSQL)
        helper.close()
    }

    private fun openWithRoom(name: String): RoadlyDatabase =
        Room.databaseBuilder(ctx, RoadlyDatabase::class.java, name).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()

    private fun count(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }

    @Test fun `every migration step produces the schema Room created at that version`() {
        val helper = openHelper("steps.db", 1, historicSchema(1))
        val db = helper.writableDatabase
        historicRows(1).forEach(db::execSQL)
        assertEquals(tablesOf(historicSchema(1)), tables(db))
        ALL_MIGRATIONS.dropLast(1).forEach { migration ->
            migration.migrate(db)
            assertEquals("après la migration ${migration.startVersion} → ${migration.endVersion}", tablesOf(historicSchema(migration.endVersion)), tables(db))
        }
        RoadlyDatabase.MIGRATION_6_7.migrate(db)
        val fresh = Room.inMemoryDatabaseBuilder(ctx, RoadlyDatabase::class.java).allowMainThreadQueries().build()
        val expected = tables(fresh.openHelper.writableDatabase)
        fresh.close()
        assertEquals("après la migration 6 → 7", expected, tables(db))
        assertEquals(2, count(db, "SELECT COUNT(*) FROM favorite_places"))
        helper.close()
        ctx.deleteDatabase("steps.db")
    }

    @Test fun `version 2 to 3 keeps the vehicle profile and adds empty size columns`() {
        val helper = openHelper("v2.db", 2, historicSchema(2))
        val db = helper.writableDatabase
        historicRows(2).forEach(db::execSQL)
        RoadlyDatabase.MIGRATION_2_3.migrate(db)
        db.query("SELECT type, fuel, consumptionPer100, fuelPriceEur, heightM, axles, critAir FROM vehicle_profile WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("CAR", c.getString(0)); assertEquals("DIESEL", c.getString(1)); assertEquals(5.4, c.getDouble(2), 1e-9)
            assertTrue(c.isNull(3)); assertTrue(c.isNull(4)); assertTrue(c.isNull(5)); assertTrue(c.isNull(6))
        }
        helper.close()
        ctx.deleteDatabase("v2.db")
    }

    @Test fun `version 6 to 7 keeps radars and sync history and adds empty columns`() {
        val helper = openHelper("v6.db", 6, historicSchema(6))
        val db = helper.writableDatabase
        historicRows(6).forEach(db::execSQL)
        RoadlyDatabase.MIGRATION_6_7.migrate(db)
        db.query("SELECT road, limitKmh, source, status, directionHint FROM vigilance_zones WHERE id = 'z1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("A7", c.getString(0)); assertEquals(130, c.getInt(1)); assertEquals("dsr", c.getString(2)); assertEquals("ACTIVE", c.getString(3))
            assertTrue(c.isNull(4))
        }
        db.query("SELECT status, total, enrichment FROM radar_versions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("APPLIED", c.getString(0)); assertEquals(1, c.getInt(1)); assertTrue(c.isNull(2))
        }
        helper.close()
        ctx.deleteDatabase("v6.db")
    }

    @Test fun `a version 1 database opens in the current version with its data`() = assertUpgradeKeepsData(1)
    @Test fun `a version 2 database opens in the current version with its data`() = assertUpgradeKeepsData(2)
    @Test fun `a version 3 database opens in the current version with its data`() = assertUpgradeKeepsData(3)
    @Test fun `a version 4 database opens in the current version with its data`() = assertUpgradeKeepsData(4)
    @Test fun `a version 5 database opens in the current version with its data`() = assertUpgradeKeepsData(5)
    @Test fun `a version 6 database opens in the current version with its data`() = assertUpgradeKeepsData(6)

    /** Ouverture par Room : la validation du schéma migré contre les entités est faite par Room (exception sinon). */
    private fun assertUpgradeKeepsData(from: Int) = runBlocking {
        val name = "upgrade-from-$from.db"
        createHistoricDatabase(name, from)
        val db = openWithRoom(name)
        try {
            val raw = db.openHelper.writableDatabase
            assertEquals(CURRENT_VERSION, raw.version)

            val favorites = db.favoriteDao().observeAll().first()
            assertEquals(listOf("fav-2", "fav-home"), favorites.map { it.id })
            val home = db.favoriteDao().observeHome().first()!!
            assertEquals("Maison", home.name); assertEquals("1 rue de la Paix, Paris", home.address)
            assertEquals(48.8686, home.lat, 1e-9); assertEquals(2.3314, home.lon, 1e-9)
            assertEquals("ADDRESS", home.kind); assertEquals("ban", home.source); assertEquals(1000L, home.createdAt)
            assertNull(db.favoriteDao().byId("fav-2")!!.address)

            val recent = db.recentSearchDao().observe().first().single()
            assertEquals("p1", recent.placeId); assertEquals("{\"id\":\"p1\"}", recent.placeJson); assertEquals(3000L, recent.searchedAt)
            assertEquals(1, count(raw, "SELECT COUNT(*) FROM saved_routes WHERE name = 'Trajet travail' AND costing = 'auto'"))

            val vehicle = db.vehicleProfileDao().observe().first()!!
            if (from >= 3) {
                assertEquals("TRUCK", vehicle.type); assertEquals(3.8, vehicle.heightM!!, 1e-9); assertEquals(3, vehicle.axles); assertEquals("2", vehicle.critAir)
            } else {
                assertEquals("CAR", vehicle.type); assertEquals(5.4, vehicle.consumptionPer100, 1e-9); assertNull(vehicle.fuelPriceEur); assertNull(vehicle.heightM)
            }

            val zones = db.vigilanceZoneDao().inBox(44.0, 3.0, 46.0, 5.0)
            if (from >= 2) {
                val zone = zones.single()
                assertEquals("z1", zone.id); assertEquals("A7", zone.road); assertEquals(130, zone.limitKmh); assertEquals("Nord vers Sud", zone.direction)
                assertEquals(if (from >= 6) "dsr" else null, zone.source)
                assertNull(zone.directionHint)
            } else {
                assertTrue(zones.isEmpty())
            }
            val versions = db.vigilanceZoneDao().observeVersions(10).first()
            if (from >= 6) { assertEquals("APPLIED", versions.single().status); assertNull(versions.single().enrichment) } else assertTrue(versions.isEmpty())

            val summaries = db.tripDao().observeSummaries().first()
            if (from >= 4) {
                val trip = db.tripDao().byId(summaries.single().id)!!
                assertEquals("Gare", trip.destinationName); assertEquals(4200.5, trip.distanceM, 1e-9); assertEquals(600L, trip.durationS); assertTrue(trip.arrived)
                assertTrue(trip.pointsCsv.startsWith("9000,48.1,2.1,"))
                if (from >= 5) { assertEquals("SCOOTER", trip.travelMode); assertEquals(90, trip.batteryStartPct); assertEquals(1L, trip.scooterProfileId) }
                else { assertNull(trip.travelMode); assertEquals(0.42, trip.costEur!!, 1e-9) }
            } else {
                assertTrue(summaries.isEmpty())
            }
            val scooters = db.scooterProfileDao().observeAll().first()
            if (from >= 5) { assertEquals("G2", scooters.single().name); assertEquals(true, scooters.single().hasRegenerativeBraking) } else assertTrue(scooters.isEmpty())

            // La base migrée reste utilisable en écriture.
            db.favoriteDao().upsert(home.copy(id = "fav-new", isHome = false))
            assertNotNull(db.favoriteDao().byId("fav-new"))
        } finally {
            db.close()
            ctx.deleteDatabase(name)
        }
    }

    @Test fun `the schema of the current version is exported`() {
        val fresh = Room.inMemoryDatabaseBuilder(ctx, RoadlyDatabase::class.java).allowMainThreadQueries().build()
        val version = fresh.openHelper.readableDatabase.version
        fresh.close()
        assertEquals("ce test énumère les migrations jusqu'à la version $CURRENT_VERSION : ajoutez la nouvelle", CURRENT_VERSION, version)
        // Répertoire de travail des tests Gradle : le module.
        val exported = File("schemas/fr.roadly.core.data.db.RoadlyDatabase/$version.json")
        assertTrue("schéma exporté absent : ${exported.absolutePath}", exported.isFile)
        assertTrue(exported.readText().contains("\"version\": $version"))
    }
}
