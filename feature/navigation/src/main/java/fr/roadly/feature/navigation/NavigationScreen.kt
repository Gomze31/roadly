package fr.roadly.feature.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import fr.roadly.core.designsystem.component.HorizonItem
import fr.roadly.core.designsystem.component.HorizonKind
import fr.roadly.core.designsystem.component.HorizonStrip
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.NavInfoBar
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.ReportSheet
import fr.roadly.core.designsystem.component.horizonKind
import fr.roadly.core.designsystem.component.labelRes
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.component.SpeedLimitSign
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.map.CameraMode
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.ManeuverType
import java.util.Locale

@Composable
fun NavigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit, onAddStop: () -> Unit = {}, vm: NavigationViewModel = hiltViewModel()) {
    // Mode conduite (spec §2) : typographie ×1,3 et cibles 56 dp via LocalDrivingMode = true.
    RoadlyTheme(darkTheme = isDark, drivingMode = true) {
        NavigationContent(isDark, onExit, onChangeDestination, onAddStop, vm)
    }
}

@Composable
private fun NavigationContent(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit, onAddStop: () -> Unit, vm: NavigationViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    var bannerExpanded by rememberSaveable { mutableStateOf(true) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    // Retour système = « Arrêter » (pas d'entrée « Reprendre » sur l'accueil en Phase 2).
    BackHandler { vm.stop() }

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive, c.routeCasing) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex(), c.routeCasing.toHex()) }
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

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val landscape = maxWidth > maxHeight
        // Signalements communautaires devant soi (projetés sur le tracé) et retour du dernier envoi.
        val reportsAhead by vm.reportsAhead.collectAsStateWithLifecycle()
        val reportMessage by vm.reportMessage.collectAsStateWithLifecycle()
        val full = state.copy(communityAhead = reportsAhead)
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture)
        }
        if (landscape) {
            LandscapeOverlay(full, bannerExpanded, { bannerExpanded = !bannerExpanded }, { showReport = true }, onChangeDestination, vm)
        } else {
            PortraitOverlay(full, bannerExpanded, { bannerExpanded = !bannerExpanded }, { showReport = true }, onChangeDestination, vm)
        }
        ReportResultPillNav(reportMessage, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 120.dp))
    }

    val communityEnabled by vm.communityEnabled.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    LaunchedEffect(reports) { map.setReports(reports) }
    if (showReport) ReportSheet(onDismiss = { showReport = false }, enabled = communityEnabled, onSelect = vm::report)
    val alternatives by vm.alternativesUi.collectAsStateWithLifecycle()
    if (alternatives != AlternativesUi.Hidden) AlternativesSheet(alternatives, onSelect = vm::switchTo, onRetry = vm::loadAlternatives, onDismiss = vm::dismissAlternatives, onAddStop = { vm.dismissAlternatives(); onAddStop() })
}

/** Portrait : bandeau en haut (replier à droite), actions à droite, compteur + Horizon + barre d'information en bas. */
@Composable
private fun PortraitOverlay(
    state: NavigationUiState,
    bannerExpanded: Boolean,
    onToggleBanner: () -> Unit,
    onReport: () -> Unit,
    onChangeDestination: () -> Unit,
    vm: NavigationViewModel,
) {
    val c = LocalRoadlyColors.current
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                    state.banner?.let { ManeuverBanner(it, Modifier.fillMaxWidth(), expanded = bannerExpanded) }
                    if (state.lanes.isNotEmpty() && bannerExpanded) LaneStrip(state.lanes)
                    if (state.rerouting) ReroutingPill()
                }
                Spacer(Modifier.width(RoadlySpacing.s))
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s), horizontalAlignment = Alignment.End) {
                    RoundIconButton(
                        if (bannerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        stringResource(if (bannerExpanded) R.string.nav_collapse_banner else R.string.nav_expand_banner),
                        onClick = onToggleBanner, size = 44.dp,
                    )
                }
            }
        }

        // Colonne d'actions à droite : signaler, voix, changer de destination.
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
            horizontalAlignment = Alignment.End,
        ) {
            ReportButton(onReport)
            RoundIconButton(
                if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                stringResource(if (state.muted) R.string.nav_unmute else R.string.nav_mute),
                onClick = vm::toggleMute,
            )
            RoundIconButton(Icons.Default.Search, stringResource(R.string.nav_change_destination), onClick = onChangeDestination)
            if (state.phase == NavigationPhase.GUIDING) RoundIconButton(Icons.Default.AltRoute, stringResource(R.string.nav_alternatives), onClick = vm::loadAlternatives)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                SpeedBadge(state.speedKmh, state.speedLimitKmh, toleranceKmh = state.toleranceKmh)
                Spacer(Modifier.width(RoadlySpacing.s))
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs), horizontalAlignment = Alignment.Start) {
                    if (state.gpsLost) GpsLostPill()
                    StatusPills(state)
                }
            }
            BottomBlock(state, vm, onChangeDestination)
        }
    }
}

