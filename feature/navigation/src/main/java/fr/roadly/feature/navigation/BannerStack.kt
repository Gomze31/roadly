package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

/**
 * Pile du bandeau, commune aux deux overlays, dans l'ordre : bandeau (D1, D2), puis panneau de direction à l'approche (D3)
 * ou voies seules (D4) de la même manœuvre, puis la ligne « puis … » (D1) de la manœuvre d'après.
 * Un seul appel par overlay garde la surface de fusion minimale.
 */
@Composable
internal fun BannerStack(
    state: NavigationUiState,
    expanded: Boolean,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
    /** Vue 3D de carrefour visible : elle remplace le panneau de direction et les voies (dessinés dans la scène). */
    junction: (@Composable () -> Unit)? = null,
) {
    val banner = state.banner ?: return
    val sign = if (junction != null) null else bannerSign(state)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        ManeuverBanner(banner, Modifier.fillMaxWidth(), expanded = expanded, onClick = onOpenPreview, roundabout = state.roundabout, signShownBelow = sign != null)
        if (junction != null) {
            junction()
        } else if (sign != null) {
            ExitSignPanel(banner.type, sign.exitNumber, sign.toward, sign.branch, if (state.showLanes) state.lanes else emptyList(), banner.distanceText, isNext = true, Modifier.fillMaxWidth())
        } else if (state.showLanes) {
            LaneGuidanceView(state.lanes, emphasized = state.approach != null)
        }
        state.then?.let { ThenRow(it) }
    }
}

/** D3 : panneau montré sous le bandeau, seulement à l'approche (`UiState.sign` est rempli quelle que soit la distance ; l'aperçu le montre toujours). */
internal fun bannerSign(state: NavigationUiState): ExitSignUi? = state.sign?.takeIf { state.approach != null }

/** D1 : « puis ↱ Rue X », manœuvre d'après à moins de 500 m de la prochaine (icône 20 dp + texte). */
@Composable
internal fun ThenRow(then: ThenUi, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier, shape = RoadlyShapes.Chip, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_then), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.width(8.dp))
            Icon(then.type.icon(), contentDescription = null, tint = c.brandBright, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(then.label, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun ThenRowNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { ThenRow(ThenUi(ManeuverType.TURN_RIGHT, "Rue Louis Plana")) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun ThenRowDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { ThenRow(ThenUi(ManeuverType.ROUNDABOUT_EXIT, "Rue Médiane")) } }
