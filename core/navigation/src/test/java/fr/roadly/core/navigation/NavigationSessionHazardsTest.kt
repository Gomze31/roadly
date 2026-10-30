package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zones de vigilance et pluie dans la course (LOT 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionHazardsTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Success(listOf(TestRoutes.straight("rerouted")))
    }
    private var limit: Int? = null
    private val speedLimits = object : SpeedLimitProvider {
        override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = Outcome.Success(limit)
    }

    private fun TestScope.session(zones: List<RouteHazard> = emptyList(), raining: Boolean? = null): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val vigilance = object : VigilanceZoneProvider { override suspend fun zonesAlong(route: Route) = zones }
        val rain = object : RainProvider { override suspend fun isRaining(at: GeoPoint): Outcome<Boolean?> = Outcome.Success(raining) }
        return NavigationSession(location, routing, speedLimits, speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, vigilance, rain, dispatchers, backgroundScope)
    }

    private fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)
    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    @Test fun `entering a vigilance zone is announced once with a beep and exposed as active`() = runTest {
        val zone = RouteHazard(HazardKind.VIGILANCE_ZONE, 200.0, 400.0, speedLimitKmh = 90)
        val s = session(zones = listOf(zone))
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0)); runCurrent()
        assertNull(guiding(s).activeZone)
        assertEquals(1, guiding(s).hazards.count { it.kind == HazardKind.VIGILANCE_ZONE })
        location.emit(fixAt(250.0)); runCurrent()
        assertEquals(zone, guiding(s).activeZone)
        assertTrue(speech.spoken.contains("Zone de vigilance, limitation à 90."))
        assertEquals(1, speech.beeps)
        location.emit(fixAt(300.0)); runCurrent()
        assertEquals(1, speech.spoken.count { it.startsWith("Zone de vigilance") })
        location.emit(fixAt(450.0)); runCurrent()
        assertNull(guiding(s).activeZone)
    }

    @Test fun `vigilance disabled means no zones and no announcement`() = runTest {
        prefs.setVigilanceEnabled(false)
        val s = session(zones = listOf(RouteHazard(HazardKind.VIGILANCE_ZONE, 200.0, 400.0, speedLimitKmh = 90)))
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(250.0)); runCurrent()
        assertNull(guiding(s).activeZone)
        assertTrue(guiding(s).hazards.none { it.kind == HazardKind.VIGILANCE_ZONE })
        assertEquals(0, speech.beeps)
    }

    @Test fun `rain lowers the displayed limit only when the option is on`() = runTest {
        limit = 90
        val s = session(raining = true)
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0)); runCurrent()
        // Première requête météo après le premier fix (boucle toutes les 10 min).
        advanceTimeBy(GuidanceConstants.RAIN_CHECK_INTERVAL_MS + 1); runCurrent()
        location.emit(fixAt(140.0)); runCurrent()
        assertEquals(true, guiding(s).raining)
        assertEquals(90, guiding(s).speedLimitKmh)
        assertFalse(guiding(s).limitLoweredByRain)
        prefs.setRainLowersLimit(true); runCurrent()
        location.emit(fixAt(160.0)); runCurrent()
        assertEquals(80, guiding(s).speedLimitKmh)
        assertTrue(guiding(s).limitLoweredByRain)
    }
}
