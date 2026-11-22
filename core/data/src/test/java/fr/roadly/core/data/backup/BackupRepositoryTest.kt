package fr.roadly.core.data.backup

import androidx.room.Room
import fr.roadly.core.data.db.FavoritePlaceEntity
import fr.roadly.core.data.db.RoadlyDatabase
import fr.roadly.core.data.db.TripEntity
import kotlinx.coroutines.Dispatchers
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** Export puis import sur de vraies bases Room en mémoire : ce qu'un utilisateur retrouve après une réinstallation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {
    private lateinit var phone: RoadlyDatabase
    private lateinit var reinstalled: RoadlyDatabase

    @Before fun setUp() {
        phone = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RoadlyDatabase::class.java).allowMainThreadQueries().build()
        reinstalled = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RoadlyDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { phone.close(); reinstalled.close() }

    private fun repository(db: RoadlyDatabase, trackBudget: Long = RoomBackupRepository.TRACK_BUDGET_CHARS) =
        RoomBackupRepository(db.favoriteDao(), db.tripDao(), "0.1.215", Dispatchers.Unconfined, { 777L }, trackBudget)

    private fun favorite(id: String, createdAt: Long, home: Boolean = false, work: Boolean = false, name: String = id) =
        FavoritePlaceEntity(id, name, "adresse de $id", 48.0, 2.0, "ADDRESS", "ban", createdAt, home, work)
    private fun trip(startedAt: Long, points: String = "0,48.00000,2.00000,50.0;900,48.10000,2.10000,60.0", scooter: Long? = null) = TripEntity(
        startedAt = startedAt, endedAt = startedAt + 600_000, destinationName = "Gare $startedAt", distanceM = 1200.5, durationS = 600, maxSpeedKmh = 87.0,
        costEur = 0.4, arrived = true, pointsCsv = points, travelMode = if (scooter != null) "SCOOTER" else "CAR", ascentM = 12.0, descentM = 3.0, energyModelWh = 41.0,
        batteryStartPct = 80, batteryEndPct = 61, scooterProfileId = scooter,
    )

    private suspend fun exported(db: RoadlyDatabase, trackBudget: Long = RoomBackupRepository.TRACK_BUDGET_CHARS): Pair<ByteArray, ExportSummary> {
        val out = ByteArrayOutputStream()
        val result = repository(db, trackBudget).export { out }
        return out.toByteArray() to (result as BackupResult.Done).value
    }
    private suspend fun imported(db: RoadlyDatabase, bytes: ByteArray) = repository(db).import { bytes.inputStream() }

    @Test fun `favourites and trips come back after a reinstall`() = runBlocking {
        phone.favoriteDao().upsert(favorite("home", 1, home = true)); phone.favoriteDao().upsert(favorite("work", 2, work = true)); phone.favoriteDao().upsert(favorite("gym", 3))
        val car = phone.tripDao().insert(trip(1_000)); phone.tripDao().insert(trip(2_000, scooter = 4))
        val (bytes, summary) = exported(phone)
        assertEquals(ExportSummary(favorites = 3, trips = 2), summary)

        val result = (imported(reinstalled, bytes) as BackupResult.Done).value
        assertEquals(ImportSummary(favorites = 3, tripsAdded = 2, tripsAlreadyPresent = 0, rejectedRows = 0), result)
        assertEquals(phone.favoriteDao().all(), reinstalled.favoriteDao().all())
        assertEquals("home", reinstalled.favoriteDao().observeHome().first()!!.id)
        assertEquals("work", reinstalled.favoriteDao().observeWork().first()!!.id)
        val before = phone.tripDao().byId(car)!!
        val after = reinstalled.tripDao().observeSummaries().first().map { reinstalled.tripDao().byId(it.id)!! }
        assertEquals(listOf(2_000L, 1_000L), after.map { it.startedAt })
        // Tout le trajet revient, tracé compris ; seuls l'identifiant local et la fiche trottinette ne sont pas repris.
        assertEquals(before.copy(id = 0), after.last().copy(id = 0))
        assertNull(after.first().scooterProfileId)
        assertEquals("SCOOTER", after.first().travelMode)
    }

    @Test fun `the exported file is the versioned document and holds no setting`() = runBlocking {
        phone.favoriteDao().upsert(favorite("a", 1))
        val (bytes, _) = exported(phone)
        val document = (BackupCodec.decode(bytes) as BackupResult.Done).value.document
        assertEquals(BackupCodec.FORMAT, document.format); assertEquals(BackupCodec.FORMAT_VERSION, document.formatVersion)
        assertEquals(777L, document.exportedAtMs); assertEquals("0.1.215", document.appVersion)
    }

    @Test fun `importing twice adds nothing the second time`() = runBlocking {
        phone.favoriteDao().upsert(favorite("a", 1)); phone.tripDao().insert(trip(1_000)); phone.tripDao().insert(trip(2_000))
        val (bytes, _) = exported(phone)
        imported(reinstalled, bytes)
        val second = (imported(reinstalled, bytes) as BackupResult.Done).value
        assertEquals(ImportSummary(favorites = 1, tripsAdded = 0, tripsAlreadyPresent = 2, rejectedRows = 0), second)
        assertEquals(2, reinstalled.tripDao().observeSummaries().first().size)
        assertEquals(1, reinstalled.favoriteDao().all().size)
        // Importer sur l'appareil d'origine ne duplique rien non plus.
        assertEquals(0, (imported(phone, bytes) as BackupResult.Done).value.tripsAdded)
    }

    @Test fun `import never deletes or overwrites what is only on the device`() = runBlocking {
        phone.favoriteDao().upsert(favorite("shared", 1, name = "Nom de la sauvegarde")); phone.favoriteDao().upsert(favorite("old-home", 2, home = true))
        phone.tripDao().insert(trip(1_000))
        val (bytes, _) = exported(phone)

        reinstalled.favoriteDao().upsert(favorite("local", 5)); reinstalled.favoriteDao().upsert(favorite("new-home", 6, home = true, work = true))
        reinstalled.favoriteDao().upsert(favorite("shared", 7, name = "Nom local"))
        val localTrip = reinstalled.tripDao().insert(trip(9_000, points = "tracé local"))
        imported(reinstalled, bytes)

        val ids = reinstalled.favoriteDao().all().map { it.id }.toSet()
        assertEquals(setOf("local", "new-home", "shared", "old-home"), ids)
        // Même identifiant : la sauvegarde met le favori à jour.
        assertEquals("Nom de la sauvegarde", reinstalled.favoriteDao().byId("shared")!!.name)
        // Le domicile et le travail de l'appareil restent en place : jamais deux domiciles.
        assertEquals("new-home", reinstalled.favoriteDao().observeHome().first()!!.id)
        assertEquals("new-home", reinstalled.favoriteDao().observeWork().first()!!.id)
        assertFalse(reinstalled.favoriteDao().byId("old-home")!!.isHome)
        assertEquals("tracé local", reinstalled.tripDao().byId(localTrip)!!.pointsCsv)
        assertEquals(2, reinstalled.tripDao().observeSummaries().first().size)
    }

    @Test fun `updating the favourite that is home on the device keeps it home`() = runBlocking {
        phone.favoriteDao().upsert(favorite("place", 1, name = "Sauvegardé"))
        val (bytes, _) = exported(phone)
        reinstalled.favoriteDao().upsert(favorite("place", 1, home = true, name = "Local"))
        imported(reinstalled, bytes)
        val place = reinstalled.favoriteDao().byId("place")!!
        assertEquals("Sauvegardé", place.name); assertTrue(place.isHome)
    }

    @Test fun `a refused file changes nothing`() = runBlocking {
        reinstalled.favoriteDao().upsert(favorite("local", 1))
        assertEquals(BackupError.NOT_A_BACKUP, (imported(reinstalled, "pas du json".toByteArray()) as BackupResult.Failed).error)
        assertEquals(BackupError.NEWER_FORMAT, (imported(reinstalled, """{"format":"roadly-backup","formatVersion":9}""".toByteArray()) as BackupResult.Failed).error)
        assertEquals(BackupError.IO, (repository(reinstalled).import { null } as BackupResult.Failed).error)
        assertEquals(BackupError.IO, (repository(reinstalled).import { throw SecurityException("accès retiré") } as BackupResult.Failed).error)
        assertEquals(listOf("local"), reinstalled.favoriteDao().all().map { it.id })
    }

    @Test fun `rows of a backup that are invalid are skipped, the rest is imported`() = runBlocking {
        val json = """{"format":"roadly-backup","formatVersion":1,
            "favorites":[{"id":"ok","name":"A","lat":1.0,"lon":2.0,"kind":"POI","source":"s","createdAt":5},
                         {"id":"bad","name":"B","lat":120.0,"lon":2.0,"kind":"POI","source":"s","createdAt":5},
                         {"id":"alien","name":"C","lat":1.0,"lon":2.0,"kind":"GALAXIE","source":"s","createdAt":5}],
            "trips":[{"startedAt":10,"endedAt":5,"destinationName":"X","distanceM":10.0,"durationS":1,"maxSpeedKmh":3.0,"arrived":true}]}"""
        val result = (imported(reinstalled, json.toByteArray()) as BackupResult.Done).value
        assertEquals(ImportSummary(favorites = 1, tripsAdded = 0, tripsAlreadyPresent = 0, rejectedRows = 3), result)
        assertEquals(listOf("ok"), reinstalled.favoriteDao().all().map { it.id })
    }

    @Test fun `when tracks do not fit, the oldest trips are exported without theirs`() = runBlocking {
        val track = "0,48.00000,2.00000,50.0;".repeat(10)
        phone.tripDao().insert(trip(1_000, points = track)); phone.tripDao().insert(trip(2_000, points = track)); phone.tripDao().insert(trip(3_000, points = track))
        val (bytes, summary) = exported(phone, trackBudget = track.length * 2L)
        assertEquals(ExportSummary(favorites = 0, trips = 3, tripsWithoutTrack = 1), summary)
        imported(reinstalled, bytes)
        val back = reinstalled.tripDao().observeSummaries().first().map { reinstalled.tripDao().byId(it.id)!! }
        assertEquals(listOf(track, track, ""), back.map { it.pointsCsv })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), back.map { it.startedAt })
    }

    @Test fun `a trip without a track is not counted as truncated`() = runBlocking {
        phone.tripDao().insert(trip(1_000, points = ""))
        assertEquals(ExportSummary(favorites = 0, trips = 1), exported(phone).second)
    }

    @Test fun `a failed write is reported and an unavailable file writes nothing`() = runBlocking {
        phone.favoriteDao().upsert(favorite("a", 1))
        val failing = object : OutputStream() { override fun write(b: Int) = throw IOException("espace insuffisant") }
        assertEquals(BackupError.IO, (repository(phone).export { failing } as BackupResult.Failed).error)
        assertEquals(BackupError.IO, (repository(phone).export { null } as BackupResult.Failed).error)
        assertEquals(BackupError.IO, (repository(phone).export { throw SecurityException("accès retiré") } as BackupResult.Failed).error)
    }

    @Test fun `an empty device exports a valid empty backup`() = runBlocking {
        val (bytes, summary) = exported(phone)
        assertEquals(ExportSummary(0, 0), summary)
        assertEquals(ImportSummary(0, 0, 0, 0), (imported(reinstalled, bytes) as BackupResult.Done).value)
    }
}
