package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RouteOptionRow
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.routing.RouteOption
import java.time.ZoneId

/** Feuille « Itinéraires » pendant le guidage : alternatives depuis la position actuelle, bascule sans quitter l'écran. */
@Composable
fun AlternativesSheet(ui: AlternativesUi, onSelect: (RouteOption) -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.alt_overline))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.alt_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(RoadlySpacing.m))
            when (ui) {
                AlternativesUi.Hidden -> Unit
                AlternativesUi.Loading -> LoadingRow(stringResource(R.string.alt_loading))
                is AlternativesUi.Error -> ErrorState(ui.error.userMessage(), onRetry = onRetry)
                is AlternativesUi.Content -> {
                    val zone = ZoneId.systemDefault()
                    ui.options.forEach { o ->
                        RouteOptionRow(
                            duration = Formatters.duration(o.route.durationSeconds),
                            title = o.label,
                            distance = Formatters.distance(o.route.distanceMeters),
                            selected = false,
                            onClick = { onSelect(o) },
                            note = stringResource(R.string.alt_arrival, Formatters.clockTime(o.arrivalEpochMs, zone)) + if (o.route.hasToll) " · " + stringResource(R.string.alt_toll) else "",
                        )
                    }
                    Spacer(Modifier.height(RoadlySpacing.s))
                    Text(stringResource(R.string.alt_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                    Spacer(Modifier.height(RoadlySpacing.sm))
                    GlassButton(stringResource(R.string.alt_keep), onClick = onDismiss, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}


