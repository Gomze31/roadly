package fr.roadly.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.update.UpdateState
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import java.time.ZoneId

/** Réglages › Mises à jour : version installée, vérification sur les releases GitHub, téléchargement, installation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdatesGroup(vm: UpdatesViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var repo by remember(ui.repository) { mutableStateOf(ui.repository) }
    var token by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onPermissionScreenClosed() }

    SettingsGroup("Mises à jour") {
        val st = ui.state
        val status = when (st) {
            UpdateState.Idle -> ui.lastCheckAtMs?.let { "Dernière vérification à ${Formatters.clockTime(it, ZoneId.systemDefault())}" } ?: "Jamais vérifié"
            UpdateState.Checking -> "Vérification sur GitHub…"
            is UpdateState.UpToDate -> "Roadly est à jour"
            is UpdateState.Available -> "Version ${st.update.manifest.versionName} disponible"
            is UpdateState.Incompatible -> "Version ${st.manifest.versionName} : Android ${st.manifest.minSdk}+ requis"
            is UpdateState.Downloading -> "Téléchargement de ${st.update.manifest.versionName}…"
            is UpdateState.ReadyToInstall -> "Prête à installer"
            is UpdateState.NeedsPermission -> "Autorisez Roadly à installer des applications"
            is UpdateState.Installing -> "Installation : confirmez dans la fenêtre Android"
            is UpdateState.Failed -> st.message
        }
        ListRow(
            "Version ${ui.current.name} (${ui.current.code})",
            subtitle = status,
            icon = Icons.Default.SystemUpdate,
            onClick = { editing = !editing }, chevron = false, trailing = { ExpandArrow(editing) },
        )
        if (st is UpdateState.Downloading && st.totalBytes > 0) {
            LinearProgressIndicator(progress = { (st.doneBytes.toFloat() / st.totalBytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        if (st is UpdateState.Available && st.update.notes.isNotBlank()) {
            Text(st.update.notes.take(800), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            when (st) {
                is UpdateState.Available, is UpdateState.ReadyToInstall -> GlassButton("Installer", onClick = vm::install)
                is UpdateState.NeedsPermission -> GlassButton("Autoriser l'installation", onClick = {
                    permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                })
                is UpdateState.Failed -> if (st.update != null) GlassButton("Réessayer", onClick = vm::install)
                else -> Unit
            }
            GlassButton("Vérifier maintenant", onClick = vm::check, style = GlassButtonStyle.Secondary, enabled = st !is UpdateState.Checking && st !is UpdateState.Downloading)
        }
        ToggleRow("Vérifier au lancement", checked = ui.autoCheck, onCheckedChange = vm::setAutoCheck, subtitle = "À l'ouverture de Roadly, au plus toutes les 30 min", divider = false)
        ExpandableContent(editing) {
            SettingsField("Dépôt GitHub (propriétaire/dépôt)", repo, { repo = it }, KeyboardType.Uri)
            SettingsField(if (ui.hasToken) "Nouveau jeton GitHub (laisser vide pour garder l'actuel)" else "Jeton GitHub (dépôt privé)", token, { token = it }, KeyboardType.Password)
            Text(
                "Dépôt privé : jeton « fine-grained » limité à ce dépôt, permission Contents en lecture seule. Il est chiffré par le Keystore Android et n'est envoyé qu'à api.github.com.",
                style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton("Enregistrer", onClick = { vm.saveSource(repo, token.takeIf { it.isNotBlank() }); token = "" })
                if (ui.hasToken) GlassButton("Supprimer le jeton", onClick = vm::clearToken, style = GlassButtonStyle.Danger)
            }
        }
    }
}
