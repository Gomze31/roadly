package fr.roadly.core.navigation

import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripPoint
import fr.roadly.core.model.VehicleProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * NAV-1 : sauvegarde du trajet en cours. « Mort du processus » = un nouvel enregistreur (mémoire vide) sur le même dossier, sans
 * que `end` ait été appelé sur le précédent. JVM seulement : l'arrêt réel d'un processus Android n'est pas simulé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripCheckpointTest {
    @get:Rule val folder = TemporaryFolder()

    private val saved = mutableListOf<Trip>()
    private var saveFails = false
    private val trips = object : TripRepository {
        override val trips: Flow<List<Trip>> = flowOf(emptyList())
        override suspend fun byId(id: Long): Trip? = null
        override suspend fun save(trip: Trip): Long { if (saveFails) throw IllegalStateException("base indisponible"); saved += trip; return saved.size.toLong() }
        override suspend fun delete(id: Long) = Unit
        override suspend fun clear() = Unit
    }
    private val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    private val dir: File get() = folder.root
    private val file: File get() = File(dir, FileTripCheckpointStore.FILE_NAME)

    private fun TestScope.recorder(): RoomTripRecorder = RoomTripRecorder(trips, vehicles, FileTripCheckpointStore(dir), backgroundScope).also { runCurrent() }

    /** Mètres par degré de longitude à cette latitude, mesurés avec la même formule que l'enregistreur. */
    private val metersPerDegree = GeoPoint(48.85, 2.3).distanceTo(GeoPoint(48.85, 2.301)) * 1_000.0
    /** Point à [meters] à l'est du départ (un cheveu de plus, pour que 20 m nominaux fassent bien les 20 m de l'échantillonnage). */
    private fun at(meters: Double) = GeoPoint(48.85, 2.3 + meters * 1.0001 / metersPerDegree)
    private fun fix(meters: Double, speedMps: Float = 10f) = LocationFix(at(meters), 90f, speedMps, 5f, 0L)

    /** Roule de [fromM] à [toM] à 10 m/s, une position par seconde (10 m), en partant de l'instant [t0]. */
    private fun RoomTripRecorder.drive(fromM: Int, toM: Int, t0: Long): Long {
        var t = t0
        for (m in fromM..toM step 10) { onFix(fix(m.toDouble()), t); t += 1_000 }
        return t
    }

    @Test fun `nothing is written before the first checkpoint is due`() = runTest {
        val r = recorder()
        r.begin("Lyon", 1_000_000L)
        r.drive(0, 180, 1_000_000L) // 180 m en 18 s : ni 200 m ni 30 s
        assertFalse(file.exists())
    }

    @Test fun `a checkpoint is written every 200 m and survives the process - the trip is saved as not arrived at the next start`() = runTest {
        val r = recorder()
        r.begin("Lyon", 1_000_000L)
        r.drive(0, 1_000, 1_000_000L) // 1 km en 100 s
        assertTrue(file.exists())
        assertTrue(saved.isEmpty())

        // Nouveau processus : la récupération part à la création de l'enregistreur.
        recorder()
        assertEquals(1, saved.size)
        val trip = saved.single()
        assertEquals("Lyon", trip.destinationName)
        assertFalse(trip.arrived)
        assertEquals(1_000_000L, trip.startedAtEpochMs)
        assertEquals(TravelMode.CAR, trip.travelMode)
        // Dernière sauvegarde à 1 000 m (un point tous les 20 m, sauvegarde tous les 200 m) : rien de perdu ici.
        assertEquals(1_000.0, trip.distanceM, 5.0)
        assertEquals(51, trip.points.size)
        assertEquals(1_000_000L + trip.points.last().tMs, trip.endedAtEpochMs)
        assertEquals(100L, trip.durationS)
        assertEquals(36.0, trip.maxSpeedKmh, 0.01)
        assertFalse("la sauvegarde est effacée une fois le trajet enregistré", file.exists())

        // Troisième processus : rien à récupérer, pas de doublon.
        recorder()
        assertEquals(1, saved.size)
    }

    @Test fun `at most the last 200 m or 30 s are lost`() = runTest {
        val r = recorder()
        r.begin("Lyon", 0L)
        r.drive(0, 1_180, 0L) // dernière sauvegarde à 1 000 m ; 180 m de plus en mémoire seulement
        recorder()
        assertEquals(1_000.0, saved.single().distanceM, 5.0)
    }

    @Test fun `slow traffic - a checkpoint is written after 30 s even if 200 m were not covered`() = runTest {
        val r = recorder()
        r.begin("Lyon", 0L)
        // 2 m/s : un point retenu tous les 20 m (10 s) ; à 30 s, 60 m parcourus.
        var t = 0L
        for (m in 0..60 step 2) { r.onFix(fix(m.toDouble(), 2f), t); t += 1_000 }
        assertTrue(file.exists())
    }

    @Test fun `the checkpoint keeps the scooter context`() = runTest {
        val r = recorder()
        r.begin("Parc", 0L)
        r.setContext(TripContext(TravelMode.ELECTRIC_SCOOTER, scooterProfileId = 7L, batteryStartPct = 83))
        r.drive(0, 400, 0L)
        recorder()
        val trip = saved.single()
        assertEquals(TravelMode.ELECTRIC_SCOOTER, trip.travelMode)
        assertEquals(7L, trip.scooterProfileId)
        assertEquals(83, trip.batteryStartPct)
        assertNull(trip.costEur)
    }

    @Test fun `ending the trip deletes the checkpoint and saves it once`() = runTest {
        val r = recorder()
        r.begin("Lyon", 0L)
        val t = r.drive(0, 600, 0L)
        assertTrue(file.exists())
        r.end(arrived = true, nowMs = t)
        assertFalse(file.exists())
        assertEquals(1, saved.size)
        assertTrue(saved.single().arrived)
        recorder()
        assertEquals(1, saved.size)
    }

    @Test fun `a database failure at the end throws nothing and keeps the checkpoint - the trip is saved at the next start`() = runTest {
        val r = recorder()
        r.begin("Lyon", 0L)
        val t = r.drive(0, 600, 0L)
        saveFails = true
        r.end(arrived = true, nowMs = t)
        assertTrue(saved.isEmpty())
        assertTrue("la sauvegarde reste tant que le trajet n'est pas en base", file.exists())
        saveFails = false
        recorder()
        assertEquals(1, saved.size)
        assertFalse(file.exists())
    }

    @Test fun `ending a trip that is too short deletes its checkpoint too`() = runTest {
        val r = recorder()
        r.begin("Lyon", 0L)
        var t = 0L
        for (m in 0..100 step 2) { r.onFix(fix(m.toDouble(), 2f), t); t += 1_000 } // 100 m en 50 s : sauvegardé à 30 s
        assertTrue(file.exists())
        r.end(arrived = false, nowMs = t)
        assertFalse(file.exists())
        assertTrue(saved.isEmpty())
    }

    @Test fun `a leftover checkpoint not yet recovered is saved by the next begin`() = runTest {
        recorder().apply { begin("Lyon", 0L); drive(0, 600, 0L) }
        // Nouveau processus dont la récupération de démarrage n'a pas encore tourné : `begin` la fait avant de repartir.
        val next = RoomTripRecorder(trips, vehicles, FileTripCheckpointStore(dir), backgroundScope)
        next.begin("Lyon", 5_000_000L)
        runCurrent()
        assertEquals(1, saved.size)
        assertEquals(0L, saved.single().startedAtEpochMs)
        assertFalse(file.exists())
        // Le nouveau trajet se sauvegarde à son tour, sans mélange avec l'ancien.
        val t = next.drive(0, 400, 5_000_000L)
        next.end(arrived = true, nowMs = t)
        assertEquals(2, saved.size)
        assertEquals(5_000_000L, saved[1].startedAtEpochMs)
        assertEquals(400.0, saved[1].distanceM, 5.0)
    }

    @Test fun `replacing a trip in progress discards it and its checkpoint, as before`() = runTest {
        val r = recorder()
        r.begin("Lyon", 0L)
        r.drive(0, 600, 0L)
        r.begin("Marseille", 100_000L) // changement de destination en cours de route
        runCurrent()
        assertTrue(saved.isEmpty())
        assertFalse(file.exists())
    }

    @Test fun `a leftover checkpoint shorter than 200 m is dropped`() = runTest {
        FileTripCheckpointStore(dir).write(TripSnapshot("Lyon", 0L, TripContext(), listOf(TripPoint(0, at(0.0), 10.0), TripPoint(5_000, at(100.0), 10.0))))
        recorder()
        assertTrue(saved.isEmpty())
        assertFalse(file.exists())
    }

    @Test fun `a corrupt checkpoint is deleted without crashing`() = runTest {
        val good = FileTripCheckpointStore.encode(TripSnapshot("Lyon", 0L, TripContext(), (0..30).map { TripPoint(it * 2_000L, at(it * 20.0), 36.0) }))
        val damaged = listOf(
            ByteArray(0),
            byteArrayOf(1, 2, 3),
            good.copyOf(good.size - 7),                                  // tronqué (écriture interrompue)
            good.copyOf().also { it[40] = (it[40] + 1).toByte() },       // un octet abîmé : somme de contrôle fausse
            good.copyOf().also { it[7] = 9 },                            // autre version du format
            good + byteArrayOf(0),                                       // octets en trop
            "pas une sauvegarde".toByteArray(),
        )
        for (bytes in damaged) {
            file.writeBytes(bytes)
            val r = recorder()
            assertTrue(saved.isEmpty())
            assertFalse(file.exists())
            // L'enregistreur reste utilisable.
            r.begin("Lyon", 0L)
            r.end(arrived = false, nowMs = 1L)
        }
    }

    @Test fun `the file format round-trips every field`() {
        val snapshot = TripSnapshot(
            "Gare de Lyon — é", 1_767_225_600_000L, TripContext(TravelMode.BICYCLE, 12L, 55),
            listOf(TripPoint(0, GeoPoint(48.85, 2.3), null), TripPoint(1_500, GeoPoint(-33.9, 151.2), 87.123456789)),
        )
        assertEquals(snapshot, FileTripCheckpointStore.decode(FileTripCheckpointStore.encode(snapshot)))
        val store = FileTripCheckpointStore(dir)
        store.write(snapshot)
        assertFalse("pas de fichier temporaire laissé", File(dir, FileTripCheckpointStore.FILE_NAME + ".tmp").exists())
        assertEquals(snapshot, store.take())
        assertNull(store.take())
    }

    @Test fun `a storage that cannot be written never breaks recording`() = runTest {
        // Dossier impossible (un fichier à sa place) : chaque sauvegarde échoue en silence, le trajet s'enregistre quand même.
        val blocked = folder.newFile("occupé")
        val r = RoomTripRecorder(trips, vehicles, FileTripCheckpointStore(blocked), backgroundScope)
        runCurrent()
        r.begin("Lyon", 0L)
        val t = r.drive(0, 600, 0L)
        r.end(arrived = true, nowMs = t)
        assertEquals(600.0, saved.single().distanceM, 5.0)
    }
}
