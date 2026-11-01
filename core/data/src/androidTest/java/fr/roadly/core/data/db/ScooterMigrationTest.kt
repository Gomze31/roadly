package fr.roadly.core.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** v4 → v5 : le schéma migré doit être identique (nom, type, NOT NULL, clé) à celui que Room crée en v5. */
@RunWith(AndroidJUnit4::class)
class ScooterMigrationTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val out = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) out += "${c.getString(1)}|${c.getString(2)}|${c.getInt(3)}|${c.getInt(5)}"
        }
        return out
    }

    @Test fun migrated_schema_matches_room_schema() = runTest {
        val fresh = Room.inMemoryDatabaseBuilder(ctx, RoadlyDatabase::class.java).build()
        val expectedTrips = columns(fresh.openHelper.writableDatabase, "trips")
        val expectedScooter = columns(fresh.openHelper.writableDatabase, "scooter_profiles")
        fresh.close()

        ctx.deleteDatabase("migration-test.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name("migration-test.db").callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) = RoadlyDatabase.MIGRATION_3_4.migrate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO trips (startedAt, endedAt, destinationName, distanceM, durationS, maxSpeedKmh, costEur, arrived, pointsCsv) VALUES (1, 2, 'x', 100.0, 10, 20.0, NULL, 1, '')")
        RoadlyDatabase.MIGRATION_4_5.migrate(db)
        assertEquals(expectedTrips, columns(db, "trips"))
        assertEquals(expectedScooter, columns(db, "scooter_profiles"))
        db.query("SELECT travelMode FROM trips").use { c -> c.moveToFirst(); assertNull(c.getString(0)) }
        helper.close()
        ctx.deleteDatabase("migration-test.db")
    }

    @Test fun scooter_profiles_roundtrip() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoadlyDatabase::class.java).build()
        val id = db.scooterProfileDao().upsert(ScooterProfileEntity(name = "G2", brand = null, model = null, maxSpeedKmh = 25.0, cruiseSpeedKmh = null, claimedRangeKm = 70.0, claimedRangeConditions = null, batteryWh = 551.0, batteryVoltage = 36.0, scooterWeightKg = 24.3, riderWeightKg = null, maxLoadKg = 120.0, wheelSizeInch = 10.0, tireType = "PNEUMATIC_TUBELESS", suspension = "DUAL", comfort = "UNKNOWN", maxGradePct = 22.0, motorRatedW = 450.0, hasRegenerativeBraking = null, specSource = null, updatedAt = 0))
        assertEquals(listOf(id), db.scooterProfileDao().observeAll().first().map { it.id })
        db.close()
    }
}