/** Paysage : colonne gauche (bandeau, voies, Horizon, barre d'information) sur ~45 % ; compteur et actions à droite. */
@Composable
private fun LandscapeOverlay(
    state: NavigationUiState,
    bannerExpanded: Boolean,
    onToggleBanner: () -> Unit,
    onReport: () -> Unit,
    onChangeDestination: () -> Unit,
    vm: NavigationViewModel,
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Colonne gauche : bandeau en haut, Horizon + barre d'information calés en bas (pas de défilement : tout doit rester visible en conduite).
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.46f).padding(RoadlySpacing.screenEdge)) {
            Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                state.banner?.let { ManeuverBanner(it, Modifier.fillMaxWidth(), expanded = bannerExpanded) }
                if (state.lanes.isNotEmpty() && bannerExpanded) LaneStrip(state.lanes)
                if (state.rerouting) ReroutingPill()
            }
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                BottomBlock(state, vm, onChangeDestination)
            }
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
            horizontalAlignment = Alignment.End,
        ) {
            RoundIconButton(
                if (bannerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                stringResource(if (bannerExpanded) R.string.nav_collapse_banner else R.string.nav_expand_banner),
                onClick = onToggleBanner, size = 44.dp,
            )
            ReportButton(onReport)
            RoundIconButton(
                if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                stringResource(if (state.muted) R.string.nav_unmute else R.string.nav_mute),
                onClick = vm::toggleMute,
            )
            RoundIconButton(Icons.Default.Search, stringResource(R.string.nav_change_destination), onClick = onChangeDestination)
            if (state.phase == NavigationPhase.GUIDING) RoundIconButton(Icons.Default.AltRoute, stringResource(R.string.nav_alternatives), onClick = vm::loadAlternatives)
        }
        // Compteur à droite, décalé pour ne pas chevaucher la colonne d'actions (56 dp + marges).
        Row(Modifier.align(Alignment.BottomEnd).padding(start = RoadlySpacing.screenEdge, bottom = RoadlySpacing.screenEdge, end = RoadlySpacing.screenEdge + RoadlySpacing.touchTargetDriving + RoadlySpacing.sm), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs), horizontalAlignment = Alignment.End) {
                if (state.gpsLost) GpsLostPill()
                StatusPills(state)
            }
            Spacer(Modifier.width(RoadlySpacing.s))
            SpeedBadge(state.speedKmh, state.speedLimitKmh, toleranceKmh = state.toleranceKmh)
        }
    }
}

/** Horizon + barre d'information (guidage), ou panneau d'arrivée / d'échec / de chargement. */
@Composable
private fun BottomBlock(state: NavigationUiState, vm: NavigationViewModel, onChangeDestination: () -> Unit) {
    val c = LocalRoadlyColors.current
    when (state.phase) {
        NavigationPhase.GUIDING -> {
            AnimatedVisibility(state.upcoming.isNotEmpty() || state.hazards.isNotEmpty() || state.communityAhead.isNotEmpty()) {
                HorizonStrip(items = horizonItems(state), title = stringResource(R.string.nav_horizon_title), modifier = Modifier.padding(bottom = RoadlySpacing.s))
            }
            NavInfoBar(
                etaClock = state.etaClock, etaLabel = stringResource(R.string.nav_eta_label),
                minutesValue = state.remainingDurationSplit.value, minutesLabel = state.remainingDurationSplit.unit,
                kmValue = state.remainingDistanceSplit.value, kmLabel = state.remainingDistanceSplit.unit,
                showRecenter = !state.following, onRecenter = vm::recenter, onStop = vm::stop,
                recenterDescription = stringResource(R.string.nav_recenter), stopDescription = stringResource(R.string.nav_stop),
            )
        }
        NavigationPhase.ARRIVED -> ArrivedPanel(onFinish = vm::stop)
        NavigationPhase.FAILED -> FailedPanel(state.error, onRetry = vm::retry, onStop = vm::stop)
        NavigationPhase.LOADING, NavigationPhase.IDLE -> GlassPanel(Modifier.fillMaxWidth()) { LoadingRow(stringResource(R.string.nav_loading)) }
    }
}

