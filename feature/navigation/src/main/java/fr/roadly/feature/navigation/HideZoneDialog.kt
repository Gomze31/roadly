package fr.roadly.feature.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Confirmation « fausse alerte » : la zone n'est plus annoncée ni affichée sur ce téléphone (réversible dans Réglages › Radars). */
@Composable
internal fun HideZoneDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nav_zone_false_alarm)) },
        text = { Text(stringResource(R.string.nav_zone_false_alarm_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.nav_zone_hide)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
