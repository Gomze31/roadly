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
import fr.roadly.core.navigation.testing.FixScript
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session de bout en bout sur trajets simulés (`FixScript`) : ronds-points, annonces, recalcul, persistance (Étape B). */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionGuidanceTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val requests = mutableListOf<RouteRequest>()
    private var routingResult: Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return routingResult }
    }

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
    }

    private fun option(route: Route) = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    /** Émet [fixes] une par seconde (horodatage = horloge de test) ; [onEach] est appelé après le traitement de chaque fix. */
    private suspend fun TestScope.play(fixes: List<LocationFix>, onEach: (LocationFix) -> Unit = {}) {
        fixes.forEach { f ->
            advanceTimeBy(1_000)
            location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent()
            onEach(f)
        }
    }

    @Test fun `the guiding state exposes the roundabout ahead and keeps it inside the ring`() = runTest {
        val route = TestRoutes.twoRoundabouts()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route).drive(150.0, 150.0, speedKmh = 30.0))
        assertEquals(2, guiding(s).roundabout!!.exitNumber)
        play(FixScript(route).drive(215.0, 215.0, speedKmh = 30.0))
        assertEquals(2, guiding(s).progress.nextManeuverIndex)
        assertEquals("cible figée dans l'anneau", 2, guiding(s).roundabout!!.exitNumber)
        play(FixScript(route).drive(260.0, 260.0, speedKmh = 30.0))
        assertEquals(1, guiding(s).roundabout!!.exitNumber)
        play(FixScript(route).drive(400.0, 400.0, speedKmh = 30.0))
        assertNull(guiding(s).roundabout)
    }

    @Test fun `announcements carry their priority - now urgent, near normal, far low`() = runTest {
        val route = TestRoutes.straight()
        val turnAt = RouteTracker(route).maneuverStartM[1]
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        // turnAt + 200 : manœuvre passée, arrivée encore au-delà du FAR ; sans ce relais, le saut jusqu'au bout sort de la fenêtre de suivi (recalcul).
        play(listOf(turnAt - 250.0, turnAt - 70.0, turnAt - 30.0, turnAt + 200.0).map { FixScript(route).drive(it, it, speedKmh = 36.0).single() })
        play(FixScript(route).drive(RouteTracker(route).totalGeometryM, RouteTracker(route).totalGeometryM, speedKmh = 36.0))
        assertEquals(
            listOf(SpeechPriority.NORMAL, SpeechPriority.LOW, SpeechPriority.NORMAL, SpeechPriority.URGENT, SpeechPriority.URGENT),
            speech.priorities,
        )
    }

    @Test fun `straight drive - distances decrease and no announcement is repeated`() = runTest { // scénario 1
        val route = TestRoutes.straight()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        val distances = mutableListOf<Pair<Int, Double>>()
        play(FixScript(route).drive(0.0, route.distanceMeters - 30.0, speedKmh = 50.0)) {
            val p = guiding(s).progress
            distances += p.nextManeuverIndex to p.distanceToNextManeuverM
        }
        assertEquals(speech.spoken.distinct(), speech.spoken)
        distances.groupBy { it.first }.values.forEach { d -> assertTrue(d.map { it.second }.zipWithNext().all { (a, b) -> b <= a }) }
    }

    @Test fun `130 km h at 1 Hz - the now announcement comes at least 3 s ahead, once`() = runTest {
        val route = TestRoutes.straight()
        val turnAt = RouteTracker(route).maneuverStartM[1]
        val now = route.maneuvers[1].verbalPre!!
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        var nowSpokenAt: Double? = null
        play(FixScript(route).drive(0.0, turnAt + 30.0, speedKmh = 130.0)) {
            if (nowSpokenAt == null && now in speech.spoken) nowSpokenAt = turnAt - guiding(s).progress.distanceAlongM
        }
        assertNotNull(nowSpokenAt)
        assertTrue("NOW à $nowSpokenAt m", nowSpokenAt!! >= 3 * 130 / 3.6)
        assertEquals(1, speech.spoken.count { it == now })
    }

    @Test fun `jitter at the turn never repeats an announcement over 10 fixes`() = runTest {
        val route = TestRoutes.straight()
        val turnAt = RouteTracker(route).maneuverStartM[1]
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route).drive(turnAt - 200.0, turnAt - 20.0, speedKmh = 30.0))
        play(FixScript(route, seed = 4).jitter(turnAt, count = 10, amplitudeM = 8.0))
        assertEquals(speech.spoken.distinct(), speech.spoken)
    }

    @Test fun `two close roundabouts - each entry said once, exit chained with puis, no doubled far`() = runTest { // scénario 6
        val route = TestRoutes.twoRoundabouts()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route, seed = 9).drive(0.0, route.distanceMeters - 40.0, speedKmh = 30.0, noiseM = 2.0))
        assertEquals(speech.spoken.distinct(), speech.spoken)
        assertEquals(1, speech.spoken.count { it == "Entrez dans le rond-point et prenez la 2e sortie." })
        assertEquals(1, speech.spoken.count { it == "Entrez dans le rond-point et prenez la 1re sortie." })
        assertTrue(speech.spoken.any { it == "Quittez le rond-point dans Rue Médiane, puis entrez dans le rond-point et prenez la 1re sortie." })
        assertTrue("pas de FAR pour le 2e rond-point (moins de 10 s après la sortie du 1er)", speech.spoken.none { it.startsWith("Dans 100 mètres, entrez") || it.startsWith("Dans 150 mètres, entrez") })
    }
}
