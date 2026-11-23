package fr.roadly.core.data.db

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** DAO sur une base Room en mémoire (TST-7) : écriture, remplacement, suppression, ordre et limites utilisés par les dépôts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaosTest {
    private lateinit var db: RoadlyDatabase

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RoadlyDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() = db.close()

    private fun favorite(id: String, createdAt: Long, name: String = id) = FavoritePlaceEntity(id, name, null, 48.0, 2.0, "ADDRESS", "ban", createdAt)
    private fun zone(id: String, lat: Double = 45.0, lon: Double = 4.0, source: String? = "dsr", status: String? = "ACTIVE", updatedAt: Long = 1) =
        VigilanceZoneEntity(id, lat, lon, null, null, "A7", 130, updatedAt, source = source, status = status)
    private fun version(fetchedAt: Long, status: String = "APPLIED") = RadarVersionEntity(
        fetchedAt = fetchedAt, status = status, sourceUrl = null, sourceModified = null, total = 0, added = 0, removed = 0, moved = 0,
        limitChanged = 0, enriched = 0, withDirection = 0, highPrecision = 0, rejected = 0, message = null,
    )
    private fun trip(startedAt: Long, points: String = "1,48.0,2.0,50.0;2,48.1,2.1,60.0") = TripEntity(
        startedAt = startedAt, endedAt = startedAt + 600_000, destinationName = "Gare", distanceM = 1200.0, durationS = 600, maxSpeedKmh = 87.0,
        costEur = 0.4, arrived = true, pointsCsv = points, travelMode = "CAR", ascentM = 12.0, batteryStartPct = 80, scooterProfileId = 3,
    )
    private fun scooter(name: String, id: Long = 0) = ScooterProfileEntity(
        id = id, name = name, brand = null, model = null, maxSpeedKmh = 25.0, cruiseSpeedKmh = null, claimedRangeKm = 70.0, claimedRangeConditions = null,
        batteryWh = 551.0, batteryVoltage = 36.0, scooterWeightKg = 24.3, riderWeightKg = null, maxLoadKg = 120.0, wheelSizeInch = 10.0,
        tireType = "PNEUMATIC_TUBELESS", suspension = "DUAL", comfort = "UNKNOWN", maxGradePct = 22.0, motorRatedW = 450.0, hasRegenerativeBraking = null,
        specSource = null, updatedAt = 0,
    )

    // --- Favoris ---

    @Test fun `favourites are listed newest first and upsert replaces the same id`() = runBlocking {
        val dao = db.favoriteDao()
        dao.upsert(favorite("a", 1)); dao.upsert(favorite("b", 3)); dao.upsert(favorite("c", 2))
        assertEquals(listOf("b", "c", "a"), dao.observeAll().first().map { it.id })
        dao.upsert(favorite("a", 1, name = "Renommé"))
        assertEquals(3, dao.observeAll().first().size)
        assertEquals("Renommé", dao.byId("a")!!.name)
        assertNull(dao.byId("absent"))
    }

    @Test fun `deleting a favourite removes only that one and clear removes all`() = runBlocking {
        val dao = db.favoriteDao()
        dao.upsert(favorite("a", 1)); dao.upsert(favorite("b", 2))
        dao.delete("a")
        assertEquals(listOf("b"), dao.observeAll().first().map { it.id })
        dao.delete("absent")
        dao.clear()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test fun `home is unique and moving it keeps the work flag of the new place`() = runBlocking {
        val dao = db.favoriteDao()
        assertNull(dao.observeHome().first())
        dao.setHome(favorite("a", 1))
        dao.setWork(favorite("b", 2))
        assertEquals("a", dao.observeHome().first()!!.id)
        assertEquals("b", dao.observeWork().first()!!.id)
        // Le domicile passe sur « b », qui reste aussi le travail ; « a » reste favori sans raccourci.
        dao.setHome(dao.byId("b")!!)
        assertEquals("b", dao.observeHome().first()!!.id)
        assertEquals("b", dao.observeWork().first()!!.id)
        assertFalse(dao.byId("a")!!.isHome)
        assertEquals(2, dao.observeAll().first().size)
        dao.setWork(dao.byId("a")!!)
        assertEquals("a", dao.observeWork().first()!!.id)
        assertTrue(dao.byId("b")!!.isHome)
        assertFalse(dao.byId("b")!!.isWork)
        dao.delete("b")
        assertNull(dao.observeHome().first())
    }

    // --- Recherches récentes ---

    @Test fun `recording the same place again keeps one row with the latest date`() = runBlocking {
        val dao = db.recentSearchDao()
        dao.record(RecentSearchEntity(placeId = "p1", placeJson = "{\"v\":1}", searchedAt = 10))
        dao.record(RecentSearchEntity(placeId = "p2", placeJson = "{}", searchedAt = 20))
        dao.record(RecentSearchEntity(placeId = "p1", placeJson = "{\"v\":2}", searchedAt = 30))
        val rows = dao.observe().first()
        assertEquals(listOf("p1", "p2"), rows.map { it.placeId })
        assertEquals("{\"v\":2}", rows.first().placeJson)
    }

    @Test fun `recent searches are capped at fifty and the oldest are dropped`() = runBlocking {
        val dao = db.recentSearchDao()
        for (i in 1..55) dao.record(RecentSearchEntity(placeId = "p$i", placeJson = "{}", searchedAt = i.toLong()))
        val rows = dao.observe().first()
        assertEquals(50, rows.size)
        assertEquals("p55", rows.first().placeId)
        assertEquals("p6", rows.last().placeId)
        dao.deleteByPlace("p55")
        assertEquals("p54", dao.observe().first().first().placeId)
        dao.clear()
        assertTrue(dao.observe().first().isEmpty())
    }

    // --- Profil véhicule ---

    @Test fun `the vehicle profile is a single row replaced on upsert`() = runBlocking {
        val dao = db.vehicleProfileDao()
        assertNull(dao.observe().first())
        dao.upsert(VehicleProfileEntity(type = "CAR", fuel = "DIESEL", consumptionPer100 = 5.4, fuelPriceEur = null, updatedAt = 1))
        dao.upsert(VehicleProfileEntity(type = "TRUCK", fuel = "DIESEL", consumptionPer100 = 28.0, fuelPriceEur = 1.7, updatedAt = 2, heightM = 3.8, axles = 3, critAir = "2"))
        val profile = dao.observe().first()!!
        assertEquals("TRUCK", profile.type); assertEquals(3.8, profile.heightM!!, 1e-9); assertEquals(3, profile.axles); assertEquals("2", profile.critAir)
        dao.clear()
        assertNull(dao.observe().first())
    }

    // --- Zones de vigilance et journal radar ---

    @Test fun `the box query returns active and legacy rows inside the box only`() = runBlocking {
        val dao = db.vigilanceZoneDao()
        dao.insertAll(listOf(
            zone("inside"), zone("legacy", source = null, status = null), zone("removed", status = "REMOVED"),
            zone("north", lat = 47.0), zone("east", lon = 6.0), zone("edge", lat = 46.0, lon = 5.0),
        ))
        assertEquals(setOf("inside", "legacy", "edge"), dao.inBox(44.0, 3.0, 46.0, 5.0).map { it.id }.toSet())
    }

    @Test fun `the count ignores removed rows and community reports`() = runBlocking {
        val dao = db.vigilanceZoneDao()
        assertEquals(0, dao.observeCount().first())
        assertNull(dao.observeUpdatedAt().first())
        dao.insertAll(listOf(zone("a", updatedAt = 5), zone("legacy", source = null, status = null, updatedAt = 9), zone("gone", status = "REMOVED"), zone("community", source = "mapatlas-camera-index")))
        assertEquals(2, dao.observeCount().first())
        assertEquals(9L, dao.observeUpdatedAt().first())
        assertEquals(setOf("a", "gone", "community"), dao.synced().map { it.id }.toSet())
    }

    @Test fun `applying a version drops legacy rows, replaces by id, marks removed and logs`() = runBlocking {
        val dao = db.vigilanceZoneDao()
        dao.replaceAll(listOf(zone("old1", source = null, status = null), zone("old2", source = null, status = null)))
        assertEquals(2, dao.observeCount().first())
        dao.applyVersion(listOf(zone("a"), zone("b")), emptyList(), 10, version(10))
        assertEquals(setOf("a", "b"), dao.synced().map { it.id }.toSet())
        assertEquals(2, dao.observeCount().first())
        dao.applyVersion(listOf(zone("a").copy(limitKmh = 110)), listOf("b"), 20, version(20))
        assertEquals(listOf("a"), dao.inBox(44.0, 3.0, 46.0, 5.0).map { it.id })
        assertEquals(110, dao.inBox(44.0, 3.0, 46.0, 5.0).single().limitKmh)
        val removed = dao.synced().single { it.id == "b" }
        assertEquals("REMOVED", removed.status); assertEquals(20L, removed.updatedAt)
        assertEquals(listOf(20L, 10L), dao.observeVersions(10).first().map { it.fetchedAt })
    }

    @Test fun `replaceAll swaps the whole table, also above one insert batch`() = runBlocking {
        val dao = db.vigilanceZoneDao()
        dao.insertAll(listOf(zone("before")))
        dao.replaceAll((1..1201).map { zone("z$it") })
        assertEquals(1201, dao.observeCount().first())
        assertTrue(dao.synced().none { it.id == "before" })
        dao.clear()
        assertEquals(0, dao.observeCount().first())
    }

    @Test fun `the sync log is newest first, limited, pruned to thirty, and knows the last success`() = runBlocking {
        val dao = db.vigilanceZoneDao()
        assertNull(dao.lastSuccess()); assertNull(dao.lastApplied())
        dao.logVersion(version(1, "APPLIED")); dao.logVersion(version(2, "UNCHANGED")); dao.logVersion(version(3, "FAILED"))
        assertEquals(listOf(3L, 2L), dao.observeVersions(2).first().map { it.fetchedAt })
        assertEquals(2L, dao.lastSuccess()!!.fetchedAt)
        assertEquals(1L, dao.lastApplied()!!.fetchedAt)
        for (i in 4..35) dao.logVersion(version(i.toLong(), "FAILED"))
        val all = dao.observeVersions(100).first()
        assertEquals(30, all.size)
        assertEquals(35L, all.first().fetchedAt); assertEquals(6L, all.last().fetchedAt)
    }

    // --- Trajets ---

    @Test fun `trip summaries are newest first without the track, byId returns the track`() = runBlocking {
        val dao = db.tripDao()
        val first = dao.insert(trip(1000))
        val second = dao.insert(trip(3000, points = "9,1.0,2.0,3.0"))
        val third = dao.insert(trip(2000))
        assertTrue(first != second && second != third)
        val summaries = dao.observeSummaries().first()
        assertEquals(listOf(second, third, first), summaries.map { it.id })
        assertTrue(summaries.all { it.pointsCsv.isEmpty() })
        // Tout le reste de la ligne est conservé dans le résumé.
        assertEquals(dao.byId(second)!!.copy(pointsCsv = ""), summaries.first())
        assertEquals("9,1.0,2.0,3.0", dao.byId(second)!!.pointsCsv)
        assertEquals(3L, summaries.first().scooterProfileId); assertEquals(80, summaries.first().batteryStartPct)
        assertNull(dao.byId(999))
    }

    @Test fun `trip delete, battery update and clear`() = runBlocking {
        val dao = db.tripDao()
        val a = dao.insert(trip(1000)); val b = dao.insert(trip(2000))
        dao.setBatteryEnd(a, 42)
        assertEquals(42, dao.byId(a)!!.batteryEndPct)
        assertNull(dao.byId(b)!!.batteryEndPct)
        dao.setBatteryEnd(a, null)
        assertNull(dao.byId(a)!!.batteryEndPct)
        dao.delete(a)
        assertEquals(listOf(b), dao.observeSummaries().first().map { it.id })
        // Un identifiant supprimé n'est jamais réattribué (AUTOINCREMENT) : un trajet ouvert ailleurs ne change pas de contenu.
        assertTrue(dao.insert(trip(3000)) > b)
        dao.clear()
        assertTrue(dao.observeSummaries().first().isEmpty())
    }

    // --- Fiches trottinette ---

    @Test fun `scooter profiles are ordered by id, replaced by id and deleted`() = runBlocking {
        val dao = db.scooterProfileDao()
        assertEquals(0, dao.count())
        val a = dao.upsert(scooter("A")); val b = dao.upsert(scooter("B"))
        assertEquals(listOf(a, b), dao.observeAll().first().map { it.id })
        assertEquals(a, dao.upsert(scooter("A2", id = a)))
        assertEquals(2, dao.count())
        assertEquals(listOf("A2", "B"), dao.observeAll().first().map { it.name })
        dao.delete(a)
        assertEquals(listOf("B"), dao.observeAll().first().map { it.name })
    }
}