/** Horizon : manœuvres et arrivée (Valhalla), virages (géométrie) et zones de vigilance (base officielle) ; pas de trafic (⛔ source). */
@Composable
private fun horizonItems(state: NavigationUiState): List<HorizonItem> {
    val maneuvers = state.upcoming.map { u ->
        val kind = if (u.type == ManeuverType.ARRIVE) HorizonKind.ARRIVAL else HorizonKind.MANEUVER
        val what = u.street ?: stringResource(R.string.nav_horizon_maneuver)
        HorizonItem(kind, u.distanceM, "${u.distanceText} · $what", "$what dans ${u.distanceText}")
    }
    val community = state.communityAhead.map { r ->
        val what = stringResource(r.report.kind.labelRes())
        HorizonItem(r.report.kind.horizonKind(), r.distanceM, "${r.distanceText} · $what", "$what dans ${r.distanceText}")
    }
    val hazards = state.hazards.map { h ->
        when (h.kind) {
            HazardKind.CURVE -> {
                val what = stringResource(R.string.nav_horizon_curve)
                HorizonItem(HorizonKind.CURVE, h.distanceM, "${h.distanceText} · $what", "$what dans ${h.distanceText}")
            }
            HazardKind.VIGILANCE_ZONE -> {
                val what = if (h.limitKmh != null) stringResource(R.string.nav_horizon_zone_limit, h.limitKmh) else stringResource(R.string.nav_horizon_zone)
                HorizonItem(HorizonKind.VIGILANCE, h.distanceM, "${h.distanceText} · $what", "${stringResource(R.string.nav_horizon_zone)} dans ${h.distanceText}")
            }
        }
    }
    return (maneuvers + hazards + community).sortedBy { it.distanceM }
}

/** Badge « Zone de vigilance » (avec sa limitation si connue) et badge pluie, à côté du compteur. */
@Composable
private fun StatusPills(state: NavigationUiState) {
    val c = LocalRoadlyColors.current
    val zone = state.activeZone
    if (zone != null) {
        GlassPanel(contentPadding = 8.dp, shape = RoadlyShapes.Chip) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Shield, style = IconTileStyle.Warning, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_zone_active), style = MaterialTheme.typography.labelLarge, color = c.warning)
                zone.speedLimitKmh?.let { Spacer(Modifier.width(8.dp)); SpeedLimitSign(it, size = 30.dp) }
            }
        }
    }
    if (state.raining == true) {
        GlassPanel(contentPadding = 8.dp, shape = RoadlyShapes.Chip) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = c.brandBright, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (state.limitLoweredByRain) R.string.nav_rain_lowered else R.string.nav_rain), style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun ReportButton(onClick: () -> Unit) {
    RoundIconButton(Icons.Default.Warning, stringResource(R.string.nav_report), onClick = onClick, style = RoundButtonStyle.Accent)
}

@Composable
private fun ReroutingPill() {
    val c = LocalRoadlyColors.current
    GlassPanel(contentPadding = 10.dp, shape = RoadlyShapes.Chip) {
        LoadingRow(stringResource(R.string.nav_rerouting))
    }
}

@Composable
private fun GpsLostPill() {
    val c = LocalRoadlyColors.current
    GlassPanel(contentPadding = 10.dp, shape = RoadlyShapes.Chip) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GpsOff, contentDescription = null, tint = c.danger, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.nav_gps_lost), style = MaterialTheme.typography.labelLarge, color = c.danger)
        }
    }
}

@Composable
private fun ArrivedPanel(onFinish: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Flag, style = IconTileStyle.BrandFilled, size = 44.dp)
                Spacer(Modifier.width(RoadlySpacing.sm))
                Column {
                    Text(stringResource(R.string.nav_arrived_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text(stringResource(R.string.nav_arrived_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            GlassButton(stringResource(R.string.nav_finish), onClick = onFinish, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
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
                GlassButton(stringResource(R.string.nav_retry), onClick = onRetry, style = GlassButtonStyle.Brand, modifier = Modifier.weight(1f))
                GlassButton(stringResource(R.string.nav_stop), onClick = onStop, style = GlassButtonStyle.Danger, modifier = Modifier.weight(1f))
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

/** Retour du dernier signalement (« envoyé », « limité », « impossible ») pendant 4 s ; `null` = rien. */
@Composable
private fun ReportResultPillNav(message: String?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    AnimatedVisibility(message != null, modifier = modifier) {
        val ok = message == "sent"
        GlassPanel(contentPadding = 10.dp, shape = RoadlyShapes.Chip) {
            Text(
                stringResource(when { ok -> R.string.nav_report_sent; message == "error:RateLimited" -> R.string.nav_report_rate_limited; else -> R.string.nav_report_failed }),
                style = MaterialTheme.typography.labelLarge, color = if (ok) c.success else c.danger,
            )
        }
    }
}
