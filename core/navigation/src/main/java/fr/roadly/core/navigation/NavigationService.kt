package fr.roadly.core.navigation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service de premier plan de type `location`, actif uniquement pendant le guidage ou la conduite libre (spec §2, §8) :
 * notification persistante (manœuvre, distance, arrivée, « Arrêter »), arrêt automatique hors `Guiding`.
 */
@AndroidEntryPoint
class NavigationService : Service() {
    @Inject lateinit var session: NavigationSession
    @Inject lateinit var freeDrive: FreeDriveSession
    @Inject lateinit var carScreen: CarScreenPresence

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var lastContent: Pair<String, String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NavigationNotifications.ensureChannel(this)
        val content = currentContent(session.state.value, freeDrive.state.value, carScreen.active.value)
        // startForeground dans les 5 s suivant startForegroundService : immédiatement, avant toute autre chose.
        val foreground = try {
            ServiceCompat.startForeground(this, NavigationNotifications.NOTIFICATION_ID, NavigationNotifications.build(this, content), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            lastContent = content
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Service de premier plan refusé (permission de localisation ?)", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Service de premier plan refusé (démarrage en arrière-plan ?)", e)
            false
        }
        if (intent?.action == ACTION_STOP) {
            // « Arrêter » de la notification : la conduite libre si elle est active, sinon le guidage.
            if (freeDrive.state.value is FreeDriveState.Active) freeDrive.stop() else session.stop()
        }
        if (!foreground || intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (collector == null) collector = scope.launch {
            combine(session.state, freeDrive.state, carScreen.active, ::Triple).collect { (nav, free, car) -> render(nav, free, car) }
        }
        return START_NOT_STICKY
    }

    /** Guidage prioritaire ; sinon conduite libre ; sinon écran Android Auto ; sinon contenu par défaut (le service va s'arrêter). */
    private fun currentContent(nav: NavigationState, free: FreeDriveState, car: Boolean): Pair<String, String> = when {
        nav is NavigationState.Guiding -> NavigationNotifications.content(this, nav)
        free is FreeDriveState.Active -> NavigationNotifications.freeDriveContent(this, free)
        car -> NavigationNotifications.carContent(this)
        else -> NavigationNotifications.content(this, null)
    }

    private fun render(nav: NavigationState, free: FreeDriveState, car: Boolean) {
        if (!keepForeground(nav, free, car)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val content = currentContent(nav, free, car)
        if (content == lastContent) return
        lastContent = content
        NavigationNotifications.notify(this, NavigationNotifications.build(this, content))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Le service reste au premier plan pendant le guidage, la conduite libre ou tant que l'écran Android Auto est affiché. */
        fun keepForeground(nav: NavigationState, free: FreeDriveState, carScreen: Boolean): Boolean =
            nav is NavigationState.Guiding || free is FreeDriveState.Active || carScreen

        const val ACTION_STOP = "fr.roadly.action.STOP_NAVIGATION"
        const val ACTION_OPEN_NAVIGATION = "fr.roadly.action.OPEN_NAVIGATION"
        private const val TAG = "RoadlyNavService"
    }
}
