package fr.roadly.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.repository.ParkedVehicle
import fr.roadly.core.data.repository.TollReminder
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.GeoPoint

/**
 * 1.0 : rappel de péage en flux libre (le plus récent ; [others] = rappels plus anciens encore ouverts). Roadly ne paie rien et ne
 * connaît pas le montant : il rappelle le passage et, en France, l'échéance de 72 h. « C'est payé » efface le rappel.
 */
@Composable
internal fun TollReminderCard(reminder: TollReminder, others: Int, overdue: Boolean, onPaid: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Euro, style = IconTileStyle.Warning, size = 36.dp)
            Spacer(Modifier.width(RoadlySpacing.s))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_toll_title) + (reminder.roads.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = " · ") ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val due = reminder.dueAtEpochMs
                Text(
                    when {
                        due == null -> stringResource(R.string.home_toll_check)
                        overdue -> stringResource(R.string.home_toll_overdue, Formatters.dayTime(due))
                        else -> stringResource(R.string.home_toll_due, Formatters.dayTime(due))
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (overdue) c.danger else c.textSecondary,
                )
                if (others > 0) Text(pluralStringResource(R.plurals.home_toll_others, others, others), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            GlassButton(stringResource(R.string.home_toll_paid), onClick = onPaid, style = GlassButtonStyle.Ghost)
        }
    }
}

/** « Il y a 2 h », « il y a 3 j » : âge d'une position mémorisée. */
internal fun ageText(nowMs: Long, thenMs: Long): AgeText {
    val min = ((nowMs - thenMs).coerceAtLeast(0L) / 60_000L).toInt()
    return when {
        min < 1 -> AgeText(AgeUnit.NOW, 0)
        min < 60 -> AgeText(AgeUnit.MINUTES, min)
        min < 48 * 60 -> AgeText(AgeUnit.HOURS, min / 60)
        else -> AgeText(AgeUnit.DAYS, min / (24 * 60))
    }
}

internal enum class AgeUnit { NOW, MINUTES, HOURS, DAYS }
internal data class AgeText(val unit: AgeUnit, val value: Int)

@Composable
private fun AgeText.label(): String = when (unit) {
    AgeUnit.NOW -> stringResource(R.string.home_age_now)
    AgeUnit.MINUTES -> stringResource(R.string.home_age_minutes, value)
    AgeUnit.HOURS -> stringResource(R.string.home_age_hours, value)
    AgeUnit.DAYS -> stringResource(R.string.home_age_days, value)
}

/** 1.0 : « Mon véhicule · 350 m · il y a 2 h » ; toucher la ligne le montre sur la carte. */
@Composable
internal fun ParkedVehicleRow(vehicle: ParkedVehicle, from: GeoPoint?, nowMs: Long, onShow: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().clip(RoadlyShapes.Tile).clickable(role = Role.Button, onClick = onShow).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Default.DirectionsCar, style = IconTileStyle.Brand, size = 28.dp)
        Spacer(Modifier.width(RoadlySpacing.s))
        val parts = listOfNotNull(
            stringResource(R.string.home_parked_title),
            from?.let { Formatters.distance(it.distanceTo(vehicle.point)) },
            ageText(nowMs, vehicle.savedAtEpochMs).label(),
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun DriveMemoryNightPreview() {
    RoadlyTheme(darkTheme = true) {
        GlassPanel {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                TollReminderCard(TollReminder(1, 1_800_000_000_000, listOf("A 79"), "FR", 4), others = 1, overdue = false, onPaid = {})
                ParkedVehicleRow(ParkedVehicle(GeoPoint(43.6, 1.44), 1_800_000_000_000, "Capitole"), GeoPoint(43.603, 1.44), 1_800_007_200_000, onShow = {})
            }
        }
    }
}
