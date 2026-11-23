package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NAV-2 : âge d'un fix et durées sur l'horloge monotone ; l'heure du téléphone ne sert qu'aux heures affichées ou enregistrées. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationClockTest {


    private fun fix(timestampMs: Long, elapsedMs: Long? = null, east: Double = 0.0) =
        LocationFix(GeoPoint(45.0, 2.0 + east / (111_320.0 * Math.cos(Math.toRadians(45.0)))), 90f, 15f, 5f, timestampMs, elapsedMs)

    // ---- Âge d'un fix ----

    @Test fun `a fix carrying a monotonic time is aged on it, whatever the phone clock says`() {
        // Course démarrée à 50 000 ms de temps monotone ; heure du téléphone en avance de 5 min sur l'heure GNSS des fixes.
        val startedElapsed = 50_000L
        val startedWall = 1_700_000_300_000L
        val gnss = 1_700_000_000_000L
        assertFalse("fix du moment", fix(gnss, elapsedMs = 50_500L).isStale(startedElapsed, startedWall))
        assertFalse("tolérance de 2 s", fix(gnss, elapsedMs = 48_000L).isStale(startedElapsed, startedWall))
        assertTrue("rejeu périmé", fix(gnss, elapsedMs = 47_999L).isStale(startedElapsed, startedWall))
        // Heure du téléphone en retard : un vieux fix ne doit pas passer pour récent.
        assertTrue(fix(gnss + 600_000L, elapsedMs = 10_000L).isStale(startedElapsed, gnss))
    }

    @Test fun `a fix without monotonic time falls back to its timestamp against the phone clock`() {
        assertFalse(fix(10_000L).isStale(startedElapsedMs = 77L, startedWallMs = 10_500L))
        assertFalse(fix(8_500L).isStale(startedElapsedMs = 77L, startedWallMs = 10_500L))
        assertTrue(fix(8_499L).isStale(startedElapsedMs = 77L, startedWallMs = 10_500L))
    }

    @Test fun `duration base of a fix is its monotonic time when known`() {
        assertEquals(1_234L, fix(99_000L, elapsedMs = 1_234L).durationBaseMs)
        assertEquals(99_000L, fix(99_000L).durationBaseMs)
    }

    @Test fun `a single-lambda clock uses the same time for both bases`() {
        val clock = NavigationClock { 42L }
        assertEquals(42L, clock.nowMs())
        assertEquals(42L, clock.elapsedMs())
    }

    // ---- Conduite libre ----

    /** Horloge à deux bases : temps monotone = temps virtuel ; heure du téléphone = temps virtuel + [wallAheadMs]. */
    private class TwoBaseClock(private val scope: TestScope, var wallAheadMs: Long) : NavigationClock {
        override fun nowMs(): Long = scope.testScheduler.currentTime + wallAheadMs
        override fun elapsedMs(): Long = scope.testScheduler.currentTime
    }

    private val location = FakeLocationRepository()

    private fun TestScope.freeDrive(clock: NavigationClock): FreeDriveSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.NoResult)
        }
        val vigilance = object : VigilanceRepository {
            override val count = flowOf(0)
            override val updatedAtMs = flowOf<Long?>(null)
            override suspend fun zonesIn(box: BoundingBox) = emptyList<fr.roadly.core.data.db.VigilanceZoneEntity>()
            override suspend fun refresh(): Outcome<Int> = Outcome.Failure(AppError.NoResult)
            override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? = null
        }
        val prefs = FakeUserPreferencesRepository()
        val speech = FakeSpeechService()
        val nav = NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), clock, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        return FreeDriveSession(location, NoRoadSnapshotProvider, vigilance, speech, prefs, FakeNavigationServiceController(), clock, NoopTripRecorder(), nav, dispatchers, backgroundScope)
    }

    private fun active(s: FreeDriveSession) = s.state.value as FreeDriveState.Active

    @Test fun `free drive - phone clock 5 min ahead of GNSS time, fixes are used at once and the GPS is not reported lost`() = runTest {
        val free = freeDrive(TwoBaseClock(this, wallAheadMs = 300_000L))
        assertTrue(free.start()); runCurrent()
        assertEquals("heure de départ = heure du téléphone", 300_000L, active(free).startedAtMs)
        repeat(15) { i ->
            advanceTimeBy(1_000)
            location.emit(fix(testScheduler.currentTime, elapsedMs = testScheduler.currentTime, east = 15.0 * i)); runCurrent()
            assertNotNull("fix n°$i pris en compte", active(free).lastFix)
            assertFalse("fix n°$i : GPS perdu", active(free).gpsLost)
        }
        assertEquals(14 * 15.0, active(free).distanceM, 2.0)
    }

    @Test fun `free drive - a fix replayed from before the start is dropped even when the phone clock is late`() = runTest {
        advanceTimeBy(60_000)
        // Dernier fix connu, vieux de 30 s, rejoué par le flux ; son heure GNSS est en avance sur l'heure (fausse) du téléphone.
        location.emit(fix(timestampMs = 5_000_000L, elapsedMs = 30_000L))
        val free = freeDrive(TwoBaseClock(this, wallAheadMs = -40_000L))
        assertTrue(free.start()); runCurrent()
        assertNull("fix périmé ignoré", active(free).lastFix)
    }

    @Test fun `free drive - phone clock set back 10 min, the GPS loss is still reported after 10 s`() = runTest {
        val clock = TwoBaseClock(this, wallAheadMs = 0L)
        val free = freeDrive(clock)
        assertTrue(free.start()); runCurrent()
        advanceTimeBy(1_000)
        location.emit(fix(testScheduler.currentTime, elapsedMs = testScheduler.currentTime)); runCurrent()
        clock.wallAheadMs = -600_000L
        advanceTimeBy(9_500); runCurrent()
        assertFalse(active(free).gpsLost)
        advanceTimeBy(2_000); runCurrent()
        assertTrue("GPS perdu après 10 s sans fix", active(free).gpsLost)
    }
}
