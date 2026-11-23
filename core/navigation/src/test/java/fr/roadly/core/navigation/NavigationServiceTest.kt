package fr.roadly.core.navigation

import android.app.Application
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.internal.builders.ServiceComponentBuilder
import dagger.hilt.android.internal.managers.ServiceComponentManager
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.location.LocationPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * TST-1 / NAV-1 : cycle de vie de [NavigationService] sous Robolectric (API 34), avec les vraies sessions branchées sur des
 * doublures ([RestoreWorld]). Le service réel est construit ; l'injection Hilt est remplacée par un composant écrit à la main.
 *
 * Ce que ces tests ne montrent PAS : qu'un vrai téléphone recrée le service après avoir arrêté le processus, ni qu'il lui laisse
 * alors la position. « Redémarrage » = `onStartCommand(null, …)` sur un service neuf dont la session est au repos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = NavigationServiceTest.TestApp::class)
class NavigationServiceTest {
    /** Application de test : rend à Hilt un composant qui injecte le service avec les objets du test. */
    class TestApp : Application(), GeneratedComponentManager<Any> {
        var inject: (NavigationService) -> Unit = {}
        override fun generatedComponent(): Any = object : GeneratedComponent, ServiceComponentManager.ServiceComponentBuilderEntryPoint {
            override fun serviceComponentBuilder(): ServiceComponentBuilder = object : ServiceComponentBuilder {
                override fun service(service: Service): ServiceComponentBuilder = this
                override fun build(): ServiceComponent = Injector(inject)
            }
        }
    }

    private class Injector(val inject: (NavigationService) -> Unit) : ServiceComponent, GeneratedComponent, NavigationService_GeneratedInjector {
        override fun injectNavigationService(navigationService: NavigationService) = inject(navigationService)
    }

    private val app = RuntimeEnvironment.getApplication() as TestApp
    // Fil principal de Robolectric : les sessions confinent leur travail avec `limitedParallelism`, que les répartiteurs
    // « unconfined » de test refusent ; les délais suivent l'horloge de la boucle principale, comme ceux du service.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var now = 1_000_000_000_000L
    private val w = RestoreWorld(scope, Dispatchers.Main, NavigationClock { now })
    private val carScreen = CarScreenPresence()
    private val intent = Intent(app, NavigationService::class.java)
    private val controllers = mutableListOf<ServiceController<NavigationService>>()

    /** Service neuf (comme après la création par Android), injecté avec le monde du test. */
    private fun service(): NavigationService {
        val wire: (NavigationService) -> Unit = { it.session = w.session; it.freeDrive = w.freeDrive; it.carScreen = carScreen; it.restorer = w.restorer }
        app.inject = wire
        val controller = Robolectric.buildService(NavigationService::class.java, intent)
        controllers += controller
        return controller.create().get().also(wire)
    }

    private fun stop() = Intent(app, NavigationService::class.java).setAction(NavigationService.ACTION_STOP)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun title(s: NavigationService) = shadowOf(shadowOf(s).lastForegroundNotification).contentTitle?.toString()
    private fun emit() = runBlocking { w.location.emit(w.fix()) }

    @After fun tearDown() {
        controllers.forEach { runCatching { it.destroy() } }
        scope.cancel()
    }

    // ---- Démarrage ----

