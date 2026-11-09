package fr.roadly.feature.navigation

import androidx.compose.ui.draw.rotate

import androidx.compose.animation.core.animateFloatAsState

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.designsystem.component.DangerZonePill
import fr.roadly.core.map.DangerZoneFeatures
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.HorizonItem
import fr.roadly.core.designsystem.component.HorizonKind
import fr.roadly.core.designsystem.component.HorizonStrip
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.LoadingRow
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
import fr.roadly.core.designsystem.theme.CockpitTint
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.scooter.map.ScooterRouteFeatures
import fr.roadly.core.scooter.osm.ScooterPoiKind
import fr.roadly.core.designsystem.component.BatteryDialog
import fr.roadly.core.designsystem.component.rememberVoiceInput
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Mic
import kotlin.math.roundToInt
import fr.roadly.core.map.AttributionPlacement
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.map.MapController
import fr.roadly.core.map.NavigationCameraController
import fr.roadly.core.map.NavigationCameraInput
import fr.roadly.core.map.ManeuverArrowBuilder
import fr.roadly.core.map.MapRendering
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.map.rememberPowerSaveMode
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay
import fr.roadly.core.map.junction.JunctionDetail
import fr.roadly.core.map.junction.rememberJunctionScene
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionPhase

@Composable
fun NavigationScreen(
    isDark: Boolean,
    onExit: () -> Unit,
    onChangeDestination: () -> Unit,
    onAddStop: () -> Unit = {},
    assistantButton: @Composable () -> Unit = {},
    assistantPanel: @Composable () -> Unit = {},
    vm: NavigationViewModel = hiltViewModel(),
) {
    // Mode conduite (spec §2) : typographie ×1,3 et cibles 56 dp via LocalDrivingMode = true.
    // Même teinte que le reste de l'app : le thème conduite est recomposé avec la teinte courante.
    // Interface : même clair / sombre que l'app (sombre en permanence sauf choix « Clair ») ; la carte suit [isDark].
    val uiDark = LocalRoadlyColors.current.isDark
    val tint = LocalRoadlyColors.current.brand
    val cockpit = CockpitTint.entries.firstOrNull { (if (uiDark) it.night else it.day).first == tint } ?: CockpitTint.MAGENTA
    RoadlyTheme(darkTheme = uiDark, drivingMode = true, tint = cockpit) {
        NavigationContent(isDark, onExit, onChangeDestination, onAddStop, vm, assistantButton, assistantPanel)
    }
}

