package fr.roadly.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.navigation.BackgroundIssue

/**
 * Avertissement montré une fois par lancement de l'application, avant le premier guidage, quand un réglage du téléphone peut
 * couper le guidage écran éteint. Il ne bloque rien : « Démarrer » lance le guidage tel quel. Les boutons ouvrent les écrans du
 * système ; l'application ne modifie aucun réglage elle-même.
 */
@Composable
fun BackgroundWarningDialog(issues: List<BackgroundIssue>, onStart: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bg_warning_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.bg_warning_intro), style = MaterialTheme.typography.bodyMedium)
                issues.forEach { issue ->
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(issue.title()), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(issue.detail()), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { openSettings(ctx, issue) }) { Text(stringResource(issue.action())) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onStart) { Text(stringResource(R.string.bg_warning_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.bg_warning_cancel)) } },
    )
}

private fun BackgroundIssue.title() = when (this) {
    BackgroundIssue.NOTIFICATIONS_OFF -> R.string.bg_issue_notifications
    BackgroundIssue.BATTERY_OPTIMIZED -> R.string.bg_issue_battery
    BackgroundIssue.BACKGROUND_RESTRICTED -> R.string.bg_issue_restricted
}

private fun BackgroundIssue.detail() = when (this) {
    BackgroundIssue.NOTIFICATIONS_OFF -> R.string.bg_issue_notifications_detail
    BackgroundIssue.BATTERY_OPTIMIZED -> R.string.bg_issue_battery_detail
    BackgroundIssue.BACKGROUND_RESTRICTED -> R.string.bg_issue_restricted_detail
}

private fun BackgroundIssue.action() = when (this) {
    BackgroundIssue.NOTIFICATIONS_OFF -> R.string.bg_open_notifications
    BackgroundIssue.BATTERY_OPTIMIZED, BackgroundIssue.BACKGROUND_RESTRICTED -> R.string.bg_open_app_settings
}

/** Écran système correspondant ; à défaut (constructeur qui ne le fournit pas), la fiche de l'application. */
private fun openSettings(ctx: Context, issue: BackgroundIssue) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
    val wanted = when (issue) {
        BackgroundIssue.NOTIFICATIONS_OFF -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        BackgroundIssue.BATTERY_OPTIMIZED, BackgroundIssue.BACKGROUND_RESTRICTED -> details
    }
    for (intent in listOf(wanted, details)) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Écran absent sur ce téléphone : essai suivant.
        }
    }
}