    @Test fun `started during a guidance the service goes foreground with the location type and is sticky`() {
        w.startGuidance()
        val s = service()
        assertEquals(Service.START_STICKY, s.onStartCommand(intent, 0, 1))
        idle()
        val shadow = shadowOf(s)
        assertEquals(NavigationNotifications.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isLastForegroundNotificationAttached)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, s.foregroundServiceType)
        // La notification annonce la prochaine manœuvre, pas celle du départ.
        assertEquals(w.route.maneuvers[w.guiding!!.progress.nextManeuverIndex].instruction, title(s))
        assertFalse(shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)
    }

    @Test fun `free drive or the car screen alone keep the service foreground but not sticky - there is no destination to resume`() {
        assertTrue(w.freeDrive.start())
        val free = service()
        assertEquals(Service.START_NOT_STICKY, free.onStartCommand(intent, 0, 1))
        assertFalse(shadowOf(free).isStoppedBySelf)
        assertTrue(shadowOf(free).isLastForegroundNotificationAttached)
        w.freeDrive.stop()
        idle()
        assertTrue(shadowOf(free).isStoppedBySelf)

        carScreen.set(true)
        val car = service()
        assertEquals(Service.START_NOT_STICKY, car.onStartCommand(intent, 0, 1))
        assertFalse(shadowOf(car).isStoppedBySelf)
        carScreen.set(false)
        idle()
        assertTrue(shadowOf(car).isStoppedBySelf)
    }

    @Test fun `started with nothing in progress the service stops at once`() {
        val s = service()
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(intent, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertTrue(shadowOf(s).isForegroundStopped)
        assertTrue(w.requests.isEmpty())
    }

    // ---- Arrêt ----

    @Test fun `ACTION_STOP stops the guidance and the service`() {
        w.startGuidance()
        val s = service()
        s.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(stop(), 0, 2))
        idle()
        assertEquals(NavigationState.Idle, w.session.state.value)
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertNull("la course arrêtée n'est plus mémorisée", w.prefs.last.value)
    }

    @Test fun `ACTION_STOP during a free drive stops the free drive`() {
        assertTrue(w.freeDrive.start())
        val s = service()
        s.onStartCommand(intent, 0, 1)
        s.onStartCommand(stop(), 0, 2)
        idle()
        assertFalse(w.freeDrive.state.value is FreeDriveState.Active)
        assertTrue(shadowOf(s).isStoppedBySelf)
    }

    @Test fun `the service stops when the guidance ends, fails or is stopped from the application`() {
        // Arrêt depuis l'écran de guidage.
        w.startGuidance()
        val stopped = service()
        stopped.onStartCommand(intent, 0, 1)
        assertFalse(shadowOf(stopped).isStoppedBySelf)
        w.session.stop()
        idle()
        assertTrue(shadowOf(stopped).isStoppedBySelf)
        assertTrue(shadowOf(stopped).isForegroundStopped)

        // Échec : un nouveau départ sans permission de localisation publie `Failed`.
        w.startGuidance()
        val failed = service()
        failed.onStartCommand(intent, 0, 1)
        assertFalse(shadowOf(failed).isStoppedBySelf)
        w.location.permission.value = LocationPermissionState.DENIED
        w.startGuidance()
        idle()
        assertTrue(w.session.state.value is NavigationState.Failed)
        assertTrue(shadowOf(failed).isStoppedBySelf)
    }

    @Test fun `only a guidance, a free drive or the car screen keep the service foreground`() {
        val guiding = w.run { startGuidance(); guiding!! }
        val active = FreeDriveState.Active(0L)
        assertTrue(NavigationService.keepForeground(guiding, FreeDriveState.Idle, false))
        assertTrue(NavigationService.keepForeground(NavigationState.Idle, active, false))
        assertTrue(NavigationService.keepForeground(NavigationState.Idle, FreeDriveState.Idle, true))
        assertFalse(NavigationService.keepForeground(NavigationState.Idle, FreeDriveState.Idle, false))
        assertFalse(NavigationService.keepForeground(NavigationState.Arrived(w.route), FreeDriveState.Idle, false))
        assertFalse(NavigationService.keepForeground(NavigationState.Failed(AppError.Network), FreeDriveState.Failed(AppError.Network), false))
    }

    // ---- Refus du système ----

    @Test fun `startForeground refused with a SecurityException - no crash, the service stops, the guidance goes on`() {
        w.startGuidance()
        val s = service()
        shadowOf(s).setThrowInStartForeground(SecurityException("Starting FGS with type location requires permissions"))
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(intent, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertNotNull("le guidage continue écran allumé", w.guiding)
    }

    @Test fun `startForeground refused from the background (IllegalStateException) - no crash, the service stops`() {
        w.startGuidance()
        val s = service()
        shadowOf(s).setThrowInStartForeground(IllegalStateException("startForegroundService() not allowed"))
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(intent, 0, 1))
        assertTrue(shadowOf(s).isStoppedBySelf)
    }

    @Test fun `restart refused by the system - nothing is resumed and the guidance stays remembered for the home card`() {
        w.remember()
        emit()
        val s = service()
        shadowOf(s).setThrowInStartForeground(SecurityException("location not allowed from the background"))
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertTrue(w.requests.isEmpty())
        assertNotNull(w.prefs.last.value)
    }

    // ---- Redémarrage par le système (intent nul, nouveau processus) ----

    @Test fun `sticky restart with a recent remembered guidance resumes it and stays foreground`() {
        w.remember(name = "Maison")
        emit()
        val s = service()
        assertEquals(Service.START_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertEquals("repris-1", w.guiding?.route?.id)
        assertEquals(1, w.requests.size)
        assertEquals("Maison", w.prefs.last.value?.destinationName)
        assertFalse(shadowOf(s).isStoppedBySelf)
        assertFalse(shadowOf(s).isForegroundStopped)
        assertTrue(shadowOf(s).isLastForegroundNotificationAttached)
    }

    @Test fun `sticky restart with nothing remembered stops the service`() {
        emit()
        val s = service()
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertTrue(shadowOf(s).isForegroundStopped)
        assertTrue(w.requests.isEmpty())
    }

    @Test fun `sticky restart with a guidance that is too old stops the service and keeps it remembered`() {
        w.remember(ageMs = GuidanceRestorer.MAX_AGE_MS + 60_000L)
        emit()
        val s = service()
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertTrue(w.requests.isEmpty())
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `sticky restart without the location permission stops the service and keeps the guidance remembered`() {
        w.remember()
        emit()
        w.location.permission.value = LocationPermissionState.DENIED
        val s = service()
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertTrue(w.requests.isEmpty())
        assertNotNull(w.prefs.last.value)
        assertEquals(NavigationState.Idle, w.session.state.value)
    }

    @Test fun `sticky restart with a route failure stops the service and keeps the guidance remembered`() {
        w.remember()
        emit()
        w.answer = { Outcome.Failure(AppError.Network) }
        val s = service()
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertEquals(1, w.requests.size)
        assertNotNull(w.prefs.last.value)
        assertFalse(w.location.guidanceActive)
    }

    @Test fun `sticky restart without a position waits in the foreground, then stops after 20 s`() {
        w.remember()
        val s = service()
        assertEquals(Service.START_STICKY, s.onStartCommand(null, 0, 1))
        idle()
        assertFalse(shadowOf(s).isStoppedBySelf)
        assertEquals("Reprise du guidage…", title(s))
        assertTrue(w.location.guidanceActive)
        shadowOf(Looper.getMainLooper()).idleFor(GuidanceRestorer.POSITION_TIMEOUT_MS + 1_000, TimeUnit.MILLISECONDS)
        assertTrue(shadowOf(s).isStoppedBySelf)
        assertTrue(shadowOf(s).isForegroundStopped)
        assertTrue(w.requests.isEmpty())
        assertFalse(w.location.guidanceActive)
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `a position arriving during the wait resumes the guidance`() {
        w.remember()
        val s = service()
        s.onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
        assertNull(w.guiding)
        emit()
        idle()
        assertNotNull(w.guiding)
        assertFalse(shadowOf(s).isStoppedBySelf)
        assertEquals(1, w.requests.size)
    }

    @Test fun `only one resume attempt per service creation`() {
        w.remember()
        emit()
        w.answer = { Outcome.Failure(AppError.Network) }
        val s = service()
        s.onStartCommand(null, 0, 1)
        idle()
        assertEquals(1, w.requests.size)
        // Le système relivre un intent nul au même service : pas de second essai, même si le calcul réussirait maintenant.
        w.answer = { Outcome.Success(listOf(w.route)) }
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(null, 0, 2))
        s.onStartCommand(null, 0, 3)
        idle()
        assertEquals(1, w.requests.size)
        assertNull(w.guiding)
        assertNotNull(w.prefs.last.value)
    }

    @Test fun `a null intent while a guidance is running in this process changes nothing`() {
        w.startGuidance()
        emit()
        val s = service()
        s.onStartCommand(intent, 0, 1)
        assertEquals(Service.START_STICKY, s.onStartCommand(null, 0, 2))
        idle()
        assertTrue(w.requests.isEmpty())
        assertEquals(w.route.id, w.guiding?.route?.id)
        assertFalse(shadowOf(s).isStoppedBySelf)
    }

    @Test fun `ACTION_STOP during the resume wait stops the service and nothing starts afterwards`() {
        w.remember()
        val s = service()
        s.onStartCommand(null, 0, 1)
        assertEquals(Service.START_NOT_STICKY, s.onStartCommand(stop(), 0, 2))
        assertTrue(shadowOf(s).isStoppedBySelf)
        controllers.last().destroy()
        emit()
        shadowOf(Looper.getMainLooper()).idleFor(GuidanceRestorer.POSITION_TIMEOUT_MS + 1_000, TimeUnit.MILLISECONDS)
        assertNull(w.guiding)
        assertTrue(w.requests.isEmpty())
        assertFalse(w.location.guidanceActive)
    }
}
