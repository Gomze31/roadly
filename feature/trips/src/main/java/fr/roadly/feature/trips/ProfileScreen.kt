package fr.roadly.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.RoadlyTopBar
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.StatTile
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Trophies
import fr.roadly.core.model.Trophy
import java.util.Locale

/** Profil : pseudonyme et points (serveur, si configuré), km / trajets / temps (local), trophées (local), classement (serveur). */
@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenTrips: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    RoadlyScreen {
        RoadlyTopBar(stringResource(R.string.profile_title), onBack = onBack, backDescription = stringResource(R.string.trips_back), actions = {
            if (state.server !is ServerSide.Unconfigured) RoundIconButton(Icons.Default.Refresh, stringResource(R.string.profile_refresh), onClick = vm::refresh, size = 40.dp)
        })
        LazyColumn(contentPadding = PaddingValues(start = RoadlySpacing.m, end = RoadlySpacing.m, top = RoadlySpacing.s, bottom = RoadlySpacing.l), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.l), modifier = Modifier.navigationBarsPadding()) {
            item {
                SettingsGroup(null) {
                    Row(Modifier.fillMaxWidth().padding(RoadlySpacing.m), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Default.Person, style = IconTileStyle.BrandFilled, size = 56.dp)
                        Spacer(Modifier.width(RoadlySpacing.sm))
                        Column(Modifier.weight(1f)) {
                            val s = state.server
                            Text(if (s is ServerSide.Ready) s.profile.pseudonym else stringResource(R.string.profile_local_only), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (s) {
                                    is ServerSide.Ready -> stringResource(R.string.profile_points, s.profile.points)
                                    ServerSide.Loading -> stringResource(R.string.profile_loading)
                                    is ServerSide.Failed -> s.error.userMessage()
                                    ServerSide.Unconfigured -> stringResource(R.string.profile_unconfigured)
                                },
                                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatTile(String.format(Locale.FRANCE, "%.0f", state.all.distanceM / 1000), stringResource(R.string.trips_stat_km))
                        StatTile(state.all.trips.toString(), stringResource(R.string.trips_stat_trips))
                        StatTile(Formatters.duration(state.all.durationS.toDouble()), stringResource(R.string.trips_stat_time))
                    }
                    ListRow(stringResource(R.string.profile_open_trips), icon = Icons.Default.History, iconStyle = IconTileStyle.Brand, onClick = onOpenTrips, divider = false)
                }
            }
            item {
                SettingsGroup(stringResource(R.string.profile_trophies), footer = stringResource(R.string.profile_trophies_footer)) {
                    Column(Modifier.padding(RoadlySpacing.m), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                        state.trophies.forEach { TrophyRow(it) }
                    }
                }
            }
            item {
                SettingsGroup(stringResource(R.string.profile_leaderboard), footer = stringResource(R.string.profile_leaderboard_footer)) {
                    when (val s = state.server) {
                        ServerSide.Unconfigured -> ListRow(stringResource(R.string.profile_leaderboard), subtitle = stringResource(R.string.profile_leaderboard_unconfigured), icon = Icons.Default.Groups, iconStyle = IconTileStyle.Neutral, chevron = false, divider = false)
                        ServerSide.Loading -> Box(Modifier.padding(RoadlySpacing.m)) { LoadingRow(stringResource(R.string.profile_loading)) }
                        is ServerSide.Failed -> ErrorState(s.error.userMessage(), onRetry = vm::refresh)
                        is ServerSide.Ready -> if (s.leaderboard.isEmpty()) {
                            ListRow(stringResource(R.string.profile_leaderboard_empty), icon = Icons.Default.Groups, iconStyle = IconTileStyle.Neutral, chevron = false, divider = false)
                        } else s.leaderboard.forEachIndexed { i, e ->
                            ListRow(e.pseudonym, value = stringResource(R.string.profile_points, e.points), icon = null, chevron = false, divider = i < s.leaderboard.lastIndex,
                                trailing = { Text("#${e.rank}", style = MaterialTheme.typography.labelLarge, color = if (e.pseudonym == (s.profile.pseudonym)) c.brandBright else c.textTertiary) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrophyRow(t: Trophy) {
    val c = LocalRoadlyColors.current
    val label = stringResource(when (t.id) {
        Trophies.KM_10 -> R.string.trophy_km_10; Trophies.KM_100 -> R.string.trophy_km_100; Trophies.KM_1000 -> R.string.trophy_km_1000
        Trophies.TRIPS_1 -> R.string.trophy_trips_1; Trophies.TRIPS_10 -> R.string.trophy_trips_10; Trophies.TRIPS_100 -> R.string.trophy_trips_100
        Trophies.LONGEST_50 -> R.string.trophy_longest_50; else -> R.string.trophy_longest_200
    })
    Row(Modifier.fillMaxWidth().alpha(if (t.unlocked) 1f else 0.6f), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Default.EmojiEvents, style = if (t.unlocked) IconTileStyle.Accent else IconTileStyle.Neutral, size = 36.dp)
        Spacer(Modifier.width(RoadlySpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).background(c.glassBorder, RoadlyShapes.Pill)) {
                Box(Modifier.fillMaxWidth((t.progress / t.target).toFloat().coerceIn(0f, 1f)).height(5.dp).background(if (t.unlocked) c.accent else c.brand, RoadlyShapes.Pill))
            }
        }
        Spacer(Modifier.width(RoadlySpacing.sm))
        Text(String.format(Locale.FRANCE, "%.0f / %.0f", t.progress, t.target), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}