@Composable
private fun NavigationContent(
    isDark: Boolean,
    onExit: () -> Unit,
    onChangeDestination: () -> Unit,
    onAddStop: () -> Unit,
    vm: NavigationViewModel,
    assistantButton: @Composable () -> Unit,
    assistantPanel: @Composable () -> Unit,
) {
    // C3 : l'état est lu deux fois — par délégation pour les overlays, par `snapshotFlow` (sans recomposition) pour la carte.
    val uiState = vm.uiState.collectAsStateWithLifecycle()
    val state by uiState
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    var bannerExpanded by rememberSaveable { mutableStateOf(true) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    // OpenGL ES 3 indisponible sur l'appareil : la vue 3D de carrefour est retirée pour la session (le guidage 2D reste complet).
    var junctionUnsupported by remember { mutableStateOf(false) }
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
    val cameraMode = vm.cameraMode.collectAsStateWithLifecycle()
    // E2 : économie d'énergie du réglage — Auto suit l'économiseur d'Android, Activée / Désactivée le forcent.
    val systemSaver = rememberPowerSaveMode()
    val powerSaveSetting = vm.powerSaveMode.collectAsStateWithLifecycle()
    val powerSave = remember { derivedStateOf { powerSaveSetting.value.effective(systemSaver.value) } }
    // E2 « Bâtiments 3D » : en mode 3D seulement, et jamais en économie d'énergie.
    val buildings3d = vm.map3d.collectAsStateWithLifecycle()
    // Bâtiments : couleur de la carte (jour / nuit), l'interface restant sombre en permanence.
    val buildingsColor = (if (isDark) fr.roadly.core.designsystem.theme.RoadlyColors.Night else fr.roadly.core.designsystem.theme.RoadlyColors.Day).buildings3d
    LaunchedEffect(cameraMode.value, powerSave.value, buildings3d.value, buildingsColor) {
        map.setBuildings3d(buildings3d.value && cameraMode.value == CameraMode3D.FOLLOW_3D && !powerSave.value, buildingsColor.toHex())
    }
    LaunchedEffect(c.brand) { map.setArrowColor("#FFFFFF", c.brand.toHex()) }
    val approach = state.approach
    LaunchedEffect(route?.id, approach?.maneuverShapeIndex) {
        if (route != null && approach != null) map.setManeuverArrow(ManeuverArrowBuilder.build(route.geometry, approach.maneuverShapeIndex)) else map.clearManeuverArrow()
    }
    // Radar Intelligence : zones de danger peintes sur le tracé ; recalculées seulement à l'entrée, à la sortie ou au recalcul.
    LaunchedEffect(route?.id, state.dangerZones) {
        map.setDangerZones(if (route == null) DangerZoneFeatures.EMPTY else DangerZoneFeatures.build(route.geometry, state.dangerZones))
    }
    DisposableEffect(map) { onDispose { map.setDangerZones(DangerZoneFeatures.EMPTY) } }
    val mapDetail by vm.mapDetail.collectAsStateWithLifecycle()
    LaunchedEffect(mapDetail) { map.applyDetail(mapDetail) }
    // Retour depuis la recherche : la dernière caméra du guidage revient avec son inclinaison, sans attendre la prochaine position.
    DisposableEffect(map) { onDispose { vm.rememberCamera(map.cameraPosition()) } }
    LaunchedEffect(map) { vm.lastCamera?.let { map.restore(it, keepTilt = true) } }
    val camera = remember { NavigationCameraController() }
    val bottomPanel = vm.bottomPanelFraction.collectAsStateWithLifecycle()
    val topStack = vm.topStackFraction.collectAsStateWithLifecycle()
    val zoomOffset = vm.zoomOffset.collectAsStateWithLifecycle()
    val pitchPreset = vm.pitchPreset.collectAsStateWithLifecycle()
    // Tracé trottinette par type de voie (piste, bande, voie verte, route partagée, chemin, vigilance).
    val analysis = state.scooter?.analysis
    LaunchedEffect(route?.id, analysis) {
        map.setScooterRoute(if (route != null && analysis != null) ScooterRouteFeatures.build(route.geometry, analysis) else ScooterRouteFeatures.EMPTY)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val landscape = maxWidth > maxHeight
        // D5 : icône d'attribution toujours accessible, en haut à droite, zone libre que le panneau du bas soit replié ou déplié.
        // Portrait : sous le bouton « replier ». Paysage : à sa gauche (dessous, Signaler la couvrirait).
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navInsets = WindowInsets.navigationBars.asPaddingValues()
        val attribution = if (landscape) {
            AttributionPlacement.guidanceLandscape(statusTop, navInsets.calculateRightPadding(LayoutDirection.Ltr), RoadlySpacing.screenEdge, BANNER_TOGGLE_SIZE, RoadlySpacing.s)
        } else {
            AttributionPlacement.guidancePortrait(statusTop, RoadlySpacing.screenEdge, BANNER_TOGGLE_SIZE, RoadlySpacing.s, RoadlySpacing.sm)
        }
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera, bottomPanel, topStack, zoomOffset, pitchPreset, buildings3d)
        // Signalements communautaires devant soi (projetés sur le tracé) et retour du dernier envoi.
        val reportsAhead by vm.reportsAhead.collectAsStateWithLifecycle()
        val reportMessage by vm.reportMessage.collectAsStateWithLifecycle()
        val showThen by vm.showNextManeuverPreview.collectAsStateWithLifecycle()
        // E2 : la ligne « puis … » suit le réglage, pour les deux overlays (un seul point, `BannerStack` inchangé).
        val full = state.copy(communityAhead = reportsAhead).withNextManeuverPreview(showThen)
        // Vue 3D de carrefour : scène préparée dès l'étape PREPARE, panneau montré à partir de REVEAL (sous le bandeau en portrait).
        val junctionPalette = rememberJunctionPalette(isDark)
        val junctionDetail = if (powerSave.value) JunctionDetail.REDUCED else JunctionDetail.FULL
        val junction = state.junction?.takeUnless { junctionUnsupported }
        val junctionScene = rememberJunctionScene(route, junction, map, junctionPalette, junctionDetail)
        val junctionFps = if (powerSave.value) MapRendering.MAX_FPS_POWER_SAVE else MapRendering.MAX_FPS
        val junctionSlot: (@Composable (Modifier) -> Unit)? = if (junction != null && junctionScene != null && junctionPanelVisible(junction, junctionScene)) {
            { m -> JunctionPanel(junctionScene, junction, state.junctionUi, junctionFps, m, onUnsupported = { junctionUnsupported = true }) }
        } else null
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(
                styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture,
                maximumFps = if (powerSave.value) MapRendering.MAX_FPS_POWER_SAVE else MapRendering.MAX_FPS,
                attribution = attribution,
            )
        }
        if (landscape) {
            LandscapeOverlay(full, bannerExpanded, { bannerExpanded = !bannerExpanded }, { showReport = true }, onChangeDestination, vm, assistantButton, assistantPanel, junctionSlot)
        } else {
            PortraitOverlay(full, bannerExpanded, { bannerExpanded = !bannerExpanded }, { showReport = true }, onChangeDestination, vm, assistantButton, assistantPanel, junctionSlot)
        }
        ReportResultPillNav(reportMessage, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 120.dp))
        // Image dans l'image : la carte continue sous une carte opaque lisible à petite taille (manœuvre, distance, rue, arrivée).
        if (fr.roadly.core.designsystem.theme.LocalPictureInPicture.current) PipGuidance(full, Modifier.fillMaxSize())
    }

    val communityEnabled by vm.communityEnabled.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    LaunchedEffect(reports) { map.setReports(reports) }
    if (showReport) ReportSheet(onDismiss = { showReport = false }, enabled = communityEnabled, onSelect = vm::report)
    val zoneToHide by vm.zoneToHide.collectAsStateWithLifecycle()
    if (zoneToHide != null) HideZoneDialog(onConfirm = vm::confirmHideZone, onDismiss = vm::cancelHideZone)
    val pois by vm.pois.collectAsStateWithLifecycle()
    if (pois != PoisUi.Hidden) ScooterPoiSheet(pois, onSelect = vm::navigateToPoi, onDismiss = vm::dismissPois)
    val alternatives by vm.alternativesUi.collectAsStateWithLifecycle()
    if (alternatives != AlternativesUi.Hidden) AlternativesSheet(alternatives, onSelect = vm::switchTo, onRetry = vm::loadAlternatives, onDismiss = vm::dismissAlternatives, onAddStop = { vm.dismissAlternatives(); onAddStop() })
    val fuelAhead by vm.fuelAheadUi.collectAsStateWithLifecycle()
    if (fuelAhead != FuelAheadUi.Hidden) FuelAheadSheet(fuelAhead, onPick = vm::addFuelStop, onRetry = vm::loadFuelAhead, onDismiss = vm::dismissFuelAhead)
    GuidanceSheets(state, vm)
}

