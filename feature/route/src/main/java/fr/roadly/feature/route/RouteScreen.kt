package fr.roadly.feature.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import fr.roadly.core.map.RouteLabelPlacement
import fr.roadly.core.data.traffic.asReport
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RouteHeadline
import fr.roadly.core.designsystem.component.RouteOptionRow
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.OfflineMapDownloader
import fr.roadly.core.scooter.map.ScooterRouteFeatures
import fr.roadly.core.designsystem.component.TravelModeSelector
import fr.roadly.core.designsystem.component.rememberVoiceInput
import androidx.compose.ui.platform.LocalContext
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.routing.RouteOption
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartNavigation: (begin: () -> Boolean) -> Unit,
    onAddStop: () -> Unit,
    onSwap: (RouteRoute) -> Unit = {},
    onSearchFuel: () -> Unit = {},
    vm: RouteViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    val density = LocalDensity.current
    var showTimePicker by remember { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var zfeShown by remember { mutableStateOf<fr.roadly.core.data.zfe.ZfeCrossing?>(null) }
    var trafficShown by remember { mutableStateOf<List<fr.roadly.core.data.traffic.TrafficHit>?>(null) }
    var panelCollapsed by rememberSaveable { mutableStateOf(false) }
    val myPosition = stringResource(R.string.route_from_my_position)
    val context = LocalContext.current
    val offlineDownloader = remember { OfflineMapDownloader(context.applicationContext) }
    val offline by offlineDownloader.state.collectAsStateWithLifecycle()
    // l'assistant vocal : reconnaissance vocale du système, réponse calculée sur les données du moteur.
    val listen = rememberVoiceInput("Votre question", onText = vm::onAssistantText)

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive, c.routeCasing) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex(), c.routeCasing.toHex()) }
    val phase = state.phase

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // Incidents officiels de l'itinéraire choisi sur la carte (icônes des signalements).
        LaunchedEffect((phase as? RouteUiState.Content)?.traffic, (phase as? RouteUiState.Content)?.activeId) {
            val content = phase as? RouteUiState.Content
            val now = System.currentTimeMillis()
            map.setReports(content?.traffic?.get(content.activeId).orEmpty().map { it.asReport(now) })
        }
        // Périmètres des ZFE traversées (une seule fois par agglomération, couleur selon le verdict le plus strict).
        LaunchedEffect((phase as? RouteUiState.Content)?.zfe) {
            map.setZfeZones(fr.roadly.core.map.ZfeFeatures.build(zfeShapes((phase as? RouteUiState.Content)?.zfe.orEmpty())))
        }
        LaunchedEffect(phase, landscape, panelCollapsed) {
            when (phase) {
                is RouteUiState.Content -> {
                    map.setRoutes(phase.options.map { it.route }, phase.activeId)
                    // Trottinette : tracé actif coloré par type de voie (piste, bande, voie verte, route partagée, vigilance).
                    val active = phase.options.firstOrNull { it.route.id == phase.activeId }
                    map.setScooterRoute(if (active?.scooter != null) ScooterRouteFeatures.build(active.route.geometry, active.scooter?.analysis) else ScooterRouteFeatures.EMPTY)
                    map.setMarkers(listOf(Place("dest", phase.destinationName, null, vm.destinationPoint, PlaceKind.ADDRESS, "local")))
                    // Marges : en-tête en haut, panneau « Votre choix » en bas (portrait) ou à gauche (paysage).
                    with(density) {
                        if (landscape) map.fitBounds(phase.options.flatMap { it.route.geometry }, (maxWidth * 0.5f).roundToPx(), 48.dp.roundToPx(), 48.dp.roundToPx(), 48.dp.roundToPx())
                        else map.fitBounds(phase.options.flatMap { it.route.geometry }, 80.dp.roundToPx(), 190.dp.roundToPx(), 80.dp.roundToPx(), (if (panelCollapsed) 140 else 470).dp.roundToPx())
                    }
                }
                // Position inconnue : on montre la destination pour que l'utilisateur sache où appuyer longuement.
                is RouteUiState.NoOrigin -> {
                    map.setMarkers(listOf(Place("dest", phase.destinationName, null, vm.destinationPoint, PlaceKind.ADDRESS, "local")))
                    map.moveTo(vm.destinationPoint, 12.0)
                }
                else -> Unit
            }
        }

        // La carte reste affichée dans tous les états (appui long = choix de l'origine).
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onRouteClick = vm::selectRoute, onMapLongClick = { vm.setOrigin(it) })
        }

        // Étiquettes « CHOISIR » sur les tracés alternatifs (au point où chacun s'écarte le plus des autres).
        val content = phase as? RouteUiState.Content
        if (content != null && content.options.size > 1) {
            val labels = remember(content.options, content.activeId) {
                content.options.filter { it.route.id != content.activeId }.mapNotNull { o ->
                    RouteLabelPlacement.labelPoint(o.route.geometry, content.options.filter { it !== o }.map { it.route.geometry })?.let { o.route.id to it }
                }
            }
            val tick by map.cameraTick.collectAsStateWithLifecycle()
            labels.forEach { (id, point) ->
                val pt = remember(tick, point) { map.toScreen(point) } ?: return@forEach
                ChooseLabel(
                    onClick = { vm.selectRoute(id) },
                    modifier = Modifier.offset { IntOffset(pt.x.roundToInt(), pt.y.roundToInt()) }
                        .graphicsLayer { translationX = -size.width / 2f; translationY = -size.height / 2f },
                )
            }
        }

        val destinationName = (phase as? RouteUiState.Content)?.destinationName ?: (phase as? RouteUiState.NoOrigin)?.destinationName ?: ""
        val originLabel = vm.originName?.ifBlank { null } ?: myPosition
        val onSwapClick: (() -> Unit)? = { vm.swapped(myPosition)?.let(onSwap) }
        val header: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                RouteTopBar(destinationName, favorite, vm::toggleFavorite, onPickTime = { showTimePicker = true }, onClose = onBack)
                RouteFromBar(originLabel, onSwapClick)
                if (state.scooterAvailable) TravelModeSelector(state.travelMode, vm::setTravelMode)
                state.assistantReply?.let { AssistantBubble(it) }
            }
        }
        val panelModifier = if (landscape) {
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.48f).statusBarsPadding().navigationBarsPadding().padding(RoadlySpacing.screenEdge).verticalScroll(rememberScrollState())
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge)
        }

        if (!landscape) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(RoadlySpacing.screenEdge)) {
                header()
                // Boutons latéraux : stations à gauche, repli du panneau à droite.
                Row(Modifier.fillMaxWidth().padding(top = RoadlySpacing.sm), verticalAlignment = Alignment.Top) {
                    SideRoundButton(Icons.Default.LocalGasStation, stringResource(R.string.route_fuel), tint = c.accent, onClick = onSearchFuel)
                    Spacer(Modifier.weight(1f))
                    if (content != null && content.scooter == null) {
                        val angle by animateFloatAsState(if (panelCollapsed) 180f else 0f, label = "chevron")
                        SideRoundButton(
                            Icons.Default.KeyboardArrowDown, stringResource(if (panelCollapsed) R.string.route_expand else R.string.route_collapse),
                            tint = c.brand, onClick = { panelCollapsed = !panelCollapsed }, iconModifier = Modifier.rotate(angle),
                        )
                    }
                }
            }
        }

        Column(panelModifier, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
            if (landscape) header()
            if (landscape) Spacer(Modifier.weight(1f))
            when (val s = phase) {
                RouteUiState.Loading -> GlassPanel(Modifier.fillMaxWidth()) { LoadingRow(stringResource(R.string.route_loading)) }
                is RouteUiState.NoOrigin -> GlassPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Default.MyLocation, style = IconTileStyle.Warning, size = 44.dp)
                        Spacer(Modifier.width(RoadlySpacing.sm))
                        Column {
                            Text(stringResource(R.string.route_no_origin_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Text(stringResource(R.string.route_no_origin_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
                is RouteUiState.Error -> GlassPanel(Modifier.fillMaxWidth()) { ErrorState(s.error.userMessage(), onRetry = vm::retry) }
                is RouteUiState.Content -> if (s.scooter != null) ScooterChoicePanel(
                    s, offline, onSelect = vm::selectRoute, onPreferences = vm::setScooterPreferences, onBattery = vm::setBattery,
                    onDownload = {
                        val active = s.options.firstOrNull { it.route.id == s.activeId } ?: s.options.first()
                        offlineDownloader.download(state.styleUrl, active.route.geometry, "Roadly · ${s.destinationName}")
                    },
                    onAssistant = listen, onStart = { onStartNavigation(vm::startGuidance) },
                ) else {
                    AnimatedVisibility(!panelCollapsed) {
                        RouteChoicePanel(
                            s, detailsOpen, onToggleDetails = { detailsOpen = !detailsOpen },
                            onSelect = vm::selectRoute, onPickTime = { showTimePicker = true }, onOpenSettings = onOpenSettings,
                            onPreferences = vm::setPreferences,
                            onAddStop = onAddStop, onMoveStop = vm::moveStop, onRemoveStop = vm::removeStop, onOptimize = vm::optimizeStops,
                            onZfe = { zfeShown = it },
                            onTraffic = { trafficShown = it },
                        )
                    }
                    RollButton(onClick = { onStartNavigation(vm::startGuidance) })
                }
            }
        }
    }

    zfeShown?.let { z -> ZfeDialog(z, onOpenVehicle = onOpenSettings, onDismiss = { zfeShown = null }) }
    trafficShown?.let { hits -> TrafficDialog(hits, onDismiss = { trafficShown = null }) }

    if (showTimePicker) {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val tp = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
        val content = phase as? RouteUiState.Content
        var arriveMode by remember { mutableStateOf(content?.arriveAtEpochMs != null) }
        // TimePickerDialog absent du Material 3 du BOM : AlertDialog + TimePicker ; « Partir à » ou « Arriver à ».
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = c.surfaceElevated,
            confirmButton = { GlassButton(stringResource(R.string.route_time_confirm), onClick = {
                var target = now.withHour(tp.hour).withMinute(tp.minute).withSecond(0)
                if (target.isBefore(now)) target = target.plusDays(1)
                val ms = target.toInstant().toEpochMilli()
                if (arriveMode) vm.setArriveAt(ms) else vm.setDepartAt(ms)
                showTimePicker = false
            }) },
            dismissButton = { GlassButton(stringResource(R.string.route_time_now), onClick = { vm.setDepartAt(System.currentTimeMillis()); showTimePicker = false }, style = GlassButtonStyle.Secondary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                    SegmentedControl(listOf(false, true), arriveMode, { arriveMode = it }, { if (it) arriveLabel else departLabel })
                    if (arriveMode) Text(stringResource(R.string.route_arrive_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    TimePicker(state = tp)
                }
            },
        )
    }
}

/** Réponse de l'assistant vocal (texte de ce qui a été prononcé). */
@Composable
private fun AssistantBubble(text: String) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = RoadlySpacing.sm) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
    }
}

private const val departLabel = "Partir à"
private const val arriveLabel = "Arriver à"

private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
