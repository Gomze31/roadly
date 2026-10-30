package fr.roadly.feature.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
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
import fr.roadly.core.designsystem.component.RouteCard
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
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
    vm: RouteViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    val density = LocalDensity.current
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex()) }
    val phase = state.phase
    LaunchedEffect(phase) {
        when (phase) {
            is RouteUiState.Content -> {
                map.setRoutes(phase.options.map { it.route }, phase.activeId)
                map.setMarkers(listOf(Place("dest", phase.destinationName, null, vm.destinationPoint, PlaceKind.ADDRESS, "local")))
                map.fitBounds(phase.options.flatMap { it.route.geometry }, paddingPx = with(density) { 96.dp.roundToPx() })
            }
            // Position inconnue : on montre la destination pour que l'utilisateur sache où appuyer longuement.
            is RouteUiState.NoOrigin -> {
                map.setMarkers(listOf(Place("dest", phase.destinationName, null, vm.destinationPoint, PlaceKind.ADDRESS, "local")))
                map.moveTo(vm.destinationPoint, 12.0)
            }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
        // La carte reste affichée dans tous les états (appui long = choix de l'origine).
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onRouteClick = vm::selectRoute, onMapLongClick = { vm.setOrigin(it) })
        }
        Row(Modifier.statusBarsPadding().padding(RoadlySpacing.m), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.route_back), onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassPanel(Modifier.weight(1f), contentPadding = 12.dp) {
                Text(stringResource(R.string.route_title, (phase as? RouteUiState.Content)?.destinationName ?: (phase as? RouteUiState.NoOrigin)?.destinationName ?: ""), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.m)) {
            when (val s = phase) {
                RouteUiState.Loading -> GlassPanel(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = c.accent, modifier = Modifier.width(24.dp).height(24.dp)); Spacer(Modifier.width(RoadlySpacing.m)); Text(stringResource(R.string.route_loading), color = c.textSecondary) } }
                is RouteUiState.NoOrigin -> GlassPanel(Modifier.fillMaxWidth()) { Column { Text(stringResource(R.string.route_no_origin_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary); Text(stringResource(R.string.route_no_origin_body), color = c.textSecondary) } }
                is RouteUiState.Error -> GlassPanel(Modifier.fillMaxWidth()) { ErrorState(s.error.userMessage(), onRetry = vm::retry) }
                is RouteUiState.Content -> {
                    Row {
                        GlassChip(
                            text = if (s.departAtEpochMs - System.currentTimeMillis() < 60_000) stringResource(R.string.route_depart_now) else stringResource(R.string.route_depart_at, Formatters.clockTime(s.departAtEpochMs, ZoneId.systemDefault())),
                            onClick = { showTimePicker = true }, icon = Icons.Default.Schedule,
                        )
                        if (!s.hasVehicleProfile) { Spacer(Modifier.width(RoadlySpacing.s)); GlassChip(stringResource(R.string.route_vehicle_cta), onClick = onOpenSettings) }
                    }
                    Spacer(Modifier.height(RoadlySpacing.s))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(s.options, key = { it.route.id }) { o ->
                            RouteCard(
                                title = o.label,
                                duration = Formatters.duration(o.route.durationSeconds),
                                distance = Formatters.distance(o.route.distanceMeters),
                                arrival = Formatters.clockTime(o.arrivalEpochMs, ZoneId.systemDefault()),
                                // Pas de profil véhicule : null, le composant affiche l'invite à le renseigner.
                                costLine = o.cost?.let { stringResource(R.string.route_cost_line, Formatters.euros(it.fuelEur), Formatters.euros(it.perKmEur)) },
                                tollLine = stringResource(if (o.route.hasToll) R.string.route_toll_yes else R.string.route_toll_no),
                                selected = o.route.id == s.activeId,
                                onClick = { vm.selectRoute(o.route.id) },
                                note = if (o.sameAsFastest) stringResource(R.string.route_same_as_fastest) else null,
                            )
                            Spacer(Modifier.height(RoadlySpacing.s))
                        }
                    }
                    // L'hôte (app) demande d'abord POST_NOTIFICATIONS (API 33+), puis appelle begin() et ouvre l'écran de guidage.
                    GlassButton(
                        stringResource(R.string.route_start),
                        onClick = { onStartNavigation(vm::startGuidance) },
                        icon = Icons.Default.Navigation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val tp = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
        // TimePickerDialog absent du Material 3 du BOM : AlertDialog + TimePicker.
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { GlassButton(stringResource(R.string.route_time_confirm), onClick = {
                var target = now.withHour(tp.hour).withMinute(tp.minute).withSecond(0)
                if (target.isBefore(now)) target = target.plusDays(1)
                vm.setDepartAt(target.toInstant().toEpochMilli()); showTimePicker = false
            }) },
            dismissButton = { GlassButton(stringResource(R.string.route_time_now), onClick = { vm.setDepartAt(System.currentTimeMillis()); showTimePicker = false }, style = GlassButtonStyle.Secondary) },
            text = { TimePicker(state = tp) },
        )
    }
}

private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
