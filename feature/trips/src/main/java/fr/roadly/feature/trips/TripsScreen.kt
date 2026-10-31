package fr.roadly.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.EmptyState
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.RoadlyTopBar
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.StatTile
import fr.roadly.core.designsystem.component.SurfaceCard
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripStats
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Historique des trajets : bilan du mois, totaux, liste (touchez un trajet pour le rejouer). */
@Composable
fun TripsScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit, vm: TripsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    RoadlyScreen {
        RoadlyTopBar(stringResource(R.string.trips_title), onBack = onBack, backDescription = stringResource(R.string.trips_back))
        if (state.trips.isEmpty()) {
            EmptyState(Icons.Default.History, stringResource(R.string.trips_empty_title), stringResource(R.string.trips_empty_subtitle))
            return@RoadlyScreen
        }
        LazyColumn(contentPadding = PaddingValues(start = RoadlySpacing.m, end = RoadlySpacing.m, top = RoadlySpacing.s, bottom = RoadlySpacing.l), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.l), modifier = Modifier.navigationBarsPadding()) {
            item { StatsCard(stringResource(R.string.trips_month), state.month) }
            item { StatsCard(stringResource(R.string.trips_all), state.all) }
            item {
                SettingsGroup(stringResource(R.string.trips_list), footer = stringResource(R.string.trips_footer)) {
                    state.trips.forEachIndexed { i, t -> TripRow(t, onOpen = { onOpenTrip(t.id) }, onDelete = { vm.delete(t.id) }, divider = i < state.trips.lastIndex) }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, s: TripStats) {
    SettingsGroup(title) {
        Row(Modifier.fillMaxWidth().padding(RoadlySpacing.m), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(s.trips.toString(), stringResource(R.string.trips_stat_trips))
            StatTile(String.format(Locale.FRANCE, "%.0f", s.distanceM / 1000), stringResource(R.string.trips_stat_km))
            StatTile(Formatters.duration(s.durationS.toDouble()), stringResource(R.string.trips_stat_time))
            StatTile(if (s.costEur > 0) Formatters.euros(s.costEur) else "—", stringResource(R.string.trips_stat_cost))
        }
    }
}

@Composable
private fun TripRow(t: Trip, onOpen: () -> Unit, onDelete: () -> Unit, divider: Boolean) {
    val c = LocalRoadlyColors.current
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE).format(Date(t.startedAtEpochMs))
    val cost = t.costEur?.let { " · " + Formatters.euros(it) } ?: ""
    ListRow(
        title = t.destinationName,
        subtitle = "$date · ${Formatters.distance(t.distanceM)} · ${Formatters.duration(t.durationS.toDouble())}$cost · " + stringResource(if (t.arrived) R.string.trips_arrived else R.string.trips_stopped),
        icon = if (t.arrived) Icons.Default.Flag else Icons.Default.Route,
        iconStyle = if (t.arrived) IconTileStyle.Brand else IconTileStyle.Neutral,
        onClick = onOpen, divider = divider,
        trailing = { RoundIconButton(Icons.Default.Delete, stringResource(R.string.trips_delete), onClick = onDelete, size = 36.dp) },
    )
}
