package fr.roadly.feature.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Interrupteur « Localisation » du téléphone. Coupé, Android ne fournit aucune position (la demande de Roadly reste « inactive ») :
 * la carte de la voiture ne peut pas suivre le véhicule. Roadly ne peut pas le rallumer lui-même ; il le signale.
 */
internal object CarLocationSwitch {
    fun isOn(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: true

    /** État courant puis chaque changement (diffusion système `MODE_CHANGED`). */
    fun changes(context: Context): Flow<Boolean> = callbackFlow {
        trySend(isOn(context))
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { trySend(isOn(context)) }
        }
        val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply { addAction(LocationManager.PROVIDERS_CHANGED_ACTION) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
