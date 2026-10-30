package fr.roadly.feature.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.map.CameraMode
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.LocationFix
import java.util.Locale

@Composable
fun NavigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit, vm: NavigationViewModel = hiltViewModel()) {
    // Mode conduite (spec §2) : typographie ×1,3 et cibles 56 dp via LocalDrivingMode = true.
    RoadlyTheme(darkTheme = isDark, drivingMode = true) {
        NavigationContent(isDark, onExit, onChangeDestination, vm)
    }
}

@Composable
private fun NavigationContent(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit, vm: NavigationViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    KeepScreenOn()
    // Retour système = « Arrêter » (pas d'entrée « Reprendre » sur l'accueil en Phase 2).
    BackHandler { vm.stop() }

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex()) }
    LaunchedEffect(state.phase) { if (state.phase == NavigationPhase.IDLE) onExit() }
    val route = state.route
    LaunchedEffect(route?.id) {
        if (route != null) {
            map.setRoutes(listOf(route), route.id)
            map.setMarkers(emptyList())
            map.clearTraversed()
        }
    }
    val progress = state.progress
    val following = state.following
    LaunchedEffect(progress, following) {
        if (route != null && progress != null) {
            val fix = LocationFix(progress.snapped, progress.bearingDeg, null, 5f, 0L)
            map.setUserLocation(fix)
            map.setTraversed(route.geometry, progress.segmentIndex, progress.snapped)
            // Suivi : zoom 17, cap, inclinaison 0 (CameraMode.FOLLOW de la v1).
            if (following) map.applyCameraMode(CameraMode.FOLLOW, fix)
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture)
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(RoadlySpacing.m),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            state.banner?.let { ManeuverBanner(it) }
            if (state.lanes.isNotEmpty()) LaneStrip(state.lanes)
            if (state.rerouting) {
                GlassPanel(contentPadding = 12.dp) { Text(stringResource(R.string.nav_rerouting), style = MaterialTheme.typography.titleMedium, color = c.accent) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.m),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            DrivingHud(state.speedKmh, state.speedLimitKmh, state.toleranceKmh, state.gpsLost)
            when (state.phase) {
                NavigationPhase.GUIDING -> {
                    if (state.upcoming.isNotEmpty()) UpcomingStrip(state.upcoming)
                    EtaPanel(
                        etaClock = state.etaClock, remainingDuration = state.remainingDuration, remainingDistance = state.remainingDistance,
                        muted = state.muted, following = state.following,
                        onStop = vm::stop, onToggleMute = vm::toggleMute, onRecenter = vm::recenter, onChangeDestination = onChangeDestination,
                    )
                }
                NavigationPhase.ARRIVED -> ArrivedPanel(onFinish = vm::stop)
                NavigationPhase.FAILED -> FailedPanel(state.error, onRetry = vm::retry, onStop = vm::stop)
                NavigationPhase.LOADING, NavigationPhase.IDLE -> GlassPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nav_loading), color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ArrivedPanel(onFinish: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(stringResource(R.string.nav_arrived_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            GlassButton(stringResource(R.string.nav_finish), onClick = onFinish, icon = Icons.Default.Flag, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FailedPanel(error: AppError?, onRetry: () -> Unit, onStop: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(stringResource(R.string.nav_failed_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text((error ?: AppError.Unknown(null)).userMessage(), style = MaterialTheme.typography.bodyMedium, color = c.danger)
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(stringResource(R.string.nav_retry), onClick = onRetry, modifier = Modifier.weight(1f))
                GlassButton(stringResource(R.string.nav_stop), onClick = onStop, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** `FLAG_KEEP_SCREEN_ON` tant que l'écran de guidage est affiché (spec §2, mode conduite). */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
