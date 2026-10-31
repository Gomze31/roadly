package fr.roadly.feature.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.RoundaboutInfo

/** Étiquette du bandeau pour les tests instrumentés. */
internal const val BANNER_TAG = "maneuver_banner"

/**
 * Bandeau de manœuvre : tuile de manœuvre (marque) ou schéma de rond-point compact (D2), distance en grand, rue et pastille de sortie.
 * [expanded] = false réduit le bandeau à la tuile, à la distance et à la direction de sortie (bouton « replier »).
 * [onClick] : tout le panneau (≥ 56 dp) ouvre l'aperçu de la manœuvre (D1).
 * [signShownBelow] : le panneau de direction est affiché dessous (D3) ; la pastille et la ligne « Direction … » s'effacent pour ne pas doubler.
 */
@Composable
fun ManeuverBanner(
    banner: BannerUi,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onClick: (() -> Unit)? = null,
    roundabout: RoundaboutInfo? = null,
    signShownBelow: Boolean = false,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.testTag(BANNER_TAG).defaultMinSize(minHeight = RoadlySpacing.touchTargetDriving), contentPadding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (roundabout != null) {
                RoundaboutManeuverView(roundabout, banner.roundaboutExit ?: roundabout.exitNumber, compact = true)
            } else {
                IconTile(banner.type.icon(), style = IconTileStyle.BrandFilled, size = 48.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(banner.distanceText, style = NavDigits.copy(fontSize = 28.sp, lineHeight = 32.sp, textAlign = TextAlign.Start), color = c.textPrimary, maxLines = 1)
                    if (!signShownBelow) {
                        exitLabel(banner)?.let { label ->
                            Spacer(Modifier.width(8.dp))
                            ExitPill(label)
                        }
                    }
                }
                // Direction de sortie visible, bandeau replié ou non (spec §3) ; à l'approche, le panneau D3 la reprend.
                if (!signShownBelow) {
                    banner.exitToward?.let {
                        Text(stringResource(R.string.nav_exit_toward, it), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Box(Modifier.background(c.accent, RoadlyShapes.Chip).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onAccent)
    }
}

private val sampleBanner = BannerUi(ManeuverType.ROUNDABOUT_ENTER, "150 m", "Au rond-point, prenez la 2e sortie.", "Avenue des Champs-Élysées", null, 2)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 420)
@Composable
private fun ManeuverBannerNightPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { ManeuverBanner(sampleBanner, onClick = {}, roundabout = previewRoundabout(180.0, 2, "Avenue des Champs-Élysées")) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 420)
@Composable
private fun ManeuverBannerDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) { ManeuverBanner(sampleBanner.copy(type = ManeuverType.TURN_RIGHT, roundaboutExit = null), onClick = {}) }
}