/** Portrait : bandeau en haut (replier à droite), actions à droite, compteur + Horizon + barre d'information en bas. */
@Composable
internal fun PortraitOverlay(
    state: NavigationUiState,
    bannerExpanded: Boolean,
    onToggleBanner: () -> Unit,
    onReport: () -> Unit,
    onChangeDestination: () -> Unit,
    vm: NavigationViewModel,
    assistantButton: @Composable () -> Unit,
    assistantPanel: @Composable () -> Unit,
    junction: (@Composable (Modifier) -> Unit)? = null,
) {
    val c = LocalRoadlyColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val junctionHeight = maxHeight * JUNCTION_PORTRAIT_FRACTION
        // Passe appareil : bas de la pile du haut (bandeau, panneau de direction, voies, « puis », bouton « replier ») → marge haute de la caméra.
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(RoadlySpacing.screenEdge).onGloballyPositioned { coords ->
                vm.setTopStackFraction(PanelGeometry.topFraction(coords.positionInRoot().y + coords.size.height, coords.findRootCoordinates().size.height.toFloat()))
            },
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            // Bandeau pleine largeur ; le bouton « replier » est dessous, à droite (référence visuelle).
            BannerStack(
                state, bannerExpanded, onOpenPreview = vm::openManeuverPreview,
                junction = junction?.let { slot -> { slot(Modifier.fillMaxWidth().height(junctionHeight)) } },
            )
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                    if (state.rerouting) ReroutingPill()
                    ScooterExtras(state, vm)
                }
                Spacer(Modifier.width(RoadlySpacing.s))
                val angle by animateFloatAsState(if (bannerExpanded) 0f else 180f, label = "bannerChevron")
                RoundIconButton(
                    Icons.Default.KeyboardArrowDown,
                    stringResource(if (bannerExpanded) R.string.nav_collapse_banner else R.string.nav_expand_banner),
                    onClick = onToggleBanner, size = 52.dp, iconTint = c.brand, iconModifier = Modifier.rotate(angle),
                )
            }
        }

        // Colonne d'actions à droite (signaler, voix, changer de destination, itinéraires, 2D/3D + Nord), posée 8 dp au-dessus du bloc
        // du bas mesuré : elle monte avec le panneau déplié. Sans la place, les doublons du panneau cèdent d'abord (`portraitActionButtons`).
        val blockDp by vm.bottomBlockHeightDp.collectAsStateWithLifecycle()
        val panelExpanded by vm.bottomPanelExpanded.collectAsStateWithLifecycle()
        val guiding = state.phase == NavigationPhase.GUIDING
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val available = portraitActionColumnAvailableDp(maxHeight.value.toInt(), statusTop.value.toInt(), blockDp)
        val buttons = portraitActionButtons(available, guiding, panelExpanded, voiceInPanel = guiding && state.following)
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = RoadlySpacing.screenEdge, bottom = blockDp.dp + RoadlySpacing.s),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
            horizontalAlignment = Alignment.End,
        ) {
            assistantButton()
            // Voix, changer de destination et itinéraires sont dans le panneau du bas déplié (référence : seul « Signaler » ici).
            for (button in buttons.filter { it == ActionButton.REPORT || it == ActionButton.CAMERA }) when (button) {
                ActionButton.REPORT -> ReportButton(onReport)
                ActionButton.VOICE -> RoundIconButton(
                    if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    stringResource(if (state.muted) R.string.nav_unmute else R.string.nav_mute),
                    onClick = vm::toggleMute,
                )
                ActionButton.SEARCH -> RoundIconButton(Icons.Default.Search, stringResource(R.string.nav_change_destination), onClick = onChangeDestination)
                ActionButton.ALTERNATIVES -> RoundIconButton(Icons.Default.AltRoute, stringResource(R.string.nav_alternatives), onClick = vm::loadAlternatives)
                ActionButton.CAMERA -> CameraModeButtons(vm)
            }
            if (state.scooter != null) ScooterActions(vm)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            assistantPanel()
            // Pastilles d'état au-dessus du compteur, jamais à côté : la rangée 2D/3D + Nord de la colonne d'actions est à sa hauteur.
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs), horizontalAlignment = Alignment.Start) {
                if (state.gpsLost) GpsLostPill()
                StatusPills(state, onZoneClick = vm::askHideZone)
            }
            BottomBlock(state, vm, onChangeDestination, speed = { SpeedBadge(state.speedKmh, state.speedLimitKmh, toleranceKmh = state.toleranceKmh) })
        }
    }
}

