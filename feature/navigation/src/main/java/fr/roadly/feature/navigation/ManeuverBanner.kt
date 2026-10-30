package fr.roadly.feature.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes

/**
 * Bandeau de manœuvre : tuile de manœuvre (marque), distance en grand, rue et pastille de sortie.
 * [expanded] = false réduit le bandeau à la tuile et à la distance (bouton « replier »).
 */
@Composable
fun ManeuverBanner(banner: BannerUi, modifier: Modifier = Modifier, expanded: Boolean = true) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(banner.type.icon(), style = IconTileStyle.BrandFilled, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(banner.distanceText, style = NavDigits.copy(fontSize = 28.sp, lineHeight = 32.sp, textAlign = TextAlign.Start), color = c.textPrimary, maxLines = 1)
                    exitLabel(banner)?.let { label ->
                        Spacer(Modifier.width(8.dp))
                        ExitPill(label)
                    }
                }
                AnimatedVisibility(expanded) {
                    Column {
                        Text(banner.instruction, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        banner.street?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
private fun exitLabel(b: BannerUi): String? {
    val exit = b.exitNumber
    val roundabout = b.roundaboutExit
    return when {
        exit != null -> stringResource(R.string.nav_exit_number, exit)
        roundabout == 1 -> stringResource(R.string.nav_roundabout_exit_first)
        roundabout != null -> stringResource(R.string.nav_roundabout_exit_n, roundabout)
        else -> null
    }
}

@Composable
private fun ExitPill(label: String) {
    val c = LocalRoadlyColors.current
    Box(Modifier.background(c.accent, RoadlyShapes.Chip).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.onAccent)
    }
}
