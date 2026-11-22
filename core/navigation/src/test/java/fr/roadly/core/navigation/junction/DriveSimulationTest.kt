package fr.roadly.core.navigation.junction

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.GpxTrack
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RouteSimulation
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Outil de validation : trajet simulé le long d'un itinéraire réel et lecture de traces GPX, puis rejeu complet dans la session. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveSimulationTest {
    private val motorway = RealJunctions.route("motorway")

    @Test fun `the simulation tool exists only in debuggable builds`() {
        val debuggable = android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
        assertTrue(fr.roadly.core.navigation.DriveSimulationGate.allowed(debuggable or android.content.pm.ApplicationInfo.FLAG_HAS_CODE))
        // Version publiée : aucun autre drapeau de l'application n'ouvre la simulation.
        assertTrue(!fr.roadly.core.navigation.DriveSimulationGate.allowed(0))
        assertTrue(!fr.roadly.core.navigation.DriveSimulationGate.allowed(debuggable.inv()))
    }

    @Test fun `simulated drive covers the whole route at plausible speeds with bounded acceleration`() {
        val fixes = RouteSimulation.fixes(motorway)
        assertTrue(fixes.size > 200)
        assertEquals(fixes.map { it.timestampMs }.sorted(), fixes.map { it.timestampMs })
        assertTrue(fixes.last().point.distanceTo(motorway.geometry.last()) < 1.0)
        assertEquals(0f, fixes.last().speedMps)
        val speeds = fixes.map { it.speedMps!!.toDouble() }
        assertTrue("jamais plus de 137 km/h : ${speeds.max() * 3.6}", speeds.max() <= 38.0)
        assertTrue("autoroute atteinte : ${speeds.max() * 3.6} km/h", speeds.max() * 3.6 > 80.0)
        speeds.dropLast(4).zipWithNext().forEach { (a, b) -> assertTrue("variation ${b - a} m/s en 1 s", b - a <= 2.0 + 1e-6 && a - b <= 2.5 + 1e-6) }
        // La durée simulée reste du même ordre que celle de Valhalla (accélérations et freinages en plus).
        val simulatedS = fixes.last().timestampMs / 1000.0
        assertTrue("simulé $simulatedS s, Valhalla ${motorway.durationSeconds} s", simulatedS in motorway.durationSeconds * 0.8..motorway.durationSeconds * 1.6)
    }

    @Test fun `the simulated drive slows down before roundabouts`() {
        val fixes = RouteSimulation.fixes(motorway)
        val enter = motorway.maneuvers.indexOfLast { it.type == ManeuverType.ROUNDABOUT_ENTER }
        val at = motorway.geometry[motorway.maneuvers[enter].beginShapeIndex]
        val near = fixes.minByOrNull { it.point.distanceTo(at) }!!
        assertTrue("vitesse à l'entrée du rond-point : ${near.speedMps!! * 3.6} km/h", near.speedMps!! * 3.6 < 60.0)
    }

    @Test fun `replaying the simulated drive through the session reaches the destination without a single reroute`() = runTest {
        val location = FakeLocationRepository()
        val speech = FakeSpeechService()
        var reroutes = 0
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> { reroutes++; return Outcome.Failure(AppError.Network) }
        }
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val s = NavigationSession(
            location, routing, NoopSpeedLimitProvider(), speech, FakeUserPreferencesRepository(), FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
        )
        s.start(RouteOption(motorway, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        val modes = HashSet<GuidanceMode>()
        var maxIndex = 0
        for (f in RouteSimulation.fixes(motorway)) {
            advanceTimeBy(1_000)
            location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent()
            (s.state.value as? NavigationState.Guiding)?.let { modes += it.situation.mode; maxIndex = maxOf(maxIndex, it.progress.nextManeuverIndex) }
            if (s.state.value is NavigationState.Arrived) break
        }
        assertTrue("arrivé : ${s.state.value::class.simpleName}, manœuvre $maxIndex/${motorway.maneuvers.lastIndex}", s.state.value is NavigationState.Arrived)
        assertEquals(0, reroutes)
        // Le trajet complet traverse les grandes situations : croisière, rond-point, sortie, bifurcation, arrivée.
        assertTrue("modes vus : $modes", modes.containsAll(listOf(GuidanceMode.CRUISE, GuidanceMode.ROUNDABOUT_APPROACH, GuidanceMode.ROUNDABOUT, GuidanceMode.EXIT, GuidanceMode.ARRIVAL)))
        // Aucune phrase dite deux fois de suite, aucune annonce après l'arrivée d'une manœuvre déjà passée.
        speech.spoken.zipWithNext().forEach { (a, b) -> assertTrue("répétition immédiate : $a", a != b) }
    }

    @Test fun `gpx track - times, speed and bearing are read or derived, bad points skipped`() {
        val gpx = """
            <?xml version="1.0"?><gpx><trk><trkseg>
            <trkpt lat="43.6000" lon="1.4400"><time>2026-11-21T10:00:00Z</time><hdop>1.2</hdop></trkpt>
            <trkpt lat="43.6001" lon="1.4400"><time>2026-11-21T10:00:01Z</time></trkpt>
            <trkpt lat="95.0" lon="1.44"><time>2026-11-21T10:00:02Z</time></trkpt>
            <trkpt lat="43.6001" lon="1.4402"><time>2026-11-21T10:00:03Z</time><extensions><gpxtpx:speed>9.5</gpxtpx:speed></extensions></trkpt>
            <trkpt lat="43.6002" lon="1.4402"><time>2026-11-21T10:00:03Z</time></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()
        val fixes = GpxTrack.parse(gpx)
        assertEquals(listOf(0L, 1_000L, 3_000L), fixes.map { it.timestampMs })
        assertEquals(6f, fixes[0].accuracyM, 0.01f)
        assertEquals(11.1f, fixes[1].speedMps!!, 0.3f)          // 11,1 m en 1 s, vers le nord
        assertEquals(0f, fixes[1].bearingDeg!!, 1f)
        assertEquals(9.5f, fixes[2].speedMps!!, 0.01f)          // vitesse de la trace
        assertEquals(90f, fixes[2].bearingDeg!!, 1f)            // vers l'est
        assertEquals(fixes[1].bearingDeg, fixes[0].bearingDeg)
    }

    @Test fun `gpx without times is replayed at one point per second, and an empty file gives nothing`() {
        val fixes = GpxTrack.parse("""<gpx><rte><rtept lat="48.85" lon="2.35"/><rtept lat="48.851" lon="2.35"/><rtept lat="48.852" lon="2.35"/></rte></gpx>""")
        assertEquals(listOf(0L, 1_000L, 2_000L), fixes.map { it.timestampMs })
        assertTrue(GpxTrack.parse("<gpx></gpx>").isEmpty())
        assertTrue(GpxTrack.parse("pas du gpx").isEmpty())
    }
}
