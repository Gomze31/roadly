package fr.roadly.core.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Économiseur de batterie Android (réglage système), suivi en direct : la 3D est allégée quand il est actif. */
@Composable
fun rememberPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val pm = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val state = remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { state.value = pm.isPowerSaveMode }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        state.value = pm.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
