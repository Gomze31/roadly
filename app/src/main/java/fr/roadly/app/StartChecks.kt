package fr.roadly.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.location.LocationIssue

/** Décisions des contrôles faits avant un guidage, séparées de l'affichage pour être testées sans Android. */
object StartChecks {
    /**
     * Un guidage ne démarre pas sans position exploitable : localisation coupée (aucune position) ou position approximative
     * seulement (plusieurs centaines de mètres, alors que le suivi exige 30 m). Le conducteur est renvoyé vers le réglage.
     */
    fun blocksGuidance(issue: LocationIssue?): Boolean = issue != null

    /** Avertissement « voix indisponible » : montré une seule fois par lancement de l'application, jamais si la voix fonctionne. */
    fun voiceNoticeDue(voiceAvailable: Boolean, alreadyShown: Boolean): Boolean = !voiceAvailable && !alreadyShown
}

private fun LocationIssue.title() = when (this) {
    LocationIssue.SERVICES_OFF -> R.string.loc_off_title
    LocationIssue.APPROXIMATE_ONLY -> R.string.loc_approx_title
}

private fun LocationIssue.text() = when (this) {
    LocationIssue.SERVICES_OFF -> R.string.loc_off_text
    LocationIssue.APPROXIMATE_ONLY -> R.string.loc_approx_text
}

private fun LocationIssue.action() = when (this) {
    LocationIssue.SERVICES_OFF -> R.string.loc_off_action
    LocationIssue.APPROXIMATE_ONLY -> R.string.loc_approx_action
}

/** Bandeau de l'accueil : dit pourquoi la position n'arrive pas, au lieu de « Localisation… » sans fin. Disparaît dès que c'est réglé. */
@Composable
fun LocationIssueBanner(issue: LocationIssue, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
            Text(stringResource(issue.title()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(issue.text()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = { openLocationSettings(ctx, issue) }) { Text(stringResource(issue.action())) }
        }
    }
}

/** Au départ d'un guidage : même explication, et le guidage ne démarre pas tant que la position n'est pas exploitable. */
@Composable
fun LocationIssueDialog(issue: LocationIssue, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(issue.title())) },
        text = { Text(stringResource(issue.text()) + "\n\n" + stringResource(R.string.loc_guidance_blocked)) },
        confirmButton = { TextButton(onClick = { openLocationSettings(ctx, issue); onDismiss() }) { Text(stringResource(issue.action())) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.bg_warning_cancel)) } },
    )
}

/** Voix française absente (moteur de synthèse manquant, langue non installée) : le guidage continue à l'écran, en silence. */
@Composable
fun VoiceUnavailableDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_missing_title)) },
        text = { Text(stringResource(R.string.voice_missing_text)) },
        confirmButton = { TextButton(onClick = { openVoiceSettings(ctx); onDismiss() }) { Text(stringResource(R.string.voice_missing_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.voice_missing_later)) } },
    )
}

private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))

private fun openFirst(ctx: Context, vararg intents: Intent) {
    for (intent in intents) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Écran absent sur ce téléphone : essai suivant.
        }
    }
}

private fun openLocationSettings(ctx: Context, issue: LocationIssue) = when (issue) {
    LocationIssue.SERVICES_OFF -> openFirst(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    // La position exacte se règle dans la fiche de l'application (Autorisations › Position › « Utiliser la position exacte »).
    LocationIssue.APPROXIMATE_ONLY -> openFirst(ctx, appDetails(ctx), Intent(Settings.ACTION_SETTINGS))
}

/** Réglages de synthèse vocale du système (action non publique mais présente sur Android depuis l'API 14), sinon les réglages. */
private fun openVoiceSettings(ctx: Context) = openFirst(ctx, Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
