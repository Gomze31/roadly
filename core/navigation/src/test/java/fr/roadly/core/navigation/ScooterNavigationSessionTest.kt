package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.ScooterRouteRequest
import fr.roadly.core.model.TravelMode
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.routing.ScooterOptionInfo
import fr.roadly.core.routing.ScooterRouteAnalyzer
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysis
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysisBuilder
import fr.roadly.core.scooter.guidance.ScooterPhrases
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.range.RangeCalibration
import fr.roadly.core.scooter.range.RangeRisk
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import fr.roadly.core.scooter.range.EnergyLeg
import fr.roadly.core.scooter.segment.EdgeAttributes
import fr.roadly.core.scooter.segment.WayCategory
import fr.roadly.core.scooter.valhalla.TraceAttributesResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guidage trottinette de bout en bout sur la session commune (routage et analyse simulés). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScooterNavigationSessionTest {
    private val location = FakeLocationRepository()
    private val speech = FakeSpeechService()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val turnAt = tracker.maneuverStartM[1]
    private val g2 = ScooterPresets.NINEBOT_MAX_G2
    private val scooterRequest = ScooterRouteRequest(g2, ScooterRoutePreferences())
    private val requests = mutableListOf<RouteRequest>()
    private val analyzed = mutableListOf<String>()
    private val contexts = mutableListOf<TripContext>()

    /** Route partagée jusqu'au virage, puis piste cyclable. */
    private fun analysisOf(r: Route): ScooterRouteAnalysis {
        val t = RouteTracker(r).maneuverStartM[1]
        val total = r.distanceMeters
        return ScooterRouteAnalysisBuilder.build(r, TraceAttributesResult(listOf(EdgeAttributes(t, "road", "none", "residential", speedLimitKmh = 30), EdgeAttributes(total - t, "cycleway")), listOf("FR")), null, g2)
    }

    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return Outcome.Success(listOf(TestRoutes.straight("rerouted"))) }
    }
    private val analyzer = object : ScooterRouteAnalyzer {
        override suspend fun analyze(route: Route, request: ScooterRouteRequest, withOsmTags: Boolean): Outcome<ScooterRouteAnalysis> { analyzed += route.id; return Outcome.Success(analysisOf(route)) }
    }
    private val recorder = object : TripRecorder {
        override fun begin(destinationName: String, startedAtMs: Long) = Unit
        override fun onFix(fix: LocationFix, nowMs: Long) = Unit
        override suspend fun end(arrived: Boolean, nowMs: Long) = Unit
        override fun setContext(context: TripContext) { contexts += context }
    }

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, FakeUserPreferencesRepository(), FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), recorder, dispatchers, backgroundScope, analyzer)
    }

    private fun option(battery: Double?): RouteOption {
        val a = analysisOf(route)
        val range = ScooterRangeEstimator(g2).estimate(listOf(EnergyLeg(route.distanceMeters)), battery, 20.0)
        return RouteOption(route, "Sécurisé", 0L, null, false, ScooterOptionInfo(scooterRequest.goal, a, range, emptyList()))
    }

    private fun config(battery: Double?) = ScooterGuidanceConfig(scooterRequest, battery, 20.0, RangeCalibration(), withOsmTags = false)
    // Hors tracé (northM > 0), cap écarté de la route (B6 : distance et cap pour sortir de l'itinéraire).
    private fun fixAt(along: Double, northM: Double = 0.0) = LocationFix(TestRoutes.north(tracker.pointAt(along), northM), if (northM > 0.0) 20f else 90f, 5f, 5f, 0L)
    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    @Test fun `turn onto a cycle track uses the scooter phrasing and tiers`() = runTest {
        val s = session()
        s.start(option(null), RoutePreferences(), scooter = config(null)); runCurrent()
        location.emit(fixAt(turnAt - 70.0)); runCurrent()
        assertTrue(speech.spoken.toString(), speech.spoken.contains("Dans 70 mètres, prenez la piste cyclable à droite."))
        val ui = guiding(s).scooter!!
        assertEquals(WayCategory.SHARED_ROAD, ui.currentCategory)
        assertEquals(WayCategory.CYCLE_TRACK, ui.nextCycleInfrastructure!!.category)
        assertEquals(TravelMode.ELECTRIC_SCOOTER, contexts.single().travelMode)
    }

    @Test fun `car guidance is unchanged when no scooter config is given`() = runTest {
        val s = session()
        s.start(option(null), RoutePreferences()); runCurrent()
        location.emit(fixAt(turnAt - 70.0)); runCurrent()
        assertTrue(speech.spoken.contains("Dans 70 mètres, tournez à droite dans Rue d'Essai."))
        assertEquals(null, guiding(s).scooter)
        assertTrue(contexts.isEmpty())
    }

    @Test fun `reroute after a wrong turn keeps the scooter costing and re-analyses the new route`() = runTest {
        val s = session()
        s.start(option(80.0), RoutePreferences(), scooter = config(80.0)); runCurrent()
        repeat(5) { location.emit(fixAt(300.0 + it * 5, northM = 60.0)); runCurrent() }
        assertEquals(scooterRequest, requests.single().scooter)
        assertEquals(null, requests.single().vehicle)
        assertEquals(listOf("rerouted"), analyzed)
        location.emit(fixAt(330.0)); runCurrent()
        assertEquals("rerouted", guiding(s).route.id)
        assertTrue(guiding(s).scooter!!.analysisAvailable)
        assertTrue(guiding(s).scooter!!.range != null)
    }

    @Test fun `insufficient battery is announced and a new manual level is taken into account`() = runTest {
        val s = session()
        s.start(option(1.0), RoutePreferences(), scooter = config(1.0)); runCurrent()
        location.emit(fixAt(50.0)); runCurrent()
        assertEquals(RangeRisk.INSUFFICIENT, guiding(s).scooter!!.range!!.risk)
        assertTrue(speech.spoken.contains(ScooterPhrases.INSUFFICIENT_RANGE))
        s.setScooterBattery(90.0); runCurrent()
        location.emit(fixAt(80.0)); runCurrent()
        assertEquals(RangeRisk.OK, guiding(s).scooter!!.range!!.risk)
    }
}
