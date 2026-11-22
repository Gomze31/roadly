package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.model.TravelMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/**
 * NAV-1 / TST-1 : décision de reprise du guidage après la recréation du service dans un nouveau processus. JVM, doublures de
 * position et de calcul : ce que fait réellement Android après avoir arrêté le processus n'est pas testé ici.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceRestorerTest {
    /** Heure de départ des tests (l'horloge virtuelle part de zéro : il faut de la marge pour dater une course du passé). */
    private val t0 = 1_000_000_000_000L

    private fun TestScope.world() = RestoreWorld(backgroundScope, StandardTestDispatcher(testScheduler), NavigationClock { t0 + testScheduler.currentTime })

    @Test fun `a recent remembered guidance is resumed with one fastest route from the current position`() = runTest {
        val w = world()
        w.remember(name = "Maison")
        w.location.emit(w.fix(w.route.geometry[3]))
        assertEquals(RestoreOutcome.RESUMED, w.restorer.attempt())
        runCurrent()
        val g = w.guiding!!
        assertEquals("repris-1", g.route.id)
        assertEquals("une seule requête d'itinéraire", 1, w.requests.size)
        assertEquals(w.route.geometry[3], w.requests.single().origin)
        assertEquals(w.destination, w.requests.single().destination)
        // La course mémorisée est celle qui vient de repartir, sous le même nom.
        assertEquals("repris-1", w.prefs.last.value?.routeId)
        assertEquals("Maison", w.prefs.last.value?.destinationName)
        assertEquals(1, w.serviceController.starts)
        assertTrue(w.location.guidanceActive)
    }

    @Test fun `nothing remembered - nothing is resumed and no route is requested`() = runTest {
        val w = world()
        w.location.emit(w.fix())
        assertEquals(RestoreOutcome.NOTHING_TO_RESUME, w.restorer.attempt())
        assertTrue(w.requests.isEmpty())
        assertFalse(w.location.guidanceActive)
        assertEquals(NavigationState.Idle, w.session.state.value)
    }

    @Test fun `a guidance older than six hours is not resumed and stays remembered for the home card`() = runTest {
        val w = world()
        w.remember(ageMs = GuidanceRestorer.MAX_AGE_MS + 1)
        w.location.emit(w.fix())
        assertEquals(RestoreOutcome.TOO_OLD, w.restorer.attempt())
        assertTrue(w.requests.isEmpty())
        assertNotNull(w.prefs.last.value)
        // Juste en dessous de la limite : reprise.
        w.remember(ageMs = GuidanceRestorer.MAX_AGE_MS)
        assertEquals(RestoreOutcome.RESUMED, w.restorer.attempt())
    }

    @Test fun `a guidance dated in the future (phone clock changed) is not resumed`() = runTest {
        val w = world()
        w.remember(ageMs = -60_000L)
        w.location.emit(w.fix())
        assertEquals(RestoreOutcome.TOO_OLD, w.restorer.attempt())
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `without the location permission nothing is resumed and the guidance stays remembered`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix())
        for (state in listOf(LocationPermissionState.UNKNOWN, LocationPermissionState.DENIED, LocationPermissionState.DENIED_FOREVER)) {
            w.location.permission.value = state
            assertEquals(RestoreOutcome.NO_PERMISSION, w.restorer.attempt())
        }
        runCurrent()
        assertTrue(w.requests.isEmpty())
        assertNotNull("un départ sans permission aurait effacé la course", w.prefs.last.value)
        assertEquals(NavigationState.Idle, w.session.state.value)
        assertTrue("aucune reprise comptée", w.budget.stamps.isEmpty())
    }

    @Test fun `scooter and bicycle modes are refused like on the home card`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix())
        for (mode in listOf(TravelMode.ELECTRIC_SCOOTER, TravelMode.BICYCLE)) {
            w.mobility.modeState.value = mode
            assertEquals(RestoreOutcome.LIGHT_VEHICLE, w.restorer.attempt())
        }
        assertTrue(w.requests.isEmpty())
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `no position within 20 s - the attempt gives up, releases the GPS and keeps the guidance remembered`() = runTest {
        val w = world()
        w.remember()
        val started = testScheduler.currentTime
        var during: Boolean? = null
        launch { delay(5_000); during = w.location.guidanceActive }
        assertEquals(RestoreOutcome.NO_POSITION, w.restorer.attempt())
        assertEquals(GuidanceRestorer.POSITION_TIMEOUT_MS, testScheduler.currentTime - started)
        assertEquals("la position est demandée pendant l'attente", true, during)
        assertFalse(w.location.guidanceActive)
        assertTrue(w.requests.isEmpty())
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `a stale last-known position is ignored and the first recent fix is used`() = runTest {
        val w = world()
        w.remember()
        val clock = w.clock
        // Dernière position connue du téléphone, vieille de 5 min (instant monotone), puis une vraie position 3 s plus tard.
        w.location.emit(w.fix(w.route.geometry[0], elapsedMs = clock.elapsedMs() - 300_000L))
        launch { delay(3_000); w.location.emit(w.fix(w.route.geometry[5], elapsedMs = clock.elapsedMs())) }
        assertEquals(RestoreOutcome.RESUMED, w.restorer.attempt())
        assertEquals(w.route.geometry[5], w.requests.single().origin)
    }

    @Test fun `a coarse position is not used as the origin - the first precise fix is (NAV-3)`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix(w.route.geometry[0]).copy(accuracyM = 800f))
        launch { delay(3_000); w.location.emit(w.fix(w.route.geometry[5])) }
        assertEquals(RestoreOutcome.RESUMED, w.restorer.attempt())
        assertEquals(w.route.geometry[5], w.requests.single().origin)
    }

    @Test fun `only stale positions - no route is requested`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix(elapsedMs = w.clock.elapsedMs() - 300_000L))
        assertEquals(RestoreOutcome.NO_POSITION, w.restorer.attempt())
        assertTrue(w.requests.isEmpty())
    }

    @Test fun `a route failure leaves the session idle, the GPS released and the guidance remembered`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix())
        for (error in listOf(AppError.Network, AppError.Timeout, AppError.RateLimited, AppError.NoResult, AppError.Http(503, "indisponible"))) {
            w.answer = { Outcome.Failure(error) }
            assertEquals(RestoreOutcome.ROUTE_FAILED, w.restorer.attempt())
            w.budget.stamps = emptyList()
        }
        runCurrent()
        assertEquals(NavigationState.Idle, w.session.state.value)
        assertFalse(w.location.guidanceActive)
        assertNotNull(w.prefs.last.value)
        assertEquals(0, w.serviceController.starts)
    }

    @Test fun `an unexpected exception during the computation is contained`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix())
        w.answer = { error("panne inattendue") }
        assertEquals(RestoreOutcome.ROUTE_FAILED, w.restorer.attempt())
        assertFalse(w.location.guidanceActive)
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `a route that cannot be followed is not reported as resumed`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix())
        // Tracé d'un seul point : `NavigationSession.start` le refuse (état `Failed`).
        w.answer = { Outcome.Success(listOf(w.route.copy(geometry = w.route.geometry.take(1)))) }
        assertEquals(RestoreOutcome.ROUTE_FAILED, w.restorer.attempt())
        assertNull(w.guiding)
    }

    @Test fun `a guidance already running is never replaced`() = runTest {
        val w = world()
        w.startGuidance()
        runCurrent()
        w.location.emit(w.fix())
        assertEquals(RestoreOutcome.SUPERSEDED, w.restorer.attempt())
        assertTrue(w.requests.isEmpty())
        assertEquals(w.route.id, w.guiding?.route?.id)
    }

    @Test fun `the user starts a guidance while the position is awaited - it is kept and its GPS is not released`() = runTest {
        val w = world()
        w.remember()
        launch { delay(2_000); w.startGuidance("Bureau"); delay(1_000); w.location.emit(w.fix()) }
        assertEquals(RestoreOutcome.SUPERSEDED, w.restorer.attempt())
        runCurrent()
        assertTrue(w.requests.isEmpty())
        assertEquals(w.route.id, w.guiding?.route?.id)
        assertTrue(w.location.guidanceActive)
    }

    @Test fun `a free drive in progress is never replaced`() = runTest {
        val w = world()
        w.remember()
        w.location.emit(w.fix())
        assertTrue(w.freeDrive.start())
        assertEquals(RestoreOutcome.SUPERSEDED, w.restorer.attempt())
        assertTrue(w.requests.isEmpty())
        assertTrue(w.freeDrive.state.value is FreeDriveState.Active)
    }

    @Test fun `after three automatic resumes within the hour the next one is refused before any route request`() = runTest {
        val w = world()
        w.location.emit(w.fix())
        repeat(RestoreBudgetRule.MAX_RESTORES) {
            w.remember()
            assertEquals(RestoreOutcome.RESUMED, w.restorer.attempt())
            // Le processus « meurt » : la session du monde est remise au repos sans effacer la course (comme un nouveau processus).
            val remembered = w.prefs.last.value
            w.session.stop(); runCurrent()
            w.prefs.last.value = remembered
            advanceTimeBy(60_000)
        }
        assertEquals(3, w.requests.size)
        assertEquals(RestoreOutcome.TOO_MANY_RESTORES, w.restorer.attempt())
        assertEquals(3, w.requests.size)
        assertNotNull(w.prefs.last.value)
        // Une heure plus tard, la reprise est de nouveau permise.
        advanceTimeBy(RestoreBudgetRule.WINDOW_MS)
        w.remember()
        assertEquals(RestoreOutcome.RESUMED, w.restorer.attempt())
    }

    @Test fun `budget rule - three per hour, entries from the future count as recent`() {
        val now = 10_000_000L
        assertEquals(listOf(now), RestoreBudgetRule.take(emptyList(), now))
        assertEquals(listOf(now - 10, now - 5, now), RestoreBudgetRule.take(listOf(now - 10, now - 5), now))
        assertNull(RestoreBudgetRule.take(listOf(now - 30, now - 20, now - 10), now))
        // Les reprises de plus d'une heure sont oubliées.
        assertEquals(listOf(now - 10, now), RestoreBudgetRule.take(listOf(now - RestoreBudgetRule.WINDOW_MS, now - RestoreBudgetRule.WINDOW_MS - 1, now - 10), now))
        // Heure du téléphone reculée : les instants « futurs » restent comptés.
        assertNull(RestoreBudgetRule.take(listOf(now + 1, now + 2, now + 3), now))
    }
}
