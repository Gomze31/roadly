package fr.roadly.feature.car

import androidx.car.app.model.Action
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.navigation.TestNavigationManager
import androidx.car.app.versioning.CarAppApiLevels
import androidx.lifecycle.Lifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.navigation.NavigationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Écran de guidage de la voiture (TST-5) : le gabarit de chaque état est construit avec la bibliothèque Car App, dont les
 * constructeurs valident les contraintes de l'hôte (nombre et type d'actions) — une contrainte violée lèverait ici (AA-5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarGuidanceScreenTest {
    private var world: CarTestWorld? = null

    private fun world(permission: LocationPermissionState = LocationPermissionState.GRANTED, carApiLevel: Int = CarAppApiLevels.getLatest()) =
        CarTestWorld(permission, carApiLevel).also { world?.close(); world = it }

    @After fun tearDown() { world?.close() }

    private fun CarTestWorld.screen(): CarGuidanceScreen {
        val screen = CarGuidanceScreen(carContext, deps, CarMapRenderer(carContext), carContext.getCarService(TestNavigationManager::class.java))
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        idle()
        return screen
    }

    private fun CarGuidanceScreen.template() = onGetTemplate() as NavigationTemplate

    private fun NavigationTemplate.titles() = actionStrip!!.actions.map { it.title?.toString() }

    @Test fun `guiding shows the manoeuvre, the next one, the arrival estimate and the voice and stop actions`() {
        val w = world()
        w.startGuidance()
        val t = w.screen().template()
        val info = t.navigationInfo as RoutingInfo
        assertEquals(false, info.isLoading)
        assertEquals("Rue d'Essai", info.currentStep!!.road.toString())
        assertNotNull(info.currentDistance)
        assertNotNull(info.nextStep)
        assertNotNull(t.destinationTravelEstimate)
        // Voix (icône) puis « Arrêter » ; pas de bouton domicile sans domicile enregistré.
        assertEquals(2, t.actionStrip!!.actions.size)
        assertNull(t.actionStrip!!.actions[0].title)
        assertNotNull(t.actionStrip!!.actions[1].title)
    }

    @Test fun `the home shortcut joins the guidance actions when home is not the destination`() {
        val w = world()
        w.home.value = Place("home", "Maison", null, w.route.geometry.first(), PlaceKind.COORDINATES, "test")
        w.startGuidance()
        assertEquals(3, w.screen().template().actionStrip!!.actions.size)
        // Domicile = destination en cours : le raccourci disparaît.
        w.home.value = Place("home", "Maison", null, w.route.geometry.last(), PlaceKind.COORDINATES, "test")
        w.idle()
        assertEquals(2, w.screen().template().actionStrip!!.actions.size)
    }

    @Test fun `the stop action of the guidance screen stops the guidance`() {
        val w = world()
        w.startGuidance()
        val stop = w.screen().template().actionStrip!!.actions.last()
        stop.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        w.idle()
        assertTrue(w.navigation.state.value is NavigationState.Idle)
    }

    @Test fun `arrived shows the arrival message with the destination and one finish action`() {
        val w = world()
        w.startGuidance()
        val screen = w.screen()
        w.emit(w.fixAtPoint(w.route.geometry.lastIndex))
        assertEquals(NavigationState.Arrived(w.route), w.navigation.state.value)
        val t = screen.template()
        val info = t.navigationInfo as MessageInfo
        assertEquals(w.carContext.getString(R.string.car_arrived), info.title.toString())
        assertEquals("Rue d'Essai", info.text.toString())
        assertNull(t.destinationTravelEstimate)
        assertEquals(listOf(w.carContext.getString(R.string.car_finish)), t.titles())
    }

    @Test fun `failed builds with its two titled actions, retry and stop (AA-5)`() {
        // Niveau d'API voiture minimal de l'application (1) et le plus récent de la bibliothèque.
        for (level in listOf(CarAppApiLevels.LEVEL_1, CarAppApiLevels.getLatest())) {
            val w = world(LocationPermissionState.DENIED, level)
            w.startGuidance()
            assertEquals(NavigationState.Failed(AppError.PermissionDenied), w.navigation.state.value)
            val t = w.screen().template()
            val info = t.navigationInfo as MessageInfo
            assertEquals(w.carContext.getString(R.string.car_guidance_failed), info.title.toString())
            assertTrue(info.text.toString().isNotBlank())
            assertEquals(listOf(w.carContext.getString(R.string.car_retry), w.carContext.getString(R.string.car_stop)), t.titles())
            assertTrue(t.actionStrip!!.actions.all { it.type == Action.TYPE_CUSTOM })
        }
    }

    @Test fun `every state builds its template on the oldest car api level too`() {
        val w = world(carApiLevel = CarAppApiLevels.LEVEL_1)
        val screen = w.screen()
        assertNull(screen.template().mapActionStrip)
        w.startGuidance()
        assertTrue(screen.template().navigationInfo is RoutingInfo)
        w.emit(w.fixAtPoint(w.route.geometry.lastIndex))
        assertTrue(screen.template().navigationInfo is MessageInfo)
    }

    @Test fun `stopped guidance leaves a single close action`() {
        val w = world()
        val t = w.screen().template()
        assertNull(t.navigationInfo)
        assertEquals(listOf(w.carContext.getString(R.string.car_close)), t.titles())
    }

    @Test fun `guidance sends the host one trip summary, not one per position`() {
        val w = world()
        val nav = w.carContext.getCarService(TestNavigationManager::class.java)
        w.openSession(state = Lifecycle.State.RESUMED)
        w.startGuidance()
        assertEquals(1, nav.tripsSent.size)
        // Même manœuvre, même distance affichée, même minute d'arrivée : rien n'est renvoyé.
        w.emit(w.fixAtPoint(0))
        assertEquals(1, nav.tripsSent.size)
    }
}
