package fr.roadly.app

import android.Manifest
import kotlinx.coroutines.launch
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.designsystem.theme.LocalPictureInPicture
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.CompositionLocalProvider
import android.util.Rational
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.location.PermissionEvents
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.NavigationService
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var location: LocationRepository
    @Inject lateinit var permissionEvents: PermissionEvents
    @Inject lateinit var navigator: AppNavigator
    @Inject lateinit var session: NavigationSession
    /** Mode image dans l'image en cours (petite fenêtre de guidage). */
    private val inPip = MutableStateFlow(false)
    private val themeVm: ThemeViewModel by viewModels()

    /** Ouverture de l'écran de guidage demandée par la notification. */
    private val openNavigation = MutableStateFlow(false)
    /** Demande externe (raccourci, widget, intent geo: / google.navigation:) à traiter une fois le graphe posé. */
    private val external = MutableStateFlow<ExternalRequest?>(null)
    private var afterNotificationPermission: (() -> Unit)? = null

    // Demande FINE + COARSE ; refus sans rationale = « refusé définitivement ».
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) location.markDeniedForever() else location.refreshPermission()
        permissionEvents.changed.tryEmit(Unit)
    }

    // Accordée ou non, le guidage démarre : sans la permission, seule la notification est masquée.
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotificationPermission?.invoke()
        afterNotificationPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val mode by themeVm.themeMode.collectAsStateWithLifecycle()
            val now by themeVm.nowTick.collectAsStateWithLifecycle()
            val openNav by openNavigation.collectAsStateWithLifecycle()
            val externalRequest by external.collectAsStateWithLifecycle()
            val tint by themeVm.tint.collectAsStateWithLifecycle()
            val p = location.lastKnown?.point
            val isDark = ThemeResolver.isDark(mode, isSystemInDarkTheme(), p?.lat, p?.lon, now)
            // Interface sombre en permanence (panneaux violets sur carte claire, comme la référence) ; la carte, elle, suit jour / nuit.
            // Seul le choix explicite « Clair » rend l'interface claire.
            val pip by inPip.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalPictureInPicture provides pip) {
            RoadlyTheme(darkTheme = isDark || mode != fr.roadly.core.data.repository.ThemeMode.LIGHT, tint = tint) {
                RoadlyNavHost(
                    isDark = isDark,
                    onRequestLocationPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onOpenAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
                    withNotificationPermission = ::withNotificationPermission,
                    openNavigationRequested = openNav,
                    onOpenNavigationHandled = { openNavigation.value = false },
                    externalRequest = externalRequest,
                    onExternalHandled = { external.value = null },
                    navRequests = navigator.requests,
                )
            }
            }
        }
        // Image dans l'image : automatique pendant un guidage (Android 12+), désactivé sinon.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.state.collect { st -> updatePipParams(st is NavigationState.Guiding) }
            }
        }
    }

    private fun pipParams(guiding: Boolean): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(3, 4))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(guiding).setSeamlessResizeEnabled(false)
        return b.build()
    }

    private fun updatePipParams(guiding: Boolean) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(pipParams(guiding)) }
    }

    /** Android 11 et moins : on quitte l'app (touche Accueil) pendant un guidage → petite fenêtre. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && session.state.value is NavigationState.Guiding &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) runCatching { enterPictureInPictureMode(pipParams(true)) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    /** Toucher la notification de guidage ramène à l'écran de guidage (activité déjà ouverte). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == NavigationService.ACTION_OPEN_NAVIGATION) { openNavigation.value = true; return }
        ExternalIntents.parse(intent.action, intent.dataString)?.let { external.value = it }
    }

    /** API 33+ : POST_NOTIFICATIONS demandée au premier démarrage du guidage, puis [block] dans tous les cas. */
    private fun withNotificationPermission(block: () -> Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) { block(); return }
        afterNotificationPermission = block
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Au retour (ex. depuis les réglages système), on resynchronise l'état de la permission.
    override fun onResume() { super.onResume(); location.refreshPermission(); permissionEvents.changed.tryEmit(Unit) }
}
