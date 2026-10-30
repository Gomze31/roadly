package fr.roadly.core.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Démarre [NavigationService] au premier plan ; un refus du système n'interrompt pas le guidage. */
class ForegroundServiceController @Inject constructor(@param:ApplicationContext private val ctx: Context) : NavigationServiceController {
    override fun start() {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, NavigationService::class.java))
        } catch (e: IllegalStateException) {
            // Démarrage refusé hors premier plan : le guidage continue écran allumé, sans notification.
            Log.w("RoadlyNavService", "startForegroundService refusé", e)
        } catch (e: SecurityException) {
            Log.w("RoadlyNavService", "startForegroundService refusé", e)
        }
    }
}
