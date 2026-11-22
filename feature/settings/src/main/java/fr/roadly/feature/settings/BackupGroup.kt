package fr.roadly.feature.settings

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.data.backup.BackupError
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.SettingsGroup
import java.time.LocalDate

/**
 * Réglages › Données › Sauvegarde. Le fichier est créé ou choisi par le sélecteur de documents d'Android (aucune permission
 * de stockage) : Roadly n'écrit et ne lit que le document désigné par l'utilisateur.
 */
@Composable
internal fun BackupGroup(vm: BackupViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val defaultName = remember { "roadly-sauvegarde-${LocalDate.now()}.json" }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.export(
                open = { resolver.openOutputStream(uri, "wt") },
                // Export impossible : le document vide que le sélecteur vient de créer n'est pas laissé derrière.
                onFailure = { runCatching { DocumentsContract.deleteDocument(resolver, uri) } },
            )
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import { resolver.openInputStream(uri) }
    }
    val working = ui == BackupUi.Working

    SettingsGroup(stringResource(R.string.settings_backup), footer = backupStatus(ui) ?: stringResource(R.string.settings_backup_footer)) {
        ListRow(
            stringResource(R.string.settings_backup_export), subtitle = stringResource(R.string.settings_backup_export_hint),
            icon = Icons.Default.FileUpload, enabled = !working, onClick = { createDocument.launch(defaultName) },
        )
        ListRow(
            stringResource(R.string.settings_backup_import), subtitle = stringResource(R.string.settings_backup_import_hint),
            icon = Icons.Default.FileDownload, enabled = !working, divider = false,
            // Certains gestionnaires de fichiers ne reconnaissent pas le type JSON : les types voisins sont acceptés.
            onClick = { openDocument.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        )
    }
}

/** Bilan de la dernière opération, null tant qu'aucune n'a eu lieu. */
@Composable
private fun backupStatus(ui: BackupUi): String? = when (ui) {
    BackupUi.Idle -> null
    BackupUi.Working -> stringResource(R.string.settings_backup_working)
    is BackupUi.Exported -> listOfNotNull(
        stringResource(R.string.settings_backup_exported, ui.summary.favorites, ui.summary.trips),
        ui.summary.tripsWithoutTrack.takeIf { it > 0 }?.let { stringResource(R.string.settings_backup_exported_no_track, it) },
        ui.summary.omitted.takeIf { it > 0 }?.let { stringResource(R.string.settings_backup_rows_ignored, it) },
    ).joinToString(" ")
    is BackupUi.Imported -> listOfNotNull(
        stringResource(R.string.settings_backup_imported, ui.summary.favorites, ui.summary.tripsAdded, ui.summary.tripsAlreadyPresent),
        ui.summary.rejectedRows.takeIf { it > 0 }?.let { stringResource(R.string.settings_backup_rows_ignored, it) },
    ).joinToString(" ")
    is BackupUi.ExportFailed -> stringResource(R.string.settings_backup_export_failed, backupError(ui.error, export = true))
    is BackupUi.ImportFailed -> stringResource(R.string.settings_backup_import_failed, backupError(ui.error, export = false))
}

@Composable
private fun backupError(error: BackupError, export: Boolean): String = stringResource(
    when (error) {
        BackupError.TOO_LARGE -> R.string.settings_backup_error_too_large
        BackupError.NOT_A_BACKUP -> R.string.settings_backup_error_not_a_backup
        BackupError.NEWER_FORMAT -> R.string.settings_backup_error_newer_format
        BackupError.IO -> if (export) R.string.settings_backup_error_write else R.string.settings_backup_error_read
    },
)
