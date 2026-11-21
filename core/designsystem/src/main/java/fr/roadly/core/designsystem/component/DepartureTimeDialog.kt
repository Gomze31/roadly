package fr.roadly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.roadly.core.designsystem.R
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import java.time.Instant
import java.time.ZoneId

/**
 * Choix de l'heure : « Partir à » ou « Arriver à » (heure du jour, le lendemain si déjà passée) ; « Maintenant » efface le choix.
 * [initialEpochMs] null ou passé = heure actuelle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartureTimeDialog(
    initialEpochMs: Long?,
    initialArrive: Boolean,
    onConfirm: (epochMs: Long, arrive: Boolean) -> Unit,
    onNow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalRoadlyColors.current
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone)
    val initial = initialEpochMs?.takeIf { it > System.currentTimeMillis() }?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: now
    val tp = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    var arriveMode by remember { mutableStateOf(initialArrive) }
    val departLabel = stringResource(R.string.departure_mode_depart)
    val arriveLabel = stringResource(R.string.departure_mode_arrive)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        confirmButton = {
            GlassButton(stringResource(R.string.departure_confirm), onClick = {
                var target = now.withHour(tp.hour).withMinute(tp.minute).withSecond(0).withNano(0)
                if (!target.isAfter(now)) target = target.plusDays(1)
                onConfirm(target.toInstant().toEpochMilli(), arriveMode)
            })
        },
        dismissButton = { GlassButton(stringResource(R.string.departure_now), onClick = onNow, style = GlassButtonStyle.Secondary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                SegmentedControl(listOf(false, true), arriveMode, { arriveMode = it }, { if (it) arriveLabel else departLabel })
                if (arriveMode) Text(stringResource(R.string.departure_arrive_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                TimePicker(state = tp)
            }
        },
    )
}