/** Panneau trottinette (voie, transitions, alertes, autonomie) et réponse de l'assistant vocal, sous le bandeau. */
@Composable
private fun ScooterExtras(state: NavigationUiState, vm: NavigationViewModel) {
    val scooter = state.scooter ?: return
    var batteryDialog by remember { mutableStateOf(false) }
    ScooterGuidancePanel(scooter, onBatteryClick = { batteryDialog = true })
    val reply by vm.assistantReply.collectAsStateWithLifecycle()
    reply?.let { AssistantReplyPill(it) }
    if (batteryDialog) BatteryDialog(scooter.range?.batteryNowPct?.roundToInt(), onDismiss = { batteryDialog = false }, onSave = { vm.setBattery(it); batteryDialog = false })
}

/** Actions trottinette : l'assistant vocal (voix), stationnement près de l'arrivée, recharge à proximité. */
@Composable
private fun ScooterActions(vm: NavigationViewModel) {
    val listen = rememberVoiceInput("Votre question", onText = vm::onAssistantText)
    RoundIconButton(Icons.Default.Mic, "Question vocale", onClick = listen)
    RoundIconButton(Icons.Default.LocalParking, "Stationnement trottinettes", onClick = { vm.searchPois(ScooterPoiKind.PARKING) })
    RoundIconButton(Icons.Default.EvStation, "Recharge", onClick = { vm.searchPois(ScooterPoiKind.CHARGING) })
}

/** Paysage : colonne gauche (bandeau, voies, Horizon, barre d'information) sur ~45 % ; compteur et actions à droite. */
@Composable
internal fun LandscapeOverlay(
    state: NavigationUiState,
    bannerExpanded: Boolean,
    onToggleBanner: () -> Unit,
    onReport: () -> Unit,
    onChangeDestination: () -> Unit,
    vm: NavigationViewModel,
    assistantButton: @Composable () -> Unit,
    assistantPanel: @Composable () -> Unit,
    junction: (@Composable (Modifier) -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Vue 3D de carrefour : en haut, entre la colonne gauche et la colonne d'actions (la voiture reste en bas à droite).
        junction?.invoke(
            Modifier.align(Alignment.TopStart)
                .padding(start = maxWidth * 0.46f, end = RoadlySpacing.touchTargetDriving + RoadlySpacing.screenEdge * 2, top = RoadlySpacing.screenEdge)
                .fillMaxWidth().height(maxHeight * JUNCTION_LANDSCAPE_FRACTION),
        )
        // Colonne gauche : bandeau en haut, Horizon + barre d'information calés en bas (pas de défilement : tout doit rester visible en conduite).
        BoxWithConstraints(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.46f).padding(RoadlySpacing.screenEdge)) {
            // Passe appareil : panneau déplié qui ne tient pas sous la pile du bandeau → replié (vérifié une fois les tailles stabilisées).
            val density = LocalDensity.current
            val panelExpanded by vm.bottomPanelExpanded.collectAsStateWithLifecycle()
            var stackPx by remember { mutableIntStateOf(0) }
            var blockPx by remember { mutableIntStateOf(0) }
            val columnDp = maxHeight.value.toInt()
            LaunchedEffect(panelExpanded, stackPx, blockPx, columnDp) {
                if (!panelExpanded || blockPx == 0) return@LaunchedEffect
                delay(PANEL_SETTLE_MS)
                val stackDp = with(density) { stackPx.toDp().value.toInt() }
                val blockDp = with(density) { blockPx.toDp().value.toInt() }
                if (!landscapePanelFits(columnDp, stackDp, blockDp)) vm.collapseBottomPanel()
            }
            Column(Modifier.align(Alignment.TopStart).onSizeChanged { stackPx = it.height }, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                BannerStack(state, bannerExpanded, onOpenPreview = vm::openManeuverPreview)
                if (state.rerouting) ReroutingPill()
                ScooterExtras(state, vm)
            }
            Column(Modifier.align(Alignment.BottomStart).onSizeChanged { blockPx = it.height }, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                assistantPanel()
                BottomBlock(state, vm, onChangeDestination)
            }
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
            horizontalAlignment = Alignment.End,
        ) {
            assistantButton()
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
            if (state.scooter != null) ScooterActions(vm)
        }
        // Compteur à droite, décalé pour ne pas chevaucher la colonne d'actions (56 dp + marges). La rangée ne garde que 2D/3D + Nord
        // et le compteur ; les pastilles d'état passent au-dessus, calées à droite, pour ne jamais pousser 2D/3D vers « Arrêter ».
        Column(
            Modifier.align(Alignment.BottomEnd).padding(start = RoadlySpacing.screenEdge, bottom = RoadlySpacing.screenEdge, end = RoadlySpacing.screenEdge + RoadlySpacing.touchTargetDriving + RoadlySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs),
            horizontalAlignment = Alignment.End,
        ) {
            if (state.gpsLost) GpsLostPill()
            StatusPills(state, onZoneClick = vm::askHideZone)
            Row(verticalAlignment = Alignment.Bottom) {
                CameraModeButtons(vm)
                Spacer(Modifier.width(RoadlySpacing.s))
                SpeedBadge(state.speedKmh, state.speedLimitKmh, toleranceKmh = state.toleranceKmh)
            }
        }
    }
}

