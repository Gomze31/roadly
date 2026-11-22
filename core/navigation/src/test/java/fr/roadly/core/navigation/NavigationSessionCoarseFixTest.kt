package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NAV-3 : un fix moins précis que [GuidanceConstants.UNUSABLE_FIX_ACCURACY_M] vaut une absence de fix pour le guidage. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionCoarseFixTest {
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val location = FakeLocationRepository()
    private val speech = FakeSpeechService()
    private var rerouteRequests = 0

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> { rerouteRequests++; return Outcome.Failure(AppError.NoResult) }
        }
        return NavigationSession(
            location, routing, NoopSpeedLimitProvider(), speech, FakeUserPreferencesRepository(), FakeNavigationServiceController(),
            { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
        ).also { it.start(RouteOption(route, "Test", 0L, null, sameAsFastest = false), RoutePreferences()) }
    }

    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    private suspend fun TestScope.good(along: Double, accuracyM: Float = 5f) {
        location.emit(LocationFix(tracker.pointAt(along), 90f, 10f, accuracyM, testScheduler.currentTime)); runCurrent()
    }

    /** Position « réseau » : à 600 m au nord du tracé, sans vitesse ni cap. */
    private suspend fun TestScope.coarse(along: Double, accuracyM: Float) {
        location.emit(LocationFix(TestRoutes.north(tracker.pointAt(along), 600.0), null, null, accuracyM, testScheduler.currentTime)); runCurrent()
    }

    @Test fun `a coarse fix does not move the position, does not end the GPS loss and asks for no reroute`() = runTest {
        val s = session(); runCurrent()
        advanceTimeBy(1_000); good(100.0)
        advanceTimeBy(1_000); good(110.0)
        assertEquals(110.0, guiding(s).progress.distanceAlongM, 1.0)
        // Tunnel : plus de GNSS ; une position réseau toutes les 2 s, précise à 800 m, loin du tracé.
        var along = guiding(s).progress.distanceAlongM
        repeat(8) {
            advanceTimeBy(2_000); runCurrent()
            val before = guiding(s).progress.distanceAlongM
            coarse(110.0, accuracyM = 800f)
            val g = guiding(s)
            assertTrue("écart mesuré sur un fix grossier", g.progress.offRouteM < 1.0)
            assertEquals("position déplacée par un fix grossier", before, g.progress.distanceAlongM, 0.001)
            assertTrue("position reculée", g.progress.distanceAlongM >= along)
            along = g.progress.distanceAlongM
        }
        assertTrue("16 s sans fix exploitable : GPS perdu", guiding(s).gpsLost)
        assertTrue(guiding(s).progress.extrapolated)
        assertEquals(1, speech.spoken.count { it == SpokenPhrases.GPS_LOST })
        assertEquals(0, rerouteRequests)
        // Sortie du tunnel : le premier fix exploitable met fin à la perte.
        advanceTimeBy(1_000); good(290.0)
        assertFalse(guiding(s).gpsLost)
        assertEquals(290.0, guiding(s).progress.distanceAlongM, 1.0)
    }

    @Test fun `a fix of exactly 100 m accuracy is still used, 101 m is not`() = runTest {
        val s = session(); runCurrent()
        advanceTimeBy(1_000); good(100.0)
        advanceTimeBy(1_000); good(150.0, accuracyM = 101f)
        assertEquals(100.0, guiding(s).progress.distanceAlongM, 1.0)
        advanceTimeBy(1_000); good(160.0, accuracyM = 100f)
        assertEquals(160.0, guiding(s).progress.distanceAlongM, 1.0)
    }
}
