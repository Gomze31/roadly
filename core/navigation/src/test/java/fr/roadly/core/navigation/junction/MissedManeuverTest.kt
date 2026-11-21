package fr.roadly.core.navigation.junction

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.MissedManeuverDetector
import fr.roadly.core.navigation.MissedManeuverDetector.Sample
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Manœuvre manquée : preuve par la croissance régulière de l'écart après un point de décision, jamais sur une seule position. */
@OptIn(ExperimentalCoroutinesApi::class)
class MissedManeuverTest {
    private fun sample(off: Double, since: Double? = 100.0, toNext: Double = 2_000.0, accuracy: Float = 5f, extrapolated: Boolean = false) =
        Sample("r", off, accuracy, extrapolated, since, toNext)

    private fun MissedManeuverDetector.feed(vararg offs: Double, since: Double? = 100.0) = offs.map { update(sample(it, since)) }

    @Test fun `a growing gap after a manoeuvre is a missed manoeuvre, on the third growing position`() {
        assertEquals(listOf(false, false, false, false, true), MissedManeuverDetector().feed(4.0, 13.0, 16.0, 19.5, 23.0))
    }

    @Test fun `a constant offset - parallel road, wide carriageway - never triggers`() {
        assertTrue(MissedManeuverDetector().feed(30.0, 30.2, 29.8, 30.1, 30.0, 30.3, 29.9, 30.2).none { it })
        assertTrue(MissedManeuverDetector().feed(8.0, 9.0, 10.0, 11.0, 11.5, 11.9).none { it })
    }

    @Test fun `a single jump, or a jump that comes back, never triggers`() {
        assertTrue(MissedManeuverDetector().feed(2.0, 80.0, 3.0, 2.0, 4.0).none { it })
        assertTrue(MissedManeuverDetector().feed(2.0, 40.0, 60.0, 3.0, 2.0).none { it })
    }

    @Test fun `far from any manoeuvre the general rule is left alone`() {
        val d = MissedManeuverDetector()
        assertTrue(listOf(13.0, 16.0, 19.5, 23.0, 30.0).none { d.update(sample(it, since = 5_000.0)) })
        assertTrue(listOf(13.0, 16.0, 19.5, 23.0, 30.0).none { d.update(sample(it, since = null, toNext = 900.0)) })
    }

    @Test fun `just before the manoeuvre point counts too - driving past a turn`() {
        val d = MissedManeuverDetector()
        assertEquals(listOf(false, false, true), listOf(13.0, 17.0, 21.0).map { d.update(sample(it, since = null, toNext = 12.0)) })
    }

    @Test fun `doubtful positions neither build nor break the evidence`() {
        val d = MissedManeuverDetector()
        assertFalse(d.update(sample(13.0)))
        assertFalse(d.update(sample(17.0)))
        assertFalse(d.update(sample(60.0, accuracy = 45f)))         // ignorée
        assertFalse(d.update(sample(70.0, extrapolated = true)))    // ignorée
        assertTrue(d.update(sample(22.0)))
        // Avec une précision de 20 m, il faut un écart de 30 m pour commencer à compter.
        val blurry = MissedManeuverDetector()
        assertTrue(listOf(14.0, 18.0, 22.0, 26.0).none { blurry.update(sample(it, accuracy = 20f)) })
    }

    @Test fun `once reported the detector starts over, and a new route resets it`() {
        val d = MissedManeuverDetector()
        assertEquals(true, d.feed(13.0, 16.0, 19.5, 23.0).last())
        assertFalse(d.update(sample(26.0)))
        d.feed(13.0, 16.0)
        assertFalse(d.update(Sample("other", 20.0, 5f, false, 100.0, 2_000.0)))
    }

    // --- Session complète : sortie 19 manquée à 110 km/h ---

    private fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        val rad = bearingDeg * PI / 180.0
        return GeoPoint(p.lat + meters * cos(rad) / 111_320.0, p.lon + meters * sin(rad) / (111_320.0 * cos(p.lat * PI / 180.0)))
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat); val dl = Math.toRadians(b.lon - a.lon)
        return (Math.toDegrees(atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl))) + 360.0) % 360.0
    }

    @Test fun `missed motorway exit at 110 km h - reroute asked within seconds, not after a kilometre`() = runTest {
        val route = RealJunctions.route("motorway")
        val exit = route.maneuvers.indexOfLast { it.type == ManeuverType.EXIT_RIGHT && it.exitNumber != null }
        val t = RouteTracker(route)
        val exitAt = t.maneuverStartM[exit]
        val location = FakeLocationRepository()
        val speech = FakeSpeechService()
        val requests = ArrayList<Long>()
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += testScheduler.currentTime; return Outcome.Failure(AppError.Network) }
        }
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val s = NavigationSession(
            location, routing, NoopSpeedLimitProvider(), speech, FakeUserPreferencesRepository(), FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
        )
        s.start(RouteOption(route, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        suspend fun emit(f: LocationFix) { advanceTimeBy(1_000); location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent() }
        // Jusqu'à la sortie, sur l'itinéraire.
        FixScript(route).drive(exitAt - 600.0, exitAt, speedKmh = 110.0).forEach { emit(it) }
        assertEquals("aucun recalcul avant la sortie", 0, requests.size)
        // La sortie est manquée : le véhicule continue tout droit, dans l'axe de l'autoroute.
        val straight = bearing(t.pointAt(exitAt - 60.0), t.pointAt(exitAt))
        val passedAt = testScheduler.currentTime
        val origin = t.pointAt(exitAt)
        var metres = 0.0
        var seconds = 0
        while (requests.isEmpty() && seconds < 60) {
            metres += 110.0 / 3.6
            seconds++
            emit(LocationFix(offset(origin, straight, metres), straight.toFloat(), (110.0 / 3.6).toFloat(), 5f, 0L))
        }
        assertTrue("recalcul demandé", requests.isNotEmpty())
        val delayS = (requests.first() - passedAt) / 1000
        // Trois positions croissantes au moins : jamais sur un seul fix ; et bien avant la règle des 100 m.
        assertTrue("délai $delayS s, $metres m après la sortie", delayS in 3..25)
        assertTrue("« Recalcul » dit une fois : ${speech.spoken}", speech.spoken.count { it == "Recalcul de l'itinéraire." } == 1)
        val g = s.state.value as NavigationState.Guiding
        assertTrue(g.rerouting || requests.isNotEmpty())
    }

    @Test fun `staying on the route through the same exit never asks for a reroute`() = runTest {
        val route = RealJunctions.route("motorway")
        val exit = route.maneuvers.indexOfLast { it.type == ManeuverType.EXIT_RIGHT && it.exitNumber != null }
        val t = RouteTracker(route)
        val location = FakeLocationRepository()
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
            location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), FakeUserPreferencesRepository(), FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
        )
        s.start(RouteOption(route, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        // Bruit de 5 m, précision 9 m, voie décalée de 6 m : la bretelle est prise normalement.
        for (f in FixScript(route, seed = 3).drive(t.maneuverStartM[exit] - 500.0, t.maneuverStartM[exit] + 900.0, speedKmh = 90.0, noiseM = 5.0, accuracyM = 9f, lateralM = 6.0)) {
            advanceTimeBy(1_000); location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent()
        }
        assertEquals(0, reroutes)
    }
}
