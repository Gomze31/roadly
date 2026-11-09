package fr.roadly.feature.navigation

import fr.roadly.core.designsystem.theme.LocalUiScale
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Traffic

import androidx.compose.foundation.layout.size

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
import androidx.compose.ui.semantics.Role
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
 * Au rond-point, la ligne « vers {rue de sortie} » ([bannerStreet]) remplace le nom de l'anneau et reste visible bandeau replié (D2).
 * [onClick] : tout le panneau (≥ 56 dp) ouvre l'aperçu de la manœuvre (D1), annoncé « Aperçu de la manœuvre » par TalkBack (bouton).
 * [signShownBelow] : le panneau de direction est affiché dessous (D3) ; la pastille, la ligne « Direction … » et l'instruction sur
 * 2 lignes s'effacent pour ne pas doubler (≈ 48 dp gagnés) ; la distance et la ligne de rue restent.
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
    val street = bannerStreet(banner, roundabout)
    GlassPanel(
        modifier.testTag(BANNER_TAG).defaultMinSize(minHeight = RoadlySpacing.touchTargetDriving), contentPadding = 12.dp, onClick = onClick,
        onClickLabel = stringResource(R.string.nav_preview_title), role = Role.Button,
    ) {
        val s = LocalUiScale.current
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            // Pictogramme posé sur le panneau, en couleur de marque (référence visuelle), sans tuile.
            Box(Modifier.size(58.dp * s), contentAlignment = Alignment.Center) {
                if (roundabout != null) {
                    RoundaboutManeuverView(roundabout, banner.roundaboutExit ?: roundabout.exitNumber, compact = true)
                } else {
                    Icon(banner.type.icon(), contentDescription = null, tint = c.brand, modifier = Modifier.size(50.dp * s))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(banner.distanceText, style = NavDigits.copy(fontSize = 34.sp * s, lineHeight = 38.sp * s, textAlign = TextAlign.Start), color = c.brand, maxLines = 1)
                    // Repère « au feu » (feu OSM au point de manœuvre), comme les directions par repère de Google Maps.
                    if (banner.atTrafficLight) {
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier.background(c.success.copy(alpha = 0.16f), RoadlyShapes.Pill).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Traffic, contentDescription = null, tint = c.success, modifier = Modifier.size(18.dp * s))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.nav_at_light), style = MaterialTheme.typography.labelLarge, color = c.success, maxLines = 1)
                        }
                    }
                }
                // Ligne principale : « 2e sortie · rue » (ou « Sortie 12 · rue »), en gras ; l'instruction complète si rien d'autre.
                val exit = if (signShownBelow) null else exitLabel(banner)
                val mainLine = listOfNotNull(exit, street).joinToString(" · ").ifEmpty { null }
                if (mainLine != null) {
                    Text(
                        mainLine, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp * s, lineHeight = 23.sp * s), color = c.textPrimary,
                        maxLines = if (expanded) 2 else 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                // Direction de sortie visible, bandeau replié ou non (spec §3) ; à l'approche, le panneau D3 la reprend.
                if (!signShownBelow) {
                    banner.exitToward?.let {
                        Text(stringResource(R.string.nav_exit_toward, it), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AnimatedVisibility(expanded && mainLine == null && !signShownBelow) {
                    Text(banner.instruction, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
