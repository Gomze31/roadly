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
    @Inject lateinit var restorer: GuidanceRestorer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var lastContent: Pair<String, String>? = null
    /** NAV-1 : une seule tentative de reprise par création du service, quoi qu'il arrive ensuite. */
    private var restoreAttempted = false
    /** Reprise en cours : le service reste au premier plan le temps de la tentative, alors que rien n'est encore en cours. */
    private var restoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * NAV-1 : pendant un guidage le service est « sticky ». Si le téléphone arrête le processus (économie de batterie, écran
     * éteint), Android peut recréer le service, dans un nouveau processus, avec un `intent` nul : rien n'est alors en cours, et
     * [GuidanceRestorer] tente une fois de reprendre la course mémorisée. Sans reprise possible, le service s'arrête et la carte
     * « Reprendre le guidage » de l'accueil reste proposée. Conduite libre et écran Android Auto seuls : pas de redémarrage.
     *
     * NON VÉRIFIÉ hors téléphone : qu'Android (12 à 15, MagicOS) recrée effectivement le service, qu'il accepte alors
     * `startForeground` de type `location` sans que l'application soit à l'écran, et qu'il lui donne la position. Un refus de
     * `startForeground` arrête simplement le service (voir plus bas) ; une position refusée fait échouer la reprise.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NavigationNotifications.ensureChannel(this)
        val restore = intent == null && !restoreAttempted && restorer.idle()
        if (intent == null) restoreAttempted = true
        if (restore) restoring = true
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
            restoring = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (collector == null) collector = scope.launch {
            // Journal du guidage (validation sur téléphone) : une ligne par changement de manœuvre, de situation ou de décompte.
            launch {
                var previous: NavigationState? = null
                session.state.collect { s -> GuidanceTrace.line(previous, s)?.let { Log.i(GuidanceTrace.TAG, it) }; previous = s }
            }
            combine(session.state, freeDrive.state, carScreen.active, ::Triple).collect { (nav, free, car) -> render(nav, free, car) }
        }
        if (restore) scope.launch {
            val outcome = restorer.attempt()
            Log.i(TAG, "Reprise du guidage après redémarrage du service : $outcome")
            restoring = false
            // Reprise faite : la notification du guidage remplace « Reprise du guidage… » ; sinon plus rien ne retient le service.
            render(session.state.value, freeDrive.state.value, carScreen.active.value)
        }
        // « Sticky » seulement pour un guidage (ou sa reprise) : lui seul a une destination mémorisée à reprendre.
        return if (session.state.value is NavigationState.Guiding || restoring) START_STICKY else START_NOT_STICKY
    }

    /** Guidage prioritaire ; sinon conduite libre ; sinon écran Android Auto ; sinon reprise en cours ; sinon contenu par défaut (le service va s'arrêter). */
    private fun currentContent(nav: NavigationState, free: FreeDriveState, car: Boolean): Pair<String, String> = when {
        nav is NavigationState.Guiding -> NavigationNotifications.content(this, nav)
        free is FreeDriveState.Active -> NavigationNotifications.freeDriveContent(this, free)
        car -> NavigationNotifications.carContent(this)
        restoring -> NavigationNotifications.restoringContent(this)
        else -> NavigationNotifications.content(this, null)
    }

    private fun render(nav: NavigationState, free: FreeDriveState, car: Boolean) {
        if (!keepForeground(nav, free, car) && !restoring) {
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
