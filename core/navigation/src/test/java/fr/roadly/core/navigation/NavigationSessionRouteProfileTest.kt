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
