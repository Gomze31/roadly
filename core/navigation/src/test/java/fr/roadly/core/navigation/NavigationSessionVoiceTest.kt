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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E1 : la session parle, se tait et se met en sourdine sur le canal du guidage seulement (scénarios 16 et 17). Pas d'`advanceUntilIdle()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionVoiceTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
    }

    private fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)

    @Test fun `announcements, stop and mute of the session use the guidance channel only`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 62.0)); runCurrent()
        s.setMuted(true); runCurrent()
        s.setMuted(false); runCurrent()
        s.stop(); runCurrent()
        assertEquals(listOf("Conduisez vers l'est.", "Dans 70 mètres, tournez à droite dans Rue d'Essai."), speech.spoken)
        assertEquals(listOf(VoiceChannel.GUIDANCE, VoiceChannel.GUIDANCE), speech.channels)
        assertTrue(speech.stoppedChannels.isNotEmpty())
        assertTrue("ni arrêt de l'assistant, ni arrêt global : ${speech.stoppedChannels}", speech.stoppedChannels.all { it == VoiceChannel.GUIDANCE })
        assertFalse(VoiceChannel.ASSISTANT in speech.mutedChannels)
    }

    @Test fun `scenario 16 - voice off silences the guidance channel, never the assistant`() = runTest {
        prefs.setVoiceEnabled(false)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        assertTrue(speech.spoken.isEmpty())
        assertEquals(setOf(VoiceChannel.GUIDANCE), speech.mutedChannels)
    }

    @Test fun `a run gives back the guidance mute and leaves the assistant mute as it was`() = runTest {
        speech.setMuted(true, VoiceChannel.ASSISTANT)
        val s = session()
        s.setMuted(true)
        s.start(option, RoutePreferences()); runCurrent()
        assertEquals(setOf(VoiceChannel.ASSISTANT, VoiceChannel.GUIDANCE), speech.mutedChannels)
        s.stop(); runCurrent()
        assertEquals("sourdine de l'assistant intacte, celle du guidage rendue avec la course", setOf(VoiceChannel.ASSISTANT), speech.mutedChannels)
    }
}
