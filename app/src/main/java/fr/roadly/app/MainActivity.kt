package fr.roadly.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.location.PermissionEvents
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var location: LocationRepository
    @Inject lateinit var permissionEvents: PermissionEvents
    private val themeVm: ThemeViewModel by viewModels()

    // Demande FINE + COARSE ; refus sans rationale = « refusé définitivement ».
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) location.markDeniedForever() else location.refreshPermission()
        permissionEvents.changed.tryEmit(Unit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by themeVm.themeMode.collectAsStateWithLifecycle()
            val now by themeVm.nowTick.collectAsStateWithLifecycle()
            val p = location.lastKnown?.point
            val isDark = ThemeResolver.isDark(mode, isSystemInDarkTheme(), p?.lat, p?.lon, now)
            RoadlyTheme(darkTheme = isDark) {
                RoadlyNavHost(
                    isDark = isDark,
                    onRequestLocationPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onOpenAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
                )
            }
        }
    }

    // Au retour (ex. depuis les réglages système), on resynchronise l'état de la permission.
    override fun onResume() { super.onResume(); location.refreshPermission(); permissionEvents.changed.tryEmit(Unit) }
}
