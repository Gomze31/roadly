package fr.roadly.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.StatTile
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import java.util.Locale

/** Replay : tracé enregistré sur la carte, marqueur animé, timeline, vitesse de lecture ×1 à ×8, « Y retourner ». */
@Composable
fun ReplayScreen(isDark: Boolean, onBack: () -> Unit, onNavigateTo: (Place) -> Unit, vm: ReplayViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    val density = LocalDensity.current
    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive, c.routeCasing) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex(), c.routeCasing.toHex()) }
    val trip = state.trip
    LaunchedEffect(trip?.id) {
        if (trip != null && trip.points.size >= 2) {
            // Le tracé enregistré est présenté comme un itinéraire (couche existante), sans manœuvres.
            val geometry = trip.points.map { it.point }
            val route = Route("replay-${trip.id}", RouteCosting.FASTEST, trip.distanceM, trip.durationS.toDouble(), geometry, listOf(Maneuver(ManeuverType.DEPART, "", null, trip.distanceM, trip.durationS.toDouble(), 0, null)), false, "local")
            map.setRoutes(listOf(route), route.id)
            map.setMarkers(listOf(Place("replay-end", trip.destinationName, null, geometry.last(), PlaceKind.POI, "local")))
            with(density) { map.fitBounds(geometry, 40.dp.roundToPx(), 140.dp.roundToPx(), 40.dp.roundToPx(), 300.dp.roundToPx()) }
        }
    }
    val frame = state.frame
    // Relecture : positions rapprochées, le marqueur suit chaque image sans glisser.
    LaunchedEffect(frame) { if (frame != null) map.setUserLocation(LocationFix(frame.point, frame.bearingDeg, null, 5f, 0L), animateMs = 0L) }

    Box(Modifier.fillMaxSize()) {
        if (state.styleUrl.isNotEmpty()) RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize())
        Row(Modifier.statusBarsPadding().padding(RoadlySpacing.screenEdge), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.replay_back), onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassPanel(Modifier.weight(1f), contentPadding = 12.dp) {
                Column {
                    OverlineText(stringResource(R.string.replay_overline))
                    Text(trip?.destinationName ?: "", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge)) {
            GlassPanel(Modifier.fillMaxWidth()) {
                if (trip == null) { LoadingRow(stringResource(R.string.replay_loading)); return@GlassPanel }
                val total = trip.points.lastOrNull()?.tMs ?: 0L
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(clock(state.elapsedMs), stringResource(R.string.replay_elapsed))
                        StatTile(frame?.speedKmh?.let { String.format(Locale.FRANCE, "%.0f", it) } ?: "—", stringResource(R.string.replay_speed))
                        StatTile(Formatters.distance(trip.distanceM), stringResource(R.string.replay_distance), align = Alignment.End)
                    }
                    Slider(
                        value = state.elapsedMs.toFloat(), onValueChange = { vm.seek(it.toLong()) }, valueRange = 0f..total.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = c.brand, activeTrackColor = c.brand, inactiveTrackColor = c.glassBorder),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        RoundIconButton(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (state.playing) R.string.replay_pause else R.string.replay_play), onClick = vm::togglePlay, style = RoundButtonStyle.Brand)
                        listOf(1, 2, 4, 8).forEach { x -> GlassChip(stringResource(R.string.replay_rate, x), onClick = { vm.setSpeed(x) }, selected = state.speed == x, compact = true) }
                    }
                    Text(stringResource(R.string.replay_summary, Formatters.duration(trip.durationS.toDouble()), Formatters.distance(trip.distanceM), trip.maxSpeedKmh.toInt()), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    GlassButton(
                        stringResource(R.string.replay_return), icon = Icons.Default.Navigation, style = GlassButtonStyle.Primary, modifier = Modifier.fillMaxWidth(),
                        onClick = { trip.points.lastOrNull()?.let { onNavigateTo(Place("replay-${trip.id}", trip.destinationName, null, it.point, PlaceKind.POI, "local")) } },
                    )
                }
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}

private fun clock(ms: Long): String { val s = ms / 1000; return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60) }
private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