/** Horizon + barre d'information (guidage), ou panneau d'arrivée / d'échec / de chargement. */
@Composable
private fun BottomBlock(state: NavigationUiState, vm: NavigationViewModel, onChangeDestination: () -> Unit, speed: (@Composable () -> Unit)? = null) {
    val c = LocalRoadlyColors.current
    val density = LocalDensity.current
    // D5 : haut du bloc (Horizon + panneau, ou panneau d'arrivée / d'échec / de chargement) mesuré → marge basse de la caméra
    // (portrait ; ignorée en paysage) et distance au bas de l'écran (dp) sous la colonne d'actions portrait.
    val measured = Modifier.fillMaxWidth().onGloballyPositioned { coords ->
        val rootHeight = coords.findRootCoordinates().size.height.toFloat()
        val top = coords.positionInRoot().y
        vm.setBottomPanelFraction(PanelGeometry.fraction(top, rootHeight))
        vm.setBottomBlockHeightDp(ceil((rootHeight - top) / density.density).toInt().coerceAtLeast(0))
    }
    when (state.phase) {
        NavigationPhase.GUIDING -> {
            val expanded by vm.bottomPanelExpanded.collectAsStateWithLifecycle()
            val destination by vm.destinationName.collectAsStateWithLifecycle()
            val recenterIn by vm.recenterInSeconds.collectAsStateWithLifecycle()
            // Retour système panneau déplié : le replier, pas arrêter le guidage (composé après le `BackHandler` de l'écran → prioritaire).
            BackHandler(enabled = expanded) { vm.collapseBottomPanel() }
            // Orientation lue ici (même critère largeur > hauteur que `NavigationContent`) : signatures et corps des overlays inchangés.
            val configuration = LocalConfiguration.current
            val landscape = configuration.screenWidthDp > configuration.screenHeightDp
            Column(measured) {
                // Compteur et Horizon sur la même ligne (portrait), comme la référence.
                Row(Modifier.fillMaxWidth().padding(bottom = RoadlySpacing.s), verticalAlignment = Alignment.Bottom) {
                    speed?.let { it(); Spacer(Modifier.width(RoadlySpacing.s)) }
                    AnimatedVisibility(horizonVisible(state, landscape, expanded), modifier = Modifier.weight(1f)) {
                        HorizonStrip(items = horizonItems(state), title = stringResource(R.string.nav_horizon_title))
                    }
                }
                recenterIn?.let { RecenterCountdownPill(it, onCancel = vm::cancelAutoRecenter) }
                NavigationBottomPanel(
                    state = state, expanded = expanded, destinationName = destination,
                    onToggleExpanded = vm::toggleBottomPanel, onToggleMute = vm::toggleMute, onRecenter = vm::recenter, onStop = vm::stop,
                    onTripDetails = vm::openTripDetails, onAlternatives = vm::loadAlternatives, compact = landscape,
                    onChangeDestination = onChangeDestination,
                    // Pas en trottinette : les stations-service ne la concernent pas.
                    onFuelAhead = if (vm.fuelAheadAvailable && !vm.scooterMode) vm::loadFuelAhead else null,
                )
            }
        }
        NavigationPhase.ARRIVED -> Box(measured) {
            if (vm.scooterMode) {
                val trip by vm.lastTrip.collectAsStateWithLifecycle()
                ScooterRecapPanel(trip, onArrivalBattery = vm::setArrivalBattery, onFinish = vm::stop)
            } else ArrivedPanel(state.freeFlowToll, onFinish = vm::stop)
        }
        NavigationPhase.FAILED -> Box(measured) { FailedPanel(state.error, onRetry = vm::retry, onStop = vm::stop) }
        NavigationPhase.LOADING, NavigationPhase.IDLE -> GlassPanel(measured) { LoadingRow(stringResource(R.string.nav_loading)) }
    }
}

