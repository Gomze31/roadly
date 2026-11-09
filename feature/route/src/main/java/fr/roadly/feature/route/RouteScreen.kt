package fr.roadly.feature.route

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
    vm: RouteViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    val density = LocalDensity.current
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val offlineDownloader = remember { OfflineMapDownloader(context.applicationContext) }
    val offline by offlineDownloader.state.collectAsStateWithLifecycle()
    // Roadly AI : reconnaissance vocale du système, réponse calculée sur les données du moteur.
    val listen = rememberVoiceInput("Roadly AI — votre demande", onText = vm::onAssistantText)

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive, c.routeCasing) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex(), c.routeCasing.toHex()) }
    val phase = state.phase

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        LaunchedEffect(phase, landscape) {
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
                        else map.fitBounds(phase.options.flatMap { it.route.geometry }, 40.dp.roundToPx(), 150.dp.roundToPx(), 40.dp.roundToPx(), 340.dp.roundToPx())
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

        val destinationName = (phase as? RouteUiState.Content)?.destinationName ?: (phase as? RouteUiState.NoOrigin)?.destinationName ?: ""
        val panelModifier = if (landscape) {
            Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.48f).statusBarsPadding().navigationBarsPadding().padding(RoadlySpacing.screenEdge).verticalScroll(rememberScrollState())
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge)
        }

        if (!landscape) {
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(RoadlySpacing.screenEdge), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                TripHeader(destinationName, onBack, favorite, vm::toggleFavorite, onPickTime = { showTimePicker = true })
                if (state.scooterAvailable) TravelModeSelector(state.travelMode, vm::setTravelMode)
                state.assistantReply?.let { AssistantBubble(it) }
            }
        }

        Column(panelModifier, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            if (landscape) TripHeader(destinationName, onBack, favorite, vm::toggleFavorite, onPickTime = { showTimePicker = true })
            if (landscape && state.scooterAvailable) TravelModeSelector(state.travelMode, vm::setTravelMode)
            if (landscape) state.assistantReply?.let { AssistantBubble(it) }
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
                ) else ChoicePanel(
                    s, onSelect = vm::selectRoute, onPickTime = { showTimePicker = true }, onOpenSettings = onOpenSettings,
                    onStart = { onStartNavigation(vm::startGuidance) }, onPreferences = vm::setPreferences,
                    onAddStop = onAddStop, onMoveStop = vm::moveStop, onRemoveStop = vm::removeStop, onOptimize = vm::optimizeStops,
                )
            }
        }
    }

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

/** Réponse de Roadly AI (texte de ce qui a été prononcé). */
@Composable
private fun AssistantBubble(text: String) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = RoadlySpacing.sm) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
    }
}

private const val departLabel = "Partir à"
private const val arriveLabel = "Arriver à"

