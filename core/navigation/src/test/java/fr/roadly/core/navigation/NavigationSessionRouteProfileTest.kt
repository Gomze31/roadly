package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeDriveMemoryRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.NoScooterRouteAnalyzer
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.0 : limitations lues dans le profil du tracé, portiques de péage en flux libre, véhicule mémorisé à l'arrivée. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionRouteProfileTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val memory = FakeDriveMemoryRepository()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val total = RouteGeometry(route.geometry).totalM
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.NoResult)
    }
    /** Appels au fournisseur fix par fix (repli). */
    private var providerCalls = 0
    private val providerLimits = object : SpeedLimitProvider {
        override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> { providerCalls++; return Outcome.Success(null) }
    }

    /** Profil : 0–600 m à 50, 600 m–fin à 90, un portique à 700 m. */
    private val profile = RouteAttributes(
        route.id, total,
        listOf(SpeedLimitSpan(0.0, 600.0, 50), SpeedLimitSpan(600.0, total, 90)),
        listOf(TollGantry(700.0, "A 79", "FR")),
    )

    private fun TestScope.session(attrs: RouteAttributes? = profile): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val provider = object : RouteAttributesProvider {
            override suspend fun attributes(route: Route): Outcome<RouteAttributes> =
                attrs?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NoResult)
        }
        return NavigationSession(
            location, routing, providerLimits, speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
            NoScooterRouteAnalyzer, provider, memory,
        )
    }

    private fun fixAt(along: Double, mps: Float = 15f) = LocationFix(tracker.pointAt(along), 90f, mps, 5f, 0L)
    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    @Test fun `limit and next limit come from the route profile without per-fix requests`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(200.0)); runCurrent()
        assertEquals(50, guiding(s).speedLimitKmh)
        // 15 m/s × 20 s = 300 m d'horizon : le passage à 90 (400 m devant) n'est pas encore annoncé.
        assertNull(guiding(s).nextSpeedLimit)
        location.emit(fixAt(400.0)); runCurrent()
        assertEquals(SpeedLimitChange(90, 200.0), guiding(s).nextSpeedLimit?.let { it.copy(distanceM = Math.round(it.distanceM).toDouble()) })
        location.emit(fixAt(650.0)); runCurrent()
        assertEquals(90, guiding(s).speedLimitKmh)
        assertEquals(0, providerCalls)
    }

    @Test fun `without a profile the per-fix provider stays the fallback`() = runTest {
        val s = session(attrs = null)
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(200.0)); runCurrent()
        assertNull(guiding(s).speedLimitKmh)
        assertTrue(providerCalls > 0)
    }

    @Test fun `a passed gantry is announced once and becomes a reminder at arrival with the parked position`() = runTest {
        val s = session()
        s.start(option, RoutePreferences(), destinationName = "Gare"); runCurrent()
        location.emit(fixAt(650.0)); runCurrent()
        assertNull(guiding(s).freeFlowToll)
        location.emit(fixAt(720.0)); runCurrent()
        val trip = guiding(s).freeFlowToll!!
        assertEquals(1, trip.gantries)
        assertEquals(listOf("A 79"), trip.roads)
        assertEquals(trip.firstPassedAtEpochMs + 72 * 3_600_000L, trip.dueAtEpochMs)
        assertEquals(1, speech.spoken.count { it.startsWith("Péage en flux libre") })
        location.emit(fixAt(800.0)); runCurrent()
        assertEquals(1, speech.spoken.count { it.startsWith("Péage en flux libre") })
        location.emit(fixAt(total)); runCurrent()
        assertTrue(s.state.value is NavigationState.Arrived)
        assertEquals(trip, (s.state.value as NavigationState.Arrived).freeFlowToll)
        val reminder = memory.tolls.value.single()
        assertEquals(listOf("A 79"), reminder.roads)
        assertEquals("FR", reminder.countryCode)
        assertEquals("Gare", memory.parked.value?.label)
    }

    @Test fun `a toll booth is shown in the horizon and announced once about a kilometre before`() = runTest {
        val booth = total - 100.0
        val s = session(profile.copy(tollGantries = emptyList(), tollBooths = listOf(booth), trafficSignals = listOf(300.0)))
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(booth - 1_300.0)); runCurrent()
        assertTrue(guiding(s).hazards.any { it.kind == fr.roadly.core.model.HazardKind.TOLL_BOOTH && it.startAlongM == booth })
        assertTrue(guiding(s).hazards.any { it.kind == fr.roadly.core.model.HazardKind.TRAFFIC_LIGHT })
        assertEquals(0, speech.spoken.count { it.startsWith("Péage dans") })
        location.emit(fixAt(booth - 1_000.0)); runCurrent()
        assertEquals(1, speech.spoken.count { it.startsWith("Péage dans 1 kilomètre") })
        location.emit(fixAt(booth - 600.0)); runCurrent()
        assertEquals(1, speech.spoken.count { it.startsWith("Péage dans") })
    }

    @Test fun `a turn at a traffic light is announced and shown as at the light`() = runTest {
        val turnAt = RouteGeometry(route.geometry).cumulative[route.maneuvers[1].beginShapeIndex]
        val s = session(profile.copy(tollGantries = emptyList(), trafficSignals = listOf(turnAt - 15.0)))
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(200.0)); runCurrent()
        assertTrue(guiding(s).nextAtTrafficLight)
        location.emit(fixAt(turnAt - 120.0)); runCurrent()
        assertTrue(speech.spoken.joinToString(" | "), speech.spoken.any { "au feu, tournez à droite dans Rue d'Essai" in it })
        assertTrue(speech.spoken.none { it.startsWith("Dans") && "au feu" !in it && "Rue d'Essai" in it })
    }

    @Test fun `without a light the Valhalla instruction is unchanged`() = runTest {
        val s = session(profile.copy(tollGantries = emptyList()))
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(200.0)); runCurrent()
        assertFalse(guiding(s).nextAtTrafficLight)
        location.emit(fixAt(RouteGeometry(route.geometry).cumulative[10] - 120.0)); runCurrent()
        assertTrue(speech.spoken.none { "au feu" in it })
    }

    @Test fun `a sharp bend on an open road is announced once with its side, not in town`() = runTest {
        // 600 m vers l'est, coude de 90° vers le nord (virage à gauche), 600 m vers le nord ; aucune manœuvre au coude.
        val pts = (0..20).map { fr.roadly.core.navigation.testing.TestRoutes.xy(it * 30.0, 0.0) } +
            (1..20).map { fr.roadly.core.navigation.testing.TestRoutes.xy(600.0, it * 30.0) }
        val bend = Route(
            "bend", fr.roadly.core.model.RouteCosting.FASTEST, 1_200.0, 60.0, pts,
            listOf(
                fr.roadly.core.model.Maneuver(fr.roadly.core.model.ManeuverType.DEPART, "Conduisez vers l'est.", null, 1_200.0, 60.0, 0, null),
                fr.roadly.core.model.Maneuver(fr.roadly.core.model.ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, pts.lastIndex, null),
            ),
            false, "test",
        )
        val t = RouteTracker(bend)
        fun at(along: Double, mps: Float) = LocationFix(t.pointAt(along), 90f, mps, 5f, 0L)
        val s = session(attrs = null)
        s.start(RouteOption(bend, "x", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        location.emit(at(20.0, 22f)); runCurrent()
        val curveStart = guiding(s).hazards.first { it.kind == fr.roadly.core.model.HazardKind.CURVE }.startAlongM
        assertEquals(false, guiding(s).hazards.first { it.kind == fr.roadly.core.model.HazardKind.CURVE }.rightHand)
        location.emit(at(curveStart - 400.0, 22f)); runCurrent()
        location.emit(at(curveStart - 300.0, 22f)); runCurrent()
        assertEquals(0, speech.spoken.count { it.startsWith("Attention, virage") })
        location.emit(at(curveStart - 150.0, 22f)); runCurrent()
        assertEquals(1, speech.spoken.count { it == "Attention, virage serré à gauche." })
        location.emit(at(curveStart - 80.0, 22f)); runCurrent()
        assertEquals(1, speech.spoken.count { it.startsWith("Attention, virage") })
    }

    @Test fun `a sharp bend taken slowly is not announced`() = runTest {
        val pts = (0..20).map { fr.roadly.core.navigation.testing.TestRoutes.xy(it * 30.0, 0.0) } +
            (1..20).map { fr.roadly.core.navigation.testing.TestRoutes.xy(600.0, -it * 30.0) }
        val bend = Route(
            "bend2", fr.roadly.core.model.RouteCosting.FASTEST, 1_200.0, 60.0, pts,
            listOf(
                fr.roadly.core.model.Maneuver(fr.roadly.core.model.ManeuverType.DEPART, "Conduisez vers l'est.", null, 1_200.0, 60.0, 0, null),
                fr.roadly.core.model.Maneuver(fr.roadly.core.model.ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, pts.lastIndex, null),
            ),
            false, "test",
        )
        val t = RouteTracker(bend)
        val s = session(attrs = null)
        s.start(RouteOption(bend, "x", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        location.emit(LocationFix(t.pointAt(20.0), 90f, 8f, 5f, 0L)); runCurrent()
        assertEquals(true, guiding(s).hazards.first { it.kind == fr.roadly.core.model.HazardKind.CURVE }.rightHand)
        for (a in listOf(300.0, 400.0, 480.0, 540.0)) { location.emit(LocationFix(t.pointAt(a), 90f, 8f, 5f, 0L)); runCurrent() }
        assertEquals(0, speech.spoken.count { it.startsWith("Attention, virage") })
    }

    @Test fun `stopping after a gantry still records the reminder, but parks nothing`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(650.0)); runCurrent()
        location.emit(fixAt(720.0)); runCurrent()
        s.stop(); runCurrent()
        assertEquals(1, memory.tolls.value.size)
        assertNull(memory.parked.value)
    }

}