/**
 * Ruban Horizon montré s'il a des éléments. En paysage, masqué tant que le panneau de direction (D3) ou les voies (D4) occupent
 * la colonne gauche : bandeau + panneau + voies (≈ 350 dp) et bloc du bas ne tiennent pas ensemble sur ≈ 409 dp.
 * Passe appareil : masqué aussi en paysage quand le panneau du bas est déplié ([panelExpanded]) ; masqué dans les deux orientations
 * pendant le panneau de direction, pour raccourcir le bloc du bas et laisser la flèche de manœuvre visible entre les blocs.
 */
internal fun horizonVisible(state: NavigationUiState, landscape: Boolean, panelExpanded: Boolean = false): Boolean {
    val hasItems = state.upcoming.isNotEmpty() || state.hazards.isNotEmpty() || state.communityAhead.isNotEmpty()
    return hasItems && bannerSign(state) == null && !(landscape && (state.showLanes || panelExpanded))
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
    // Feux : seul le prochain, à moins d'1 km (en ville, la liste entière noierait le ruban).
    val firstLight = state.hazards.firstOrNull { it.kind == HazardKind.TRAFFIC_LIGHT && it.distanceM <= TRAFFIC_LIGHT_HORIZON_M }
    val hazards = state.hazards.filter { it.kind != HazardKind.TRAFFIC_LIGHT || it === firstLight }.map { h ->
        when (h.kind) {
            HazardKind.TOLL_BOOTH -> {
                val what = stringResource(R.string.nav_horizon_toll)
                HorizonItem(HorizonKind.TOLL, h.distanceM, "${h.distanceText} · $what", "$what dans ${h.distanceText}")
            }
            HazardKind.TRAFFIC_LIGHT -> {
                val what = stringResource(R.string.nav_horizon_light)
                HorizonItem(HorizonKind.TRAFFIC_LIGHT, h.distanceM, "${h.distanceText} · $what", "$what dans ${h.distanceText}")
            }
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

/** Portée des feux tricolores dans l'Horizon (m). */
private const val TRAFFIC_LIGHT_HORIZON_M = 1_000.0

/** Zone de danger (Radar Intelligence), prochaine limitation, péage en flux libre et pluie, au-dessus du compteur. */
@Composable
private fun StatusPills(state: NavigationUiState, onZoneClick: () -> Unit = {}) {
    val c = LocalRoadlyColors.current
    // Radar Intelligence : zone de danger en cours (reste à parcourir) ou prochaine (compte à rebours), fiabilité des données.
    state.radar?.let { r ->
        DangerZonePill(
            stringResource(R.string.nav_zone_active),
            stringResource(if (r.inside) R.string.nav_danger_remaining else R.string.nav_danger_in, r.distance),
            r.limitKmh, r.confidence, r.inside,
            modifier = Modifier.clip(RoadlyShapes.Chip).clickable(onClickLabel = stringResource(R.string.nav_zone_false_alarm), onClick = onZoneClick),
        )
    }
    // 1.0 : limitation à venir sur le tracé (profil OSM via Valhalla), jamais devinée.
    state.nextLimit?.let { next ->
        GlassPanel(contentPadding = 8.dp, shape = RoadlyShapes.Chip) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedLimitSign(next.kmh, size = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_next_limit, next.distance), style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
            }
        }
    }
    // 1.0 : portique de péage en flux libre franchi : rappel discret, détaillé à l'arrivée.
    state.freeFlowToll?.let { toll ->
        GlassPanel(contentPadding = 8.dp, shape = RoadlyShapes.Chip) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Euro, style = IconTileStyle.Warning, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_free_flow_passed), style = MaterialTheme.typography.labelLarge, color = c.warning)
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
    val c = LocalRoadlyColors.current
    RoundIconButton(Icons.Default.Warning, stringResource(R.string.nav_report), onClick = onClick, size = 60.dp, iconTint = c.accent)
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
private fun ArrivedPanel(toll: FreeFlowTollUi?, onFinish: () -> Unit) {
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
            if (toll != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.Euro, style = IconTileStyle.Warning, size = 36.dp)
                    Spacer(Modifier.width(RoadlySpacing.sm))
                    Column {
                        Text(
                            pluralStringResource(R.plurals.nav_free_flow_count, toll.gantries, toll.gantries) + (toll.roads?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleSmall, color = c.warning,
                        )
                        Text(
                            toll.due?.let { stringResource(R.string.nav_free_flow_due, it) } ?: stringResource(R.string.nav_free_flow_check),
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
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
internal fun KeepScreenOn() {
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

/** Ce que la carte du guidage reflète ; comparé pour n'agir que sur un vrai changement (position, suivi, mode, économie, orientation). */
private data class MapTick(
    val route: Route?,
    val progress: RouteProgress?,
    val speedKmh: Double?,
    val approaching: Boolean,
    val following: Boolean,
    val mode: CameraMode3D,
    val powerSave: Boolean,
    val landscape: Boolean,
    /** D5 : part de l'écran sous le panneau du bas (quantifiée), null avant la première mesure. */
    val bottomPanel: Float?,
    /** Part de l'écran couverte par la pile du haut (portrait, quantifiée), null avant la première mesure. */
    val topPanel: Float?,
    /** E2 : décalage de zoom, inclinaison choisie, bâtiments 3D. */
    val zoomOffset: Int,
    val pitchPreset: PitchPreset,
    val buildings3d: Boolean,
    val junctionWide: Boolean = false,
)

/**
 * C3 : position, tronçon parcouru et caméra pilotés par un seul `snapshotFlow`, hors recomposition des overlays
 * (au lieu de `LaunchedEffect` relancés à chaque position). C1 : caméra lissée, transitions, recentrage adouci.
 */
@Composable
private fun GuidanceMapEffects(
    ui: State<NavigationUiState>,
    mode: State<CameraMode3D>,
    powerSave: State<Boolean>,
    landscape: Boolean,
    map: MapController,
    camera: NavigationCameraController,
    bottomPanel: State<Float?>,
    topPanel: State<Float?>,
    zoomOffset: State<Int>,
    pitchPreset: State<PitchPreset>,
    buildings3d: State<Boolean>,
) {
    // Rotation (l'activité gère `orientation|screenSize`) : pas de relance de l'effet, juste une image de recadrage ordinaire.
    val isLandscape = rememberUpdatedState(landscape)
    LaunchedEffect(map, camera) {
        var lastProgress: RouteProgress? = null
        var wasFollowing = true
        // `snapshotFlow` n'émet que si la valeur change (égalité) : pas besoin de `distinctUntilChanged`.
        snapshotFlow {
            ui.value.let { s ->
                MapTick(
                    s.route, s.progress, s.speedKmh, s.approach != null, s.following, mode.value, powerSave.value, isLandscape.value, bottomPanel.value, topPanel.value,
                    zoomOffset.value, pitchPreset.value, buildings3d.value, junctionWide(s.junction),
                )
            }
        }
            .collect { t ->
                val route = t.route ?: return@collect
                val progress = t.progress ?: return@collect
                // Premier tracé : image immédiate ; recalcul ou alternative : transition douce, cap et zoom gardés (règle du contrôleur).
                camera.onRouteChanged(route.id)
                if (progress !== lastProgress) {
                    lastProgress = progress
                    map.setUserLocation(LocationFix(progress.snapped, progress.bearingDeg, null, 5f, 0L))
                    map.setTraversed(route.geometry, progress.segmentIndex, progress.snapped)
                }
                if (!t.following) { wasFollowing = false; return@collect }
                if (!wasFollowing) { wasFollowing = true; camera.requestTransition() }
                val next = route.maneuvers.getOrNull(progress.nextManeuverIndex) ?: return@collect
                val frame = camera.update(
                    NavigationCameraInput(
                        point = progress.snapped, bearingDeg = progress.bearingDeg, speedKmh = t.speedKmh,
                        distanceToManeuverM = progress.distanceToNextManeuverM, maneuverType = next.type, approaching = t.approaching,
                        mode = t.mode, powerSave = t.powerSave, landscape = t.landscape, nowMs = SystemClock.elapsedRealtime(),
                        bottomPanelFraction = t.bottomPanel, topPanelFraction = t.topPanel,
                        zoomOffset = t.zoomOffset, pitchPreset = t.pitchPreset, buildings3d = t.buildings3d, junctionWide = t.junctionWide,
                    ),
                )
                map.applyGuidanceCamera(frame.point, frame.camera, frame.durationMs, frame.eased)
            }
    }
}

/** Boutons 2D / 3D et « nord en haut » (C1), côte à côte pour ne prendre qu'une rangée. */
@Composable
private fun CameraModeButtons(vm: NavigationViewModel) {
    val mode by vm.cameraMode.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
        val is3d = mode == CameraMode3D.FOLLOW_3D
        RoundIconButton(
            if (is3d) Icons.Default.Map else Icons.Default.ViewInAr,
            stringResource(if (is3d) R.string.nav_view_2d else R.string.nav_view_3d),
            onClick = vm::toggleCameraMode,
        )
        val northUp = mode == CameraMode3D.NORTH_UP
        RoundIconButton(
            if (northUp) Icons.Default.Navigation else Icons.Default.Explore,
            stringResource(if (northUp) R.string.nav_heading_up else R.string.nav_north_up),
            onClick = vm::toggleNorthUp,
            style = if (northUp) RoundButtonStyle.Brand else RoundButtonStyle.Glass,
        )
    }
}

/** « Recentrage dans N s · Annuler » au-dessus de la barre d'information, pendant la reprise automatique (C1). */
@Composable
private fun RecenterCountdownPill(seconds: Int, onCancel: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.padding(bottom = RoadlySpacing.s), shape = RoadlyShapes.Chip, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_recenter_in, seconds), style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassButton(stringResource(R.string.nav_recenter_cancel), onClick = onCancel, style = GlassButtonStyle.Ghost)
        }
    }
}

/** D1 / D5 : feuilles ouvertes depuis le bandeau ou le panneau du bas ; le contenu suit l'état en direct, la carte continue derrière. */
@Composable
internal fun GuidanceSheets(state: NavigationUiState, vm: NavigationViewModel) {
    val sheet by vm.guidanceSheet.collectAsStateWithLifecycle()
    val destination by vm.destinationName.collectAsStateWithLifecycle()
    // Fin du guidage (arrivée, échec, arrêt) : la feuille est refermée dans le ViewModel, pas seulement masquée, pour ne pas rouvrir au guidage suivant ;
    // le panneau du bas est replié pour la même raison.
    LaunchedEffect(state.phase) { if (closesGuidanceSheet(state.phase)) { vm.closeSheet(); vm.collapseBottomPanel() } }
    // Début d'une approche (deux orientations) : le panneau du bas se replie pour laisser la place au panneau de direction et aux voies.
    val approaching = state.approach != null
    LaunchedEffect(approaching) { if (approaching) vm.collapseBottomPanel() }
    // Manœuvre visée franchie : l'aperçu (modal, couvre compteur, Horizon et Arrêter) se referme ; le trajet complet reste ouvert.
    val nextIndex = state.progress?.nextManeuverIndex
    var lastIndex by remember { mutableStateOf(nextIndex) }
    LaunchedEffect(nextIndex) {
        if (closesPreviewOnIndexChange(lastIndex, nextIndex, visibleSheet(state.phase, sheet))) vm.closeSheet()
        if (nextIndex != null) lastIndex = nextIndex
    }
    when (visibleSheet(state.phase, sheet)) {
        GuidanceSheet.MANEUVER_PREVIEW -> ManeuverPreviewSheet(state, onDismiss = vm::closeSheet)
        GuidanceSheet.TRIP_DETAILS -> TripDetailsSheet(state, destination, onDismiss = vm::closeSheet)
        GuidanceSheet.NONE -> Unit
    }
}

/** Feuille affichée : aucune hors guidage (chargement, arrivée, échec, arrêt). */
internal fun visibleSheet(phase: NavigationPhase, sheet: GuidanceSheet): GuidanceSheet = if (phase == NavigationPhase.GUIDING) sheet else GuidanceSheet.NONE

/** La feuille ouverte est refermée quand le guidage s'arrête ; pas pendant le chargement, qui précède le premier état. */
internal fun closesGuidanceSheet(phase: NavigationPhase): Boolean =
    phase == NavigationPhase.ARRIVED || phase == NavigationPhase.FAILED || phase == NavigationPhase.IDLE

/** D1 : l'aperçu se referme quand la manœuvre visée change ([prev] → [next], tous deux connus) ; jamais le trajet complet. */
internal fun closesPreviewOnIndexChange(prev: Int?, next: Int?, sheet: GuidanceSheet): Boolean =
    sheet == GuidanceSheet.MANEUVER_PREVIEW && prev != null && next != null && prev != next

/** Taille du bouton « replier le bandeau » des overlays (`size = 44.dp`) : l'attribution se place dessous en portrait, à sa gauche en paysage. */
private val BANNER_TOGGLE_SIZE = 44.dp

/** Paysage : délai sans changement de taille avant de vérifier que le panneau déplié tient (fin des animations du panneau et de l'Horizon). */
private const val PANEL_SETTLE_MS = 400L

/** Part de la hauteur (portrait) occupée par la vue 3D de carrefour sous le bandeau. */
internal const val JUNCTION_PORTRAIT_FRACTION = 0.34f
/** Paysage : part de la hauteur de la vue 3D, en haut entre les deux colonnes. */
internal const val JUNCTION_LANDSCAPE_FRACTION = 0.62f

/**
 * Carte plus large pendant la préparation et l'affichage d'un échangeur, d'une bifurcation ou d'un carrefour complexe, jusqu'à la
 * manœuvre ; retour progressif ensuite (paliers de zoom de la caméra).
 */
internal fun junctionWide(j: JunctionGuidance?): Boolean =
    j != null && j.phase < JunctionPhase.MANEUVER && (j.kind == JunctionKind.INTERCHANGE || j.kind == JunctionKind.MOTORWAY_FORK || j.complexity >= 2)
