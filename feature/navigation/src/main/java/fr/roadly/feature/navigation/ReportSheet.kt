package fr.roadly.feature.navigation

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
import fr.roadly.core.designsystem.component.ComingSoonTag
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Les neuf types de signalement de la mission (§5). Envoi réel en LOT 8 (backend communautaire). */
enum class ReportKind(val labelRes: Int, val icon: ImageVector) {
    ACCIDENT(R.string.report_accident, Icons.Default.CarCrash),
    JAM(R.string.report_jam, Icons.Default.Traffic),
    OBSTACLE(R.string.report_obstacle, Icons.Default.Block),
    CLOSED(R.string.report_closed, Icons.Default.Block),
    ROADWORK(R.string.report_roadwork, Icons.Default.Construction),
    WEATHER(R.string.report_weather, Icons.Default.Umbrella),
    POTHOLE(R.string.report_pothole, Icons.Default.ReportProblem),
    WORK_LIGHT(R.string.report_work_light, Icons.Default.WbTwilight),
    HAZARD(R.string.report_hazard, Icons.Default.Warning),
}

/**
 * Feuille « Signaler » : les types sont listés mais désactivés tant qu'aucun service communautaire
 * n'est branché (règle : aucun bouton qui prétend agir sans source réelle). [onSelect] sera relié en LOT 8.
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
                        stringResource(k.labelRes), onClick = { onSelect(k); onDismiss() }, icon = k.icon,
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
