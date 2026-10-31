package fr.roadly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.R
import fr.roadly.core.model.ReportKind
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Libellé et icône des neuf types de signalement (`core:model` ReportKind, alignés sur l'enum SQL). */
fun ReportKind.labelRes(): Int = when (this) {
    ReportKind.ACCIDENT -> R.string.report_accident; ReportKind.JAM -> R.string.report_jam; ReportKind.OBSTACLE -> R.string.report_obstacle
    ReportKind.CLOSED -> R.string.report_closed; ReportKind.ROADWORK -> R.string.report_roadwork; ReportKind.WEATHER -> R.string.report_weather
    ReportKind.POTHOLE -> R.string.report_pothole; ReportKind.WORK_LIGHT -> R.string.report_work_light; ReportKind.HAZARD -> R.string.report_hazard
}

fun ReportKind.icon(): ImageVector = when (this) {
    ReportKind.ACCIDENT -> Icons.Default.CarCrash; ReportKind.JAM -> Icons.Default.Traffic; ReportKind.OBSTACLE, ReportKind.CLOSED -> Icons.Default.Block
    ReportKind.ROADWORK -> Icons.Default.Construction; ReportKind.WEATHER -> Icons.Default.Umbrella; ReportKind.POTHOLE -> Icons.Default.ReportProblem
    ReportKind.WORK_LIGHT -> Icons.Default.WbTwilight; ReportKind.HAZARD -> Icons.Default.Warning
}

/** Catégorie Horizon d'un signalement. */
fun ReportKind.horizonKind(): HorizonKind = when (this) {
    ReportKind.JAM, ReportKind.WEATHER -> HorizonKind.SLOWDOWN
    ReportKind.ACCIDENT, ReportKind.OBSTACLE, ReportKind.HAZARD -> HorizonKind.INCIDENT
    ReportKind.CLOSED -> HorizonKind.CLOSURE
    ReportKind.ROADWORK, ReportKind.WORK_LIGHT -> HorizonKind.ROADWORK
    ReportKind.POTHOLE -> HorizonKind.SPEED_BUMP
}

/**
 * Feuille « Signaler » : les types sont listés mais désactivés tant qu'aucun service communautaire
 * n'est branché (règle : aucun bouton qui prétend agir sans source réelle). [enabled] = service configuré (Réglages → Communauté).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(onDismiss: () -> Unit, enabled: Boolean = false, onSelect: (ReportKind) -> Unit = {}) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.report_overline))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(RoadlySpacing.m))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                ReportKind.entries.forEach { k ->
                    GlassChip(
                        stringResource(k.labelRes()), onClick = { onSelect(k); onDismiss() }, icon = k.icon(),
                        enabled = enabled, trailing = if (enabled) null else ({ ComingSoonTag() }),
                    )
                }
            }
            if (!enabled) {
                Spacer(Modifier.height(RoadlySpacing.m))
                Text(stringResource(R.string.report_unavailable), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}
