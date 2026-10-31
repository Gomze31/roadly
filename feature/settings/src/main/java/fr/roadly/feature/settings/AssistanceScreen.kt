package fr.roadly.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.ComingSoonTag
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.RoadlyTopBar
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.theme.RoadlySpacing

/**
 * Assistance : chaque demande (bug / idée / autre) ouvre le sélecteur de partage Android avec un rapport pré-rempli
 * (type, version de l'app, appareil, Android). Pas d'adresse ni de serveur d'assistance inventés : l'utilisateur choisit le canal.
 * Le fil « Mes demandes » exige un compte : affiché « Bientôt », sans action.
 */
@Composable
fun AssistanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { context.versionName() ?: "?" }
    val kinds = listOf(
        Triple(R.string.assist_bug, Icons.Default.BugReport, IconTileStyle.Danger),
        Triple(R.string.assist_idea, Icons.Default.Lightbulb, IconTileStyle.Accent),
        Triple(R.string.assist_other, Icons.Default.MailOutline, IconTileStyle.Brand),
    )
    RoadlyScreen {
        RoadlyTopBar(stringResource(R.string.assist_title), onBack = onBack, backDescription = stringResource(R.string.settings_back))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(stringResource(R.string.assist_group_new), footer = stringResource(R.string.assist_footer)) {
                kinds.forEachIndexed { i, (res, icon, style) ->
                    val label = stringResource(res)
                    ListRow(label, icon = icon, iconStyle = style, divider = i < kinds.lastIndex, onClick = {
                        context.shareReport(subject = "Roadly — $label", body = reportBody(label, version))
                    })
                }
            }
            SettingsGroup(stringResource(R.string.assist_group_history)) {
                ListRow(stringResource(R.string.assist_history), subtitle = stringResource(R.string.assist_history_subtitle), icon = Icons.Default.Forum, iconStyle = IconTileStyle.Neutral, chevron = false, divider = false, trailing = { ComingSoonTag() })
            }
        }
    }
}

internal fun reportBody(kind: String, version: String): String = buildString {
    appendLine("Type : $kind")
    appendLine("Roadly $version")
    appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine()
    appendLine("Description :")
}

private fun Context.shareReport(subject: String, body: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
    startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
