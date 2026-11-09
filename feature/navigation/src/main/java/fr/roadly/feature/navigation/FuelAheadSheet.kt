package fr.roadly.feature.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.EmptyState
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.FuelType
import fr.roadly.core.navigation.StationAhead
import java.util.Locale

/**
 * Feuille « Carburant sur le trajet » : stations devant soi à moins de 1,5 km du tracé, de la moins chère à la plus chère
 * (flux officiel des prix), distance devant et détour estimé ; un appui ajoute la station comme arrêt.
 */
@Composable
fun FuelAheadSheet(ui: FuelAheadUi, onPick: (StationAhead) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.fuel_ahead_overline))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.fuel_ahead_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(RoadlySpacing.s))
            when (ui) {
                FuelAheadUi.Hidden -> Unit
                FuelAheadUi.Loading -> LoadingRow(stringResource(R.string.fuel_ahead_loading))
                FuelAheadUi.Electric -> EmptyState(Icons.Default.EvStation, stringResource(R.string.fuel_ahead_electric))
                is FuelAheadUi.Error -> ErrorState(ui.error.userMessage(), onRetry = onRetry)
                is FuelAheadUi.Content -> if (ui.stations.isEmpty()) {
                    EmptyState(Icons.Default.LocalGasStation, stringResource(R.string.fuel_ahead_empty))
                } else {
                    Text(stringResource(R.string.fuel_ahead_subtitle, fuelName(ui.fuel)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(RoadlySpacing.s))
                    val cheapest = ui.stations.firstOrNull()?.priceEur
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        ui.stations.forEachIndexed { i, s ->
                            if (i > 0) RowDivider(inset = 0.dp)
                            StationRow(s, best = i == 0 && cheapest != null, extraVsBest = if (i > 0 && cheapest != null) s.priceEur?.minus(cheapest) else null, onClick = { onPick(s) })
                        }
                    }
                    Spacer(Modifier.height(RoadlySpacing.s))
                    Text(stringResource(R.string.fuel_ahead_source), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun StationRow(s: StationAhead, best: Boolean, extraVsBest: Double?, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val add = stringResource(R.string.fuel_ahead_add)
    Row(
        Modifier.fillMaxWidth().heightIn(min = RoadlySpacing.touchTargetDriving).clickable(onClickLabel = add, role = Role.Button, onClick = onClick)
            .padding(vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Default.LocalGasStation, style = if (best) IconTileStyle.Brand else IconTileStyle.Neutral, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.station.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.fuel_ahead_where, Formatters.distance(s.aheadM), Formatters.distance(s.detourM)),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                s.priceEur?.let { String.format(Locale.FRANCE, "%.3f €", it) } ?: "—",
                style = MaterialTheme.typography.titleMedium, color = if (best) c.success else c.textPrimary,
            )
            when {
                best -> Text(stringResource(R.string.fuel_ahead_best), style = MaterialTheme.typography.labelSmall, color = c.success)
                extraVsBest != null && extraVsBest > 0.0005 -> Text(String.format(Locale.FRANCE, "+%.2f €/L", extraVsBest), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
    }
}

@Composable
private fun fuelName(f: FuelType): String = stringResource(
    when (f) {
        FuelType.SP95 -> R.string.fuel_sp95
        FuelType.SP98 -> R.string.fuel_sp98
        FuelType.E10 -> R.string.fuel_e10
        FuelType.E85 -> R.string.fuel_e85
        FuelType.DIESEL -> R.string.fuel_diesel
        FuelType.LPG -> R.string.fuel_lpg
        FuelType.ELECTRIC -> R.string.fuel_electric
    },
)
