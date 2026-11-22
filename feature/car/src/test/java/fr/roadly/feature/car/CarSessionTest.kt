package fr.roadly.feature.car

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.SessionInfo
import androidx.car.app.model.MessageTemplate
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestAppManager
import androidx.car.app.testing.TestScreenManager
import androidx.car.app.testing.navigation.TestNavigationManager
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.navigation.NavigationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Session et service Android Auto (TST-5), démarrage du service de localisation (AA-3), demande « Navigue vers » (SEC-5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarSessionTest {
    private var world: CarTestWorld? = null

    private fun world(permission: LocationPermissionState = LocationPermissionState.GRANTED) = CarTestWorld(permission).also { world = it }

    @After fun tearDown() { world?.close() }

    private fun CarTestWorld.session(
        intent: Intent = Intent(Intent.ACTION_MAIN),
        state: Lifecycle.State = Lifecycle.State.STARTED,
    ): Pair<RoadlyCarSession, SessionController> = openSession(intent, state)

    private val CarTestWorld.screens get() = carContext.getCarService(TestScreenManager::class.java)
    private val CarTestWorld.nav get() = carContext.getCarService(TestNavigationManager::class.java)

    private fun navigate(uri: String) = Intent(CarContext.ACTION_NAVIGATE, Uri.parse(uri))

    // --- Création ---

    @Test fun `the session opens on the home screen and gives the host its map surface`() {
        val w = world()
        w.session(state = Lifecycle.State.CREATED)
        assertEquals(1, w.screens.screensPushed.size)
        assertTrue(w.screens.top is CarHomeScreen)
        assertNotNull(w.carContext.getCarService(TestAppManager::class.java).surfaceCallback)
        assertEquals(0, w.nav.navigationStartedCount)
    }

    @Test fun `a session opened during guidance shows the guidance screen over the home screen`() {
        val w = world()
        w.startGuidance()
        w.session()
        assertEquals(listOf(CarHomeScreen::class, CarGuidanceScreen::class), w.screens.screensPushed.map { it::class })
        assertTrue(w.screens.top is CarGuidanceScreen)
        assertEquals(1, w.nav.navigationStartedCount)
    }

    // --- AA-3 : service de premier plan `location` jamais démarré sans l'autorisation ---

    @Test fun `with the location permission the visible car screen starts the location service`() {
        val w = world()
        val (_, controller) = w.session(state = Lifecycle.State.CREATED)
        assertEquals(0, w.service.starts)
        w.moveTo(controller, Lifecycle.State.STARTED)
        assertEquals(1, w.service.starts)
    }

    @Test fun `without the location permission nothing is started and the home screen asks for it`() {
        for (state in listOf(LocationPermissionState.UNKNOWN, LocationPermissionState.DENIED, LocationPermissionState.DENIED_FOREVER)) {
            val w = world(state)
            w.session()
            assertEquals("$state", 0, w.service.starts)
            assertTrue("$state", w.screens.top.onGetTemplate() is MessageTemplate)
            w.close()
        }
    }

    @Test fun `the location service starts when the permission is granted while the car screen is shown`() {
        val w = world(LocationPermissionState.DENIED)
        w.session()
        assertEquals(0, w.service.starts)
        w.location.permission.value = LocationPermissionState.GRANTED
        w.idle()
        assertEquals(1, w.service.starts)
    }

    @Test fun `a permission granted while the car screen is hidden starts nothing until it is shown again`() {
        val w = world(LocationPermissionState.DENIED)
        val (_, controller) = w.session()
        w.moveTo(controller, Lifecycle.State.CREATED)
        w.location.permission.value = LocationPermissionState.GRANTED
        w.idle()
        assertEquals(0, w.service.starts)
        w.moveTo(controller, Lifecycle.State.STARTED)
        assertEquals(1, w.service.starts)
    }

    @Test fun `a navigate request without the location permission starts neither the service nor a navigation`() {
        val w = world(LocationPermissionState.DENIED)
        w.location.lastKnown = w.fixAtPoint(0)
        w.session(navigate("geo:48.85,2.32"))
        assertEquals(0, w.service.starts)
        assertEquals(0, w.nav.navigationStartedCount)
        assertEquals(NavigationState.Failed(AppError.PermissionDenied), w.navigation.state.value)
        assertFalse(w.screens.top is CarGuidanceScreen)
    }

    // --- « Navigue vers » ---

    @Test fun `a navigate request with coordinates starts guidance and shows the guidance screen`() {
        val w = world()
        w.location.lastKnown = w.fixAtPoint(0)
        w.session(navigate("geo:0,0?q=48.85,2.32(Rue d'Essai)"))
        assertTrue(w.navigation.state.value is NavigationState.Guiding)
        assertEquals("Rue d'Essai", w.guidance.destinationName)
        assertEquals(48.85, w.routeRequests.first().destination.lat, 1e-9)
        assertEquals(2.32, w.routeRequests.first().destination.lon, 1e-9)
        assertTrue(w.screens.top is CarGuidanceScreen)
        assertEquals(1, w.nav.navigationStartedCount)
    }

    @Test fun `a navigate request with an address opens the search with the text, on creation and on a new intent`() {
        val w = world()
        val (session, _) = w.session(navigate("geo:0,0?q=gare+de+Lyon"))
        assertEquals(Lifecycle.State.STARTED, w.screens.top.lifecycle.currentState)
        assertTrue(w.screens.top is CarSearchScreen)
        assertEquals(listOf("gare de Lyon"), w.searches)
        assertTrue(w.navigation.state.value is NavigationState.Idle)
        session.onNewIntent(navigate("geo:0,0?q=Bellecour"))
        w.idle()
        assertTrue(w.screens.top is CarSearchScreen)
        assertEquals(listOf("gare de Lyon", "Bellecour"), w.searches)
        assertEquals(listOf(CarHomeScreen::class, CarSearchScreen::class, CarSearchScreen::class), w.screens.screensPushed.map { it::class })
    }

    @Test fun `an oversized or unreadable navigate request does nothing, an oversized text is cut before the search`() {
        val w = world()
        w.location.lastKnown = w.fixAtPoint(0)
        val (session, _) = w.session(navigate("geo:48.85,2.32?z=" + "1".repeat(CarNavigateIntent.MAX_URI)))
        session.onNewIntent(navigate("geo:0,0"))
        session.onNewIntent(Intent(CarContext.ACTION_NAVIGATE))
        session.onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.85,2.32")))
        w.idle()
        assertTrue(w.screens.top is CarHomeScreen)
        assertTrue(w.navigation.state.value is NavigationState.Idle)
        assertTrue(w.routeRequests.isEmpty())
        session.onNewIntent(navigate("geo:0,0?q=" + "a".repeat(CarNavigateIntent.MAX_TEXT + 300)))
        w.idle()
        assertEquals(listOf("a".repeat(CarNavigateIntent.MAX_TEXT)), w.searches)
    }

    // --- navigationStarted / navigationEnded ---

    @Test fun `navigation started and ended stay balanced through arrival, stop, restart and the end of the session`() {
        val w = world()
        val (_, controller) = w.session()
        w.startGuidance()
        assertEquals(1 to 0, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
        // Changement d'itinéraire pendant le guidage : même navigation.
        w.startGuidance()
        assertEquals(1 to 0, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
        w.emit(w.fixAtPoint(w.route.geometry.lastIndex))
        assertEquals(NavigationState.Arrived(w.route), w.navigation.state.value)
        assertEquals(1 to 1, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
        assertTrue(w.screens.top is CarGuidanceScreen)
        w.guidance.stop()
        w.idle()
        assertEquals(1 to 1, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
        assertTrue(w.screens.top is CarHomeScreen)
        // Le véhicule est reparti du départ (la dernière position connue, rejouée au nouveau guidage, n'est plus l'arrivée).
        w.emit(w.fixAtPoint(0))
        w.startGuidance()
        assertTrue(w.navigation.state.value is NavigationState.Guiding)
        assertEquals(2 to 1, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
        w.moveTo(controller, Lifecycle.State.DESTROYED)
        assertEquals(2 to 2, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
    }

    @Test fun `the host asking to stop navigation stops the guidance`() {
        val w = world()
        w.session()
        w.startGuidance()
        w.nav.navigationManagerCallback!!.onStopNavigation()
        w.idle()
        assertTrue(w.navigation.state.value is NavigationState.Idle)
        assertEquals(1 to 1, w.nav.navigationStartedCount to w.nav.navigationEndedCount)
    }

    // --- Service ---

    private fun service(debuggable: Boolean): RoadlyCarAppService {
        val app = RuntimeEnvironment.getApplication()
        app.applicationInfo.flags = if (debuggable) app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        else app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        // Contexte attaché sans `onCreate` : l'injection Hilt (application de test non Hilt) n'est pas sollicitée.
        return RoadlyCarAppService().also {
            ReflectionHelpers.callInstanceMethod<Any?>(ContextWrapper::class.java, it, "attachBaseContext", ClassParameter.from(Context::class.java, app))
        }
    }

    @Test fun `the service creates the Roadly session`() {
        val w = world()
        val service = service(debuggable = true).apply { deps = w.deps }
        assertTrue(service.onCreateSession(SessionInfo.DEFAULT_SESSION_INFO) is RoadlyCarSession)
    }

    @Test fun `only a debuggable build accepts every host`() {
        assertSame(HostValidator.ALLOW_ALL_HOSTS_VALIDATOR, service(debuggable = true).createHostValidator())
        val release = service(debuggable = false).createHostValidator()
        assertNotSame(HostValidator.ALLOW_ALL_HOSTS_VALIDATOR, release)
        assertTrue(release.allowedHosts.isNotEmpty())
    }
}
