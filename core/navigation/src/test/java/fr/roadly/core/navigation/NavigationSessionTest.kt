package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Pas de `advanceUntilIdle()` ici : le tic de surveillance d'1 s boucle indéfiniment. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val service = FakeNavigationServiceController()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private val requests = mutableListOf<RouteRequest>()
    private var routingResult: Outcome<List<Route>> = Outcome.Success(listOf(TestRoutes.straight("rerouted")))
    private var routeHangs = false
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            if (routeHangs) awaitCancellation()
            return routingResult
        }
    }
    private var limit: Int? = null
    private val speedLimits = object : SpeedLimitProvider {
        override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = Outcome.Success(limit)
    }

    private fun TestScope.session(preferences: UserPreferencesRepository = prefs): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, speedLimits, speech, preferences, service, { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
    }

    /** Un écart latéral simulé s'accompagne d'un cap qui s'éloigne du tracé (20°, soit 70° d'écart, B6) ; sinon cap du tracé (90°). */
    private fun fixAt(along: Double, speedMps: Float? = 10f, northM: Double = 0.0, timestampMs: Long = 0L, bearing: Float = if (northM > 0.0) 20f else 90f) =
        LocationFix(TestRoutes.north(tracker.pointAt(along), northM), bearing, speedMps, 5f, timestampMs)

    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    @Test fun `start guides at once, holds the GPS, starts the service and announces the departure`() = runTest {
        val s = session()
        s.start(option, RoutePreferences())
        assertTrue(s.state.value is NavigationState.Guiding)
        assertEquals(0.0, guiding(s).progress.distanceAlongM, 0.0)
        assertTrue(location.guidanceActive)
        assertEquals(1, service.starts)
        runCurrent()
        assertEquals(listOf("Conduisez vers l'est."), speech.spoken)
        assertEquals(100, speech.volume)
    }

    @Test fun `a fix updates progress and ETA`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(300.0)); runCurrent()
        val g = guiding(s)
        assertEquals(300.0, g.progress.distanceAlongM, 0.5)
        assertEquals(1, g.progress.nextManeuverIndex)
        assertEquals(testScheduler.currentTime + g.progress.remainingDurationS * 1000, g.etaEpochMs.toDouble(), 1.0)
        assertEquals(36.0, g.speedKmh!!, 1e-9)
        assertEquals(listOf(route.maneuvers[2]), g.upcoming)
        assertTrue(g.lanes.isEmpty())
    }

    @Test fun `near announcement is spoken once`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        val turnAt = tracker.maneuverStartM[1]
        location.emit(fixAt(turnAt - 70.0)); runCurrent()
        location.emit(fixAt(turnAt - 62.0)); runCurrent()
        assertEquals(listOf("Conduisez vers l'est.", "Dans 70 mètres, tournez à droite dans Rue d'Essai."), speech.spoken)
    }

    @Test fun `five fixes 60 m off route trigger one reroute with the preferences`() = runTest {
        val s = session()
        val prefsForRoute = RoutePreferences(avoidTolls = true)
        s.start(option, prefsForRoute); runCurrent()
        repeat(5) { location.emit(fixAt(300.0 + it * 5, northM = 60.0)); runCurrent() }
        assertEquals(1, requests.size)
        val r = requests.single()
        assertEquals(fixAt(320.0, northM = 60.0).point, r.origin)
        assertEquals(route.geometry.last(), r.destination)
        assertEquals(RouteCosting.FASTEST, r.costing)
        assertEquals(prefsForRoute, r.preferences)
        assertTrue(speech.spoken.contains(SpokenPhrases.REROUTING))
        assertEquals("rerouted", guiding(s).route.id)
        assertFalse(guiding(s).rerouting)
    }

    @Test fun `reroute is limited to one every 10 s and the old route is kept on failure`() = runTest {
        routingResult = Outcome.Failure(AppError.Network)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(5) { location.emit(fixAt(300.0, northM = 60.0)); runCurrent() }
        assertEquals(1, requests.size)
        assertEquals("straight", guiding(s).route.id)
        advanceTimeBy(5_000); runCurrent()
        location.emit(fixAt(300.0, northM = 60.0)); runCurrent()
        assertEquals(1, requests.size)
        advanceTimeBy(5_001); runCurrent()
        location.emit(fixAt(300.0, northM = 60.0)); runCurrent()
        assertEquals(2, requests.size)
    }

    @Test fun `a single off-route fix after a long GPS gap waits for the next fix before rerouting`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0)); runCurrent()
        advanceTimeBy(15_000); runCurrent()
        location.emit(fixAt(300.0, northM = 150.0)); runCurrent() // sortie de tunnel : écart > 100 m dès le premier fix
        assertEquals(0, requests.size)
        advanceTimeBy(1_000); runCurrent()
        location.emit(fixAt(310.0, northM = 150.0)); runCurrent()
        assertEquals(1, requests.size)
    }

    @Test fun `arrival announces, publishes Arrived and releases the GPS`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.totalGeometryM)); runCurrent()
        assertEquals(NavigationState.Arrived(route), s.state.value)
        assertFalse(location.guidanceActive)
        assertEquals("Votre destination est sur la droite.", speech.spoken.last())
    }

    @Test fun `arrival lets the last announcement finish before releasing the speech engine`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.totalGeometryM)); runCurrent()
        assertEquals(0, speech.releases)
        advanceTimeBy(GuidanceConstants.SPEECH_RELEASE_DELAY_MS + 1); runCurrent()
        assertEquals(1, speech.releases)
    }

    @Test fun `a new start cancels the pending speech release`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.totalGeometryM)); runCurrent()
        advanceTimeBy(5_000)
        // Le flux rejoue le fix d'arrivée, désormais antérieur au départ de la nouvelle course : il est ignoré.
        s.start(option, RoutePreferences()); runCurrent()
        advanceTimeBy(GuidanceConstants.SPEECH_RELEASE_DELAY_MS + 1); runCurrent()
        assertEquals(0, speech.releases)
    }

    @Test fun `GPS loss extrapolates after 3 s, stops after 10 s and shows the badge`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0, speedMps = 10f)); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        assertEquals(140.0, guiding(s).progress.distanceAlongM, 0.5)
        assertTrue(guiding(s).progress.extrapolated)
        assertFalse(guiding(s).gpsLost)
        advanceTimeBy(7_000); runCurrent()
        // 10 s après le dernier fix réel, l'extrapolation s'arrête (100 m + 10 s × 10 m/s) ; le badge reste affiché.
        assertEquals(200.0, guiding(s).progress.distanceAlongM, 0.5)
        assertTrue(guiding(s).gpsLost)
    }

    @Test fun `a real fix after the GPS loss clears the badge`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0)); runCurrent()
        advanceTimeBy(12_000); runCurrent()
        assertTrue(guiding(s).gpsLost)
        location.emit(fixAt(230.0)); runCurrent()
        assertFalse(guiding(s).gpsLost)
        assertFalse(guiding(s).progress.extrapolated)
        assertEquals(230.0, guiding(s).progress.distanceAlongM, 0.5)
    }

    @Test fun `muted session says nothing`() = runTest {
        val s = session()
        s.setMuted(true)
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        assertTrue(speech.spoken.isEmpty())
        assertTrue(s.muted.value)
        assertTrue(speech.muted)
    }

    @Test fun `muting during the run cuts the speech and unmuting restores it`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        val stopsBefore = speech.stops
        s.setMuted(true); runCurrent()
        assertTrue(speech.stops > stopsBefore)
        assertTrue(speech.muted)
        s.setMuted(false); runCurrent()
        assertFalse(speech.muted)
    }

    @Test fun `disabled voice says nothing`() = runTest {
        prefs.setVoiceEnabled(false)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        assertTrue(speech.spoken.isEmpty())
        assertTrue(speech.muted)
    }

    @Test fun `voice preference changes follow the run`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        assertFalse(speech.muted)
        prefs.setVoiceEnabled(false); runCurrent()
        assertTrue(speech.muted)
        prefs.setVoiceVolume(20); runCurrent()
        assertEquals(20, speech.volume)
    }

    @Test fun `volume preference is applied`() = runTest {
        prefs.setVoiceVolume(35)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        assertEquals(35, speech.volume)
    }

    @Test fun `over speed beeps once when the sound option is on`() = runTest {
        limit = 50
        prefs.setSpeedAlertSound(true)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 20f)); runCurrent() } // 72 km/h > 50 + 5
        assertEquals(1, speech.beeps)
        assertEquals(50, guiding(s).speedLimitKmh)
    }

    @Test fun `over speed beeps again after dropping below the threshold`() = runTest {
        limit = 50
        prefs.setSpeedAlertSound(true)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 20f)); runCurrent() }
        repeat(8) { location.emit(fixAt(200.0 + it * 5, speedMps = 5f)); runCurrent() } // 18 km/h : sous le seuil
        repeat(8) { location.emit(fixAt(300.0 + it * 20, speedMps = 20f)); runCurrent() }
        assertEquals(2, speech.beeps)
    }

    @Test fun `over speed is silent when the sound option is off`() = runTest {
        limit = 50
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 20f)); runCurrent() }
        assertEquals(0, speech.beeps)
    }

    @Test fun `unknown limit never beeps`() = runTest {
        prefs.setSpeedAlertSound(true)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 40f)); runCurrent() }
        assertEquals(0, speech.beeps)
        assertEquals(null, guiding(s).speedLimitKmh)
    }

    @Test fun `stop goes Idle and releases everything`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        s.stop()
        assertEquals(NavigationState.Idle, s.state.value)
        assertFalse(location.guidanceActive)
        assertTrue(speech.stops >= 1)
        assertEquals(1, speech.releases)
        location.emit(fixAt(300.0)); runCurrent()
        assertEquals("plus aucune publication après l'arrêt", NavigationState.Idle, s.state.value)
    }

    @Test fun `retry restarts the last option`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        s.stop()
        s.retry()
        assertEquals(route.id, guiding(s).route.id)
    }

    @Test fun `without location permission the session fails`() = runTest {
        location.permission.value = LocationPermissionState.DENIED
        val s = session()
        s.start(option, RoutePreferences())
        assertEquals(NavigationState.Failed(AppError.PermissionDenied), s.state.value)
        assertEquals(0, service.starts)
        assertFalse(location.guidanceActive)
    }

    @Test fun `a route without maneuvers fails cleanly`() = runTest {
        val s = session()
        s.start(option.copy(route = route.copy(maneuvers = emptyList())), RoutePreferences())
        assertEquals(NavigationState.Failed(AppError.NoResult), s.state.value)
    }

    @Test fun `starting a new route replaces the current one`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        val other = TestRoutes.straight("other")
        s.start(option.copy(route = other), RoutePreferences()); runCurrent()
        assertEquals("other", guiding(s).route.id)
        location.emit(fixAt(300.0)); runCurrent()
        assertEquals("other", guiding(s).route.id)
        assertEquals(2, service.starts)
    }

    @Test fun `unexpected failure publishes Failed and releases GPS`() = runTest {
        val broken = object : UserPreferencesRepository by prefs {
            override val voiceEnabled: Flow<Boolean> = flow { throw IOException("datastore illisible") }
        }
        val s = session(broken)
        s.start(option, RoutePreferences()); runCurrent()
        assertEquals(NavigationState.Failed(AppError.Unknown("datastore illisible")), s.state.value)
        assertFalse(location.guidanceActive)
    }

    @Test fun `disabled voice also silences the over-speed beep`() = runTest {
        limit = 50
        prefs.setSpeedAlertSound(true)
        prefs.setVoiceEnabled(false)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 20f)); runCurrent() }
        assertEquals(50, guiding(s).speedLimitKmh)
        assertEquals(0, speech.beeps)
    }

    @Test fun `a fix older than the run start is ignored`() = runTest {
        advanceTimeBy(10_000)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(300.0, timestampMs = 0L)); runCurrent() // rejeu périmé
        assertEquals(0.0, guiding(s).progress.distanceAlongM, 0.0)
        location.emit(fixAt(300.0, timestampMs = 10_000L)); runCurrent()
        assertEquals(300.0, guiding(s).progress.distanceAlongM, 0.5)
    }

    @Test fun `a reroute without answer times out, keeps the old route and allows a new try`() = runTest {
        routeHangs = true
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(5) { location.emit(fixAt(300.0, northM = 60.0)); runCurrent() }
        assertEquals(1, requests.size)
        assertTrue(guiding(s).rerouting)
        advanceTimeBy(10_001); runCurrent()
        assertFalse(guiding(s).rerouting)
        assertEquals("straight", guiding(s).route.id)
        location.emit(fixAt(300.0, northM = 60.0, timestampMs = 10_001L)); runCurrent() // 1er fix après 10 s de silence : on attend le suivant
        advanceTimeBy(1_000); runCurrent()
        location.emit(fixAt(300.0, northM = 60.0, timestampMs = 11_001L)); runCurrent()
        assertEquals(2, requests.size)
    }

    @Test fun `a reroute finishing during a suspended fix does not announce from the old route`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var armed = false
        val gated = object : UserPreferencesRepository by prefs {
            // Une seule lecture est bloquée (celle de l'annonce du tic de surveillance) ; les suivantes passent.
            override val voiceEnabled: Flow<Boolean> = flow {
                if (armed) { armed = false; gate.await() }
                emit(true)
            }
        }
        val s = session(gated)
        s.start(option, RoutePreferences()); runCurrent()
        val turnAt = tracker.maneuverStartM[1]
        location.emit(fixAt(turnAt - 320.0)); runCurrent() // 320 m : au-delà du palier « far » (300 m), rien à annoncer
        assertEquals(listOf("Conduisez vers l'est."), speech.spoken)
        armed = true
        advanceTimeBy(4_000); runCurrent() // position extrapolée à 280 m du virage : annonce « far » suspendue sur voiceEnabled
        assertFalse(armed)
        location.emit(fixAt(turnAt - 280.0, northM = 150.0, timestampMs = 4_000L)); runCurrent() // le recalcul aboutit pendant la suspension
        assertEquals("rerouted", guiding(s).route.id)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("Conduisez vers l'est.", SpokenPhrases.REROUTING), speech.spoken)
        assertEquals("rerouted", guiding(s).progress.routeId)
    }

    @Test fun `a reroute on a moving fix passes the GPS heading`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(5) { location.emit(fixAt(300.0 + it * 5, northM = 60.0)); runCurrent() }
        assertEquals(20f, requests.single().originHeadingDeg)
    }

    @Test fun `a reroute on a slow fix sends no heading`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        // Lent : pas de cap GPS, seule la règle des 100 m s'applique (B6).
        repeat(5) { location.emit(fixAt(300.0, speedMps = 1f, northM = 150.0)); runCurrent() }
        assertEquals(null, requests.single().originHeadingDeg)
    }

    @Test fun `the rerouting phrase is spoken once per off-route episode`() = runTest {
        routingResult = Outcome.Failure(AppError.Network)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(35) {
            advanceTimeBy(1_000)
            location.emit(fixAt(300.0, northM = 60.0, timestampMs = testScheduler.currentTime)); runCurrent()
        }
        assertTrue(requests.size >= 3)
        assertEquals(1, speech.spoken.count { it == SpokenPhrases.REROUTING })
        // Retour sur l'itinéraire puis nouvelle sortie : nouvel épisode, la phrase est redite.
        repeat(3) {
            advanceTimeBy(1_000)
            location.emit(fixAt(300.0, timestampMs = testScheduler.currentTime)); runCurrent()
        }
        repeat(12) { // laisse écouler l'intervalle minimal de 10 s depuis le dernier recalcul
            advanceTimeBy(1_000)
            location.emit(fixAt(300.0, northM = 60.0, timestampMs = testScheduler.currentTime)); runCurrent()
        }
        assertEquals(2, speech.spoken.count { it == SpokenPhrases.REROUTING })
    }

    @Test fun `the badge shows the raw GPS speed while announcements use the smoothed one`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0, speedMps = 10f)); runCurrent()
        location.emit(fixAt(120.0, speedMps = 20f)); runCurrent()
        assertEquals(72.0, guiding(s).speedKmh!!, 1e-6) // brute (lissée : 46,8)
        location.emit(fixAt(140.0, speedMps = null)); runCurrent()
        assertEquals(null, guiding(s).speedKmh)
    }
}
