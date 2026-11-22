package fr.roadly.core.location

import android.Manifest
import android.app.Application
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.lang.reflect.Proxy

/**
 * TST-3 : [FusedLocationRepository] sous Robolectric (API 34), avec un client de localisation simulé. Vérifie ce que le dépôt
 * demande au GPS (rien sans permission, cadence selon le mode, arrêt quand plus personne n'écoute), la conversion des positions
 * et le rejeu qui remplace le GPS.
 *
 * Ce que ces tests ne montrent PAS : le comportement des services Google Play eux-mêmes (cadence réellement servie, position
 * refusée écran éteint). Le client simulé rend ce qu'on lui donne.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FusedLocationRepositoryTest {
    /** Client simulé : seules les trois méthodes utilisées par le dépôt répondent ; toute autre est une erreur du test. */
    private class FakeFused {
        var last: Location? = null
        val requests = mutableListOf<LocationRequest>()
        val active = mutableListOf<LocationCallback>()
        var removals = 0

        val client: FusedLocationProviderClient = Proxy.newProxyInstance(
            FusedLocationProviderClient::class.java.classLoader, arrayOf(FusedLocationProviderClient::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getLastLocation" -> Tasks.forResult(last)
                "requestLocationUpdates" -> { requests += args[0] as LocationRequest; active += args[1] as LocationCallback; Tasks.forResult<Void>(null) }
                "removeLocationUpdates" -> { active.remove(args[0]); removals++; Tasks.forResult<Void>(null) }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args[0]
                "toString" -> "FakeFused"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as FusedLocationProviderClient

        fun emit(location: Location) = active.toList().forEach { it.onLocationResult(LocationResult.create(listOf(location))) }
    }

    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs = FakeUserPreferencesRepository()
    private val fused = FakeFused()

    private fun grant() = shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    private class World(val repo: FusedLocationRepository, val replay: LocationReplay, val seen: MutableList<LocationFix>, val listener: Job)

    /** Dépôt construit comme en production, plus un abonné qui note chaque position reçue. */
    private fun TestScope.world(): World {
        val replay = LocationReplay(backgroundScope)
        val repo = FusedLocationRepository(app, backgroundScope, prefs, replay, fused.client)
        val seen = mutableListOf<LocationFix>()
        val listener = backgroundScope.launch { repo.fixes.toList(seen) }
        settle()
        return World(repo, replay, seen, listener)
    }

    /** Laisse passer les coroutines en attente et les rappels postés sur le fil principal (tâches des services Google Play). */
    private fun TestScope.settle() {
        repeat(3) { runCurrent(); ShadowLooper.idleMainLooper() }
        runCurrent()
    }

    private fun location(lat: Double = 43.6, lon: Double = 1.44, configure: Location.() -> Unit = {}) = Location("gps").apply {
        latitude = lat; longitude = lon; time = 1_700_000_000_000L; elapsedRealtimeNanos = 5_000_000_000L
        configure()
    }

    // ---- Permission ----

    @Test fun `without the location permission nothing is asked of the GPS, whatever the mode`() = runTest {
        val w = world()
        assertEquals(LocationPermissionState.UNKNOWN, w.repo.permissionState.value)
        w.repo.setMode(LocationMode.FOLLOW)
        w.repo.setGuidanceActive(true)
        w.repo.setCarDisplayActive(true)
        settle()
        assertTrue(fused.requests.isEmpty())
        assertTrue(w.seen.isEmpty())
        // Refus constaté : toujours rien.
        w.repo.refreshPermission()
        settle()
        assertEquals(LocationPermissionState.DENIED, w.repo.permissionState.value)
        assertTrue(fused.requests.isEmpty())
    }

    @Test fun `permission granted later starts the GPS in the mode already requested`() = runTest {
        val w = world()
        w.repo.setMode(LocationMode.MAP)
        settle()
        assertTrue(fused.requests.isEmpty())
        grant()
        w.repo.refreshPermission()
        settle()
        assertEquals(LocationPermissionState.GRANTED, w.repo.permissionState.value)
        assertEquals(1, fused.requests.size)
    }

    @Test fun `a permanent refusal is saved, survives a refresh and a new process, and ends when the permission is granted`() = runTest {
        val w = world()
        w.repo.markDeniedForever()
        settle()
        assertEquals(LocationPermissionState.DENIED_FOREVER, w.repo.permissionState.value)
        assertTrue(prefs.deniedForever.value)
        w.repo.refreshPermission()
        assertEquals("un refus définitif ne redevient pas un simple refus", LocationPermissionState.DENIED_FOREVER, w.repo.permissionState.value)

        // Nouveau processus : le refus définitif est relu dans les réglages.
        val next = FusedLocationRepository(app, backgroundScope, prefs, LocationReplay(backgroundScope), fused.client)
        settle()
        assertEquals(LocationPermissionState.DENIED_FOREVER, next.permissionState.value)

        grant()
        next.refreshPermission()
        settle()
        assertEquals(LocationPermissionState.GRANTED, next.permissionState.value)
        assertFalse(prefs.deniedForever.value)
    }

    // ---- Ce qui est demandé au GPS ----

    @Test fun `the request follows the mode - high accuracy, 1 Hz on the map, 2 Hz when following, nothing when off`() = runTest {
        grant()
        val w = world()
        assertTrue("mode OFF au départ", fused.requests.isEmpty())

        w.repo.setMode(LocationMode.MAP)
        settle()
        val map = fused.requests.single()
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, map.priority)
        assertEquals(1_000L, map.intervalMillis)
        assertEquals(500L, map.minUpdateIntervalMillis)

        w.repo.setMode(LocationMode.FOLLOW)
        settle()
        assertEquals(500L, fused.requests.last().intervalMillis)
        assertEquals(250L, fused.requests.last().minUpdateIntervalMillis)
        assertEquals("l'ancienne demande est retirée, une seule reste active", 1, fused.active.size)

        w.repo.setMode(LocationMode.OFF)
        settle()
        assertTrue(fused.active.isEmpty())
    }

    @Test fun `guidance keeps the GPS on when the screen asks for nothing, and so does the car screen`() = runTest {
        grant()
        val w = world()
        w.repo.setGuidanceActive(true)
        settle()
        assertEquals(LocationMode.GUIDANCE.intervalMs, fused.requests.single().intervalMillis)
        w.repo.setGuidanceActive(false)
        settle()
        assertTrue(fused.active.isEmpty())

        w.repo.setCarDisplayActive(true)
        settle()
        assertEquals(1, fused.active.size)
        assertEquals(LocationMode.MAP.intervalMs, fused.requests.last().intervalMillis)
        w.repo.setCarDisplayActive(false)
        settle()
        assertTrue(fused.active.isEmpty())
    }

    @Test fun `the GPS is released 5 s after the last listener leaves, not before`() = runTest {
        grant()
        val w = world()
        w.repo.setMode(LocationMode.MAP)
        settle()
        assertEquals(1, fused.active.size)
        w.listener.cancel()
        settle()
        advanceTimeBy(4_000)
        settle()
        assertEquals("un changement d'écran ne coupe pas le GPS", 1, fused.active.size)
        advanceTimeBy(2_000)
        settle()
        assertTrue(fused.active.isEmpty())
    }

    // ---- Positions ----

    @Test fun `the last known position comes first, then each update, and lastKnown follows`() = runTest {
        grant()
        fused.last = location(lat = 43.0)
        val w = world()
        assertNull(w.repo.lastKnown)
        w.repo.setMode(LocationMode.MAP)
        settle()
        assertEquals(listOf(43.0), w.seen.map { it.point.lat })
        fused.emit(location(lat = 43.5))
        settle()
        assertEquals(listOf(43.0, 43.5), w.seen.map { it.point.lat })
        assertEquals(GeoPoint(43.5, 1.44), w.repo.lastKnown?.point)
    }

    @Test fun `no last known position - nothing is emitted until the first update`() = runTest {
        grant()
        val w = world()
        w.repo.setMode(LocationMode.MAP)
        settle()
        assertTrue(w.seen.isEmpty())
    }

    @Test fun `a position without bearing, speed or accuracy gives null bearing and speed and a 50 m accuracy`() = runTest {
        grant()
        val w = world()
        w.repo.setMode(LocationMode.MAP)
        settle()
        fused.emit(location())
        settle()
        val bare = w.seen.single()
        assertNull(bare.bearingDeg)
        assertNull(bare.speedMps)
        assertEquals(50f, bare.accuracyM, 0f)
        assertEquals(1_700_000_000_000L, bare.timestampMs)

        fused.emit(location { bearing = 90f; speed = 13.9f; accuracy = 4f })
        settle()
        val full = w.seen.last()
        assertEquals(90f, full.bearingDeg)
        assertEquals(13.9f, full.speedMps)
        assertEquals(4f, full.accuracyM, 0f)
    }

    @Test fun `the monotonic instant of the fix is kept in milliseconds, and absent when the source gives none (NAV-2)`() = runTest {
        grant()
        val w = world()
        w.repo.setMode(LocationMode.MAP)
        settle()
        fused.emit(location { elapsedRealtimeNanos = 5_000_000_000L })
        fused.emit(location { elapsedRealtimeNanos = 0L })
        settle()
        assertEquals(5_000L, w.seen[0].elapsedRealtimeMs)
        assertEquals(5_000L, w.seen[0].durationBaseMs)
        assertNull(w.seen[1].elapsedRealtimeMs)
        assertEquals("sans instant monotone, la durée se rabat sur l'heure du fix", 1_700_000_000_000L, w.seen[1].durationBaseMs)
    }

    // ---- Rejeu ----

    @Test fun `a replay takes the place of the GPS and the GPS comes back when it stops`() = runTest {
        grant()
        val w = world()
        w.repo.setMode(LocationMode.MAP)
        settle()
        assertEquals(1, fused.active.size)

        val track = listOf(LocationFix(GeoPoint(48.0, 2.0), 0f, 10f, 5f, 0L), LocationFix(GeoPoint(48.001, 2.0), 0f, 10f, 5f, 1_000L))
        w.replay.play(track, nowMs = { 42L })
        settle()
        assertTrue("le GPS réel est relâché pendant le rejeu", fused.active.isEmpty())
        advanceTimeBy(1_000)
        settle()
        assertEquals(listOf(48.0, 48.001), w.seen.map { it.point.lat })
        assertEquals("les positions rejouées portent l'heure courante", listOf(42L, 42L), w.seen.map { it.timestampMs })

        w.replay.stop()
        settle()
        assertEquals(1, fused.active.size)
    }
}
