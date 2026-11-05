package fr.roadly.core.data.db

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** v5 → v6 (Radar Intelligence) : schéma migré identique à celui que Room crée, lignes 2018 conservées, retirés jamais servis. */
@RunWith(AndroidJUnit4::class)
class RadarMigrationTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val out = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) out += "${c.getString(1)}|${c.getString(2)}|${c.getInt(3)}|${c.getInt(5)}" }
        return out
    }

    @Test fun migrated_schema_matches_room_schema() = runTest {
        val fresh = Room.inMemoryDatabaseBuilder(ctx, RoadlyDatabase::class.java).build()
        val expectedZones = columns(fresh.openHelper.writableDatabase, "vigilance_zones")
        val expectedVersions = columns(fresh.openHelper.writableDatabase, "radar_versions")
        fresh.close()

        ctx.deleteDatabase("radar-migration-test.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name("radar-migration-test.db").callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) = RoadlyDatabase.MIGRATION_1_2.migrate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO vigilance_zones (id, lat, lon, direction, type, road, limitKmh, updatedAt) VALUES ('12923', 45.0, 4.0, 'Nord vers Sud', 'Radar fixe', 'A7', 130, 1)")
        RoadlyDatabase.MIGRATION_5_6.migrate(db)
        assertEquals(expectedZones, columns(db, "vigilance_zones"))
        assertEquals(expectedVersions, columns(db, "radar_versions"))
        db.query("SELECT COUNT(*) FROM vigilance_zones WHERE source IS NULL").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        helper.close()
        ctx.deleteDatabase("radar-migration-test.db")
    }

    @Test fun removed_radars_are_kept_but_never_served() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ctx, RoadlyDatabase::class.java).build()
        val dao = db.vigilanceZoneDao()
        val a = VigilanceZoneEntity("a", 45.0, 4.0, null, null, null, 90, 1, source = "dsr", status = "ACTIVE", confidence = 85)
        val b = a.copy(id = "b")
        val version = RadarVersionEntity(fetchedAt = 1, status = "APPLIED", sourceUrl = null, sourceModified = null, total = 2, added = 2, removed = 0, moved = 0, limitChanged = 0, enriched = 0, withDirection = 0, highPrecision = 0, rejected = 0, message = null)
        dao.applyVersion(listOf(a, b), emptyList(), 1, version)
        dao.applyVersion(listOf(a), listOf("b"), 2, version.copy(fetchedAt = 2))
        assertEquals(listOf("a"), dao.inBox(44.0, 3.0, 46.0, 5.0).map { it.id })
        assertEquals(2, dao.synced().size)
        db.close()
    }
}
