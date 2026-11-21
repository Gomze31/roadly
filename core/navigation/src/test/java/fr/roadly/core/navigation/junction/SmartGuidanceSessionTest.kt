package fr.roadly.core.navigation.junction

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.navigation.GuidanceDensity
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.PositioningPolicy
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.FixScript
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guidage intelligent de bout en bout, session réelle sur trajets réels simulés : mode rond-point (Étoile, 6e sortie), situation de
 * conduite et voix progressive à une sortie d'autoroute (Toulouse, sortie 19 après 11 km), confirmation avant un long tronçon.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartGuidanceSessionTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }

    private fun TestScope.session(topology: JunctionTopologyProvider = NoJunctionTopologyProvider): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(
            location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope, junctionTopology = topology,
        )
    }

    private fun option(route: Route) = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)

    private suspend fun TestScope.play(s: NavigationSession, fixes: List<LocationFix>, onEach: (NavigationState.Guiding) -> Unit) {
        fixes.forEach { f ->
            advanceTimeBy(1_000)
            location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent()
            (s.state.value as? NavigationState.Guiding)?.let(onEach)
        }
    }

    private fun topologyOf(name: String) = object : JunctionTopologyProvider {
        override suspend fun topology(route: Route, maneuverIndex: Int, window: JunctionWindow): Outcome<JunctionTopology> =
            Outcome.Success(RealJunctions.topology(name, route, maneuverIndex))
    }

    @Test fun `etoile - the roundabout mode counts the exits down and says to leave only once the 5th is behind`() = runTest {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val t = RouteTracker(route)
        val s = session(topologyOf("etoile")); s.start(option(route), RoutePreferences()); runCurrent()
        val exitPhrase = route.maneuvers[enter + 1].verbalPre!!.trimEnd('.')
        val phases = ArrayList<RoundaboutPhase?>()
        val remaining = ArrayList<Int>()
        var saidWhileNext: Boolean? = null
        play(s, FixScript(route).drive(t.maneuverStartM[enter] - 250.0, t.maneuverStartM[enter + 1] + 90.0, speedKmh = 28.0)) { g ->
            val live = g.roundaboutLive
            phases += live?.phase
            live?.exitsRemaining?.let { remaining += it }
            if (saidWhileNext == null && speech.spoken.any { it.startsWith(exitPhrase) }) saidWhileNext = live?.exitIsNext == true
            if (live?.phase == RoundaboutPhase.INSIDE) {
                assertEquals(GuidanceMode.ROUNDABOUT, g.situation.mode)
                assertEquals(GuidanceDensity.REINFORCED, g.situation.density)
            }
        }
        assertEquals(listOf(RoundaboutPhase.APPROACH, RoundaboutPhase.INSIDE, RoundaboutPhase.EXITED, null), phases.dropWhile { it == null }.distinct())
        // Le décompte ne remonte jamais et se termine à zéro.
        assertEquals(remaining.sortedDescending(), remaining)
        assertEquals(0, remaining.last())
        assertTrue("au moins 4 paliers observés à 1 Hz : ${remaining.distinct()}", remaining.distinct().size >= 4)
        assertEquals("« Quittez le rond-point » dit une seule fois", 1, speech.spoken.count { it.startsWith(exitPhrase) })
        assertEquals("…et seulement quand la sortie suivante est la bonne", true, saidWhileNext)
        // L'entrée a été annoncée avant, avec le rang de sortie de Valhalla.
        assertTrue(speech.spoken.any { it.contains("6ème sortie") })
    }

    @Test fun `etoile without topology - same drive, no countdown is claimed and the exit is still announced`() = runTest {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val t = RouteTracker(route)
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        var seen = 0
        play(s, FixScript(route).drive(t.maneuverStartM[enter] - 150.0, t.maneuverStartM[enter + 1] + 30.0, speedKmh = 28.0)) { g ->
            g.roundaboutLive?.let { seen++; assertNull(it.exitsRemaining); assertEquals(1, it.layout.branches.size) }
        }
        assertTrue(seen > 5)
        assertEquals(1, speech.spoken.count { it.startsWith(route.maneuvers[enter + 1].verbalPre!!.trimEnd('.')) })
    }

    @Test fun `motorway exit 19 at 110 km h - anticipation, then keep right, then the manoeuvre, in that order`() = runTest {
        val route = RealJunctions.route("motorway")
        val exit = 11
        val t = RouteTracker(route)
        val keepRight = assertNotNull(PositioningPolicy.phrase(route.maneuvers[exit])).let { PositioningPolicy.phrase(route.maneuvers[exit])!! }
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        val modes = ArrayList<GuidanceMode>()
        play(s, FixScript(route).drive(t.maneuverStartM[exit] - 2_600.0, t.maneuverStartM[exit] - 20.0, speedKmh = 110.0)) { g ->
            if (g.progress.nextManeuverIndex == exit) modes += g.situation.mode
        }
        assertEquals(listOf(GuidanceMode.CRUISE, GuidanceMode.EXIT), modes.distinct())
        val forExit = speech.spoken.dropWhile { !it.startsWith("Dans 2") && !it.startsWith("Dans 1,") }
        assertEquals("placement dit une seule fois", 1, speech.spoken.count { it == keepRight })
        val far = forExit.indexOfFirst { it.startsWith("Dans ") }
        val place = forExit.indexOf(keepRight)
        val now = forExit.indexOfLast { it == route.maneuvers[exit].verbalPre || it.startsWith(route.maneuvers[exit].verbalPre!!.trimEnd('.')) }
        assertTrue("anticipation ($far) < placement ($place) < manœuvre ($now) : $forExit", far in 0 until place && place < now)
    }

    @Test fun `after joining the motorway for 11 km the driver is told so, once`() = runTest {
        val route = RealJunctions.route("motorway")
        val merge = 10
        val t = RouteTracker(route)
        val confirmation = route.maneuvers[merge].verbalPost!!
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(s, FixScript(route).drive(t.maneuverStartM[merge] - 200.0, t.maneuverStartM[merge] + 600.0, speedKmh = 90.0)) {}
        assertEquals(1, speech.spoken.count { it == confirmation })
        // Les courts tronçons (60 m, 300 m…) ne sont jamais confirmés.
        assertTrue(speech.spoken.none { it.startsWith("Continuez pendant") && it != confirmation })
    }
}