/** En-tête : retour, destination (tuile + nom), ligne « Partir de · Ma position ». */
@Composable
private fun TripHeader(
    destinationName: String,
    onBack: () -> Unit,
    favorite: Boolean?,
    onToggleFavorite: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.route_back), onClick = onBack)
        Spacer(Modifier.width(RoadlySpacing.s))
        GlassPanel(Modifier.weight(1f), contentPadding = 0.dp) {
            Column {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.Place, style = IconTileStyle.BrandFilled, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(destinationName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // Étoile (favori) et horloge (partir à / arriver à), comme l'en-tête de l'écran d'itinéraires de référence.
                    if (favorite != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(if (favorite) R.string.route_favorite_remove else R.string.route_favorite_add),
                                tint = if (favorite) c.warning else c.textSecondary,
                            )
                        }
                    }
                    IconButton(onClick = onPickTime) {
                        Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.route_pick_time), tint = c.textSecondary)
                    }
                }
                RowDivider(inset = 0.dp)
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.MyLocation, style = IconTileStyle.Neutral, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        OverlineText(stringResource(R.string.route_from), color = c.textTertiary, dash = false)
                        Text(stringResource(R.string.route_from_my_position), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

/** Panneau « Votre choix » : durée en grand, arrivée, distance, puces d'information, alternatives, arrêt, « Rouler ». */
@Composable
private fun ChoicePanel(
    s: RouteUiState.Content,
    onSelect: (String) -> Unit,
    onPickTime: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onPreferences: (RoutePreferences) -> Unit,
    onAddStop: () -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onRemoveStop: (Int) -> Unit,
    onOptimize: () -> Unit,
) {
    val c = LocalRoadlyColors.current
    val active = s.options.firstOrNull { it.route.id == s.activeId } ?: s.options.first()
    val zone = ZoneId.systemDefault()
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.padding(RoadlySpacing.m)) {
            OverlineText(stringResource(R.string.route_your_choice))
            Spacer(Modifier.height(RoadlySpacing.s))
            RouteHeadline(
                duration = Formatters.duration(active.route.durationSeconds),
                arrival = Formatters.clockTime(active.arrivalEpochMs, zone), arrivalLabel = stringResource(R.string.route_arrival_label),
                distance = Formatters.distance(active.route.distanceMeters), distanceLabel = stringResource(R.string.route_distance_label),
                chips = { InfoChips(s, active, onPickTime, onOpenSettings) },
            )
            s.identities[active.route.id]?.let { identity ->
                Spacer(Modifier.height(RoadlySpacing.s))
                RouteIdentityCard(identity, s.dangerZones[active.route.id])
            }
            Spacer(Modifier.height(RoadlySpacing.s))
            // Options d'itinéraire : éviter péages / autoroutes / ferries, appliquées aux trois variantes et au recalcul.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                val p = s.preferences
                GlassChip(stringResource(R.string.route_opt_no_tolls), onClick = { onPreferences(p.copy(avoidTolls = !p.avoidTolls)) }, icon = Icons.Default.Toll, compact = true, selected = p.avoidTolls)
                GlassChip(stringResource(R.string.route_opt_no_highways), onClick = { onPreferences(p.copy(avoidHighways = !p.avoidHighways)) }, icon = Icons.Default.AltRoute, compact = true, selected = p.avoidHighways)
                GlassChip(stringResource(R.string.route_opt_no_tunnels), onClick = { onPreferences(p.copy(avoidTunnels = !p.avoidTunnels)) }, icon = Icons.Default.Subway, compact = true, selected = p.avoidTunnels)
                GlassChip(stringResource(R.string.route_opt_no_ferries), onClick = { onPreferences(p.copy(avoidFerries = !p.avoidFerries)) }, icon = Icons.Default.DirectionsBoat, compact = true, selected = p.avoidFerries)
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            RowDivider(inset = 0.dp)
            Spacer(Modifier.height(RoadlySpacing.xs))
            s.options.forEach { o ->
                RouteOptionRow(
                    duration = Formatters.duration(o.route.durationSeconds),
                    title = o.label,
                    distance = Formatters.distance(o.route.distanceMeters),
                    selected = o.route.id == s.activeId,
                    onClick = { onSelect(o.route.id) },
                    tag = if (o.route.hasToll) ({ Text(stringResource(R.string.route_toll_short), style = MaterialTheme.typography.labelSmall, color = c.textTertiary) }) else null,
                    note = listOfNotNull(
                        if (o.sameAsFastest) stringResource(R.string.route_same_as_fastest) else null,
                        s.dangerZones[o.route.id]?.let { n -> pluralStringResource(R.plurals.route_danger_zones, n, n) },
                    ).joinToString(" · ").ifEmpty { null },
                )
            }
            Spacer(Modifier.height(RoadlySpacing.xs))
            RowDivider(inset = 0.dp)
            // Arrêts intermédiaires (≤ 10), réordonnables, optimisables (mode tournée).
            StopsList(s.stops, s.optimizing, onAdd = onAddStop, onMove = onMoveStop, onRemove = onRemoveStop, onOptimize = onOptimize, modifier = Modifier.padding(vertical = RoadlySpacing.s))
            GlassButton(stringResource(R.string.route_start), onClick = onStart, style = GlassButtonStyle.Brand, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Puces d'information : heure de départ, péage (présence seulement), coût carburant ou invitation à renseigner le véhicule. */
@Composable
private fun InfoChips(s: RouteUiState.Content, active: RouteOption, onPickTime: () -> Unit, onOpenSettings: () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        val zone = ZoneId.systemDefault()
        val arrive = s.arriveAtEpochMs
        GlassChip(
            text = when {
                arrive != null -> stringResource(R.string.route_arrive_at, Formatters.clockTime(arrive, zone), Formatters.clockTime(s.departAtEpochMs, zone))
                s.departAtEpochMs - System.currentTimeMillis() < 60_000 -> stringResource(R.string.route_depart_now)
                else -> stringResource(R.string.route_depart_at, Formatters.clockTime(s.departAtEpochMs, zone))
            },
            onClick = onPickTime, icon = Icons.Default.Schedule, compact = true, selected = arrive != null,
        )
        GlassChip(stringResource(if (active.route.hasToll) R.string.route_toll_yes else R.string.route_toll_no), onClick = {}, icon = Icons.Default.Toll, compact = true, enabled = false)
        val cost = active.cost
        if (cost != null) {
            GlassChip(stringResource(R.string.route_cost_chip, Formatters.euros(cost.fuelEur)), onClick = onOpenSettings, icon = Icons.Default.LocalGasStation, compact = true)
        } else {
            GlassChip(stringResource(R.string.route_vehicle_cta), onClick = onOpenSettings, icon = Icons.Default.DirectionsCar, compact = true)
        }
    }
}

private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
