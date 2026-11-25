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
    /** Côté du schéma du mode rond-point : 116 dp en portrait, réduit en paysage où la colonne gauche est courte. */
    roundaboutDiagramSize: androidx.compose.ui.unit.Dp = 116.dp,
) {
    val banner = state.banner ?: return
    // Mode rond-point : le schéma vivant remplace la vue 3D, le panneau et les voies (une seule représentation à la fois).
    val roundaboutMode = roundaboutModeVisible(state)
    val sign = if (junction != null || roundaboutMode) null else bannerSign(state)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        ManeuverBanner(
            // Mode rond-point : le schéma dit déjà le rang et la consigne ; le bandeau ne garde que la distance et la rue de sortie.
            banner, Modifier.fillMaxWidth(), expanded = expanded, onClick = onOpenPreview, roundabout = state.roundabout, signShownBelow = sign != null || roundaboutMode,
            urgency = bannerUrgency(state), distanceUncertain = positionUncertain(state),
        )
        if (roundaboutMode) {
            RoundaboutModePanel(state.roundaboutLive!!, Modifier.fillMaxWidth(), diagramSize = roundaboutDiagramSize, distanceText = banner.distanceText)
        } else if (junction != null) {
            junction()
        } else if (branchDiagramVisible(state)) {
            // Schéma de bifurcation à gauche (branche à prendre et branches à éviter), panneau ou voies à droite.
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.Top) {
                BranchDiagramTile(state.branchDiagram!!)
                if (sign != null) {
                    ExitSignPanel(banner.type, sign.exitNumber, sign.toward, sign.branch, if (state.showLanes) state.lanes else emptyList(), banner.distanceText, isNext = true, Modifier.weight(1f))
                } else if (state.showLanes) {
                    LaneGuidanceView(state.lanes, emphasized = state.approach != null, modifier = Modifier.weight(1f))
                }
            }
        } else if (sign != null) {
            ExitSignPanel(banner.type, sign.exitNumber, sign.toward, sign.branch, if (state.showLanes) state.lanes else emptyList(), banner.distanceText, isNext = true, Modifier.fillMaxWidth())
        } else if (state.showLanes) {
            LaneGuidanceView(state.lanes, emphasized = state.approach != null)
        }
        sideHint(state)?.let { SideHintRow(it) }
        state.then?.let { ThenRow(it) }
    }
}

/** Recalcul, GPS perdu ou imprécis : les distances affichées ne sont plus mesurées. */
internal fun positionUncertain(state: NavigationUiState): Boolean =
    state.rerouting || state.gpsLost || state.situation.mode == fr.roadly.core.navigation.GuidanceMode.GPS_UNCERTAIN ||
        state.situation.mode == fr.roadly.core.navigation.GuidanceMode.REROUTING

/**
 * Le schéma de bifurcation est montré pendant la préparation d'une sortie, d'une fourche ou d'un enchaînement, quand la topologie
 * réelle du nœud est connue. La vue 3D, quand elle est affichée, garde la priorité (une seule représentation à la fois).
 */
internal fun branchDiagramVisible(state: NavigationUiState): Boolean {
    if (state.branchDiagram == null || roundaboutModeVisible(state)) return false
    return when (state.situation.mode) {
        fr.roadly.core.navigation.GuidanceMode.EXIT, fr.roadly.core.navigation.GuidanceMode.FORK, fr.roadly.core.navigation.GuidanceMode.COMPLEX -> true
        else -> false
    }
}

/**
 * Côté où se placer, montré sous le bandeau pendant la préparation d'une sortie, d'une bifurcation ou d'un enchaînement — le pendant
 * visuel de « Serrez à droite ». Absent quand les voies sont dessinées (elles le disent mieux) et dans un rond-point.
 */
internal fun sideHint(state: NavigationUiState): fr.roadly.core.navigation.ManeuverSide? {
    if (state.showLanes || roundaboutModeVisible(state)) return null
    return when (state.situation.mode) {
        fr.roadly.core.navigation.GuidanceMode.EXIT, fr.roadly.core.navigation.GuidanceMode.FORK, fr.roadly.core.navigation.GuidanceMode.COMPLEX -> state.situation.side
        else -> null
    }
}

@Composable
internal fun SideHintRow(side: fr.roadly.core.navigation.ManeuverSide, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val right = side == fr.roadly.core.navigation.ManeuverSide.RIGHT
    GlassPanel(modifier, shape = RoadlyShapes.Chip, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon((if (right) ManeuverType.SLIGHT_RIGHT else ManeuverType.SLIGHT_LEFT).icon(), contentDescription = null, tint = c.routeActive, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (right) R.string.nav_side_right else R.string.nav_side_left), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
        }
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

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF070A18)
@Composable
private fun ThenRowNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { ThenRow(ThenUi(ManeuverType.TURN_RIGHT, "Rue Louis Plana")) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF3F5FA)
@Composable
private fun ThenRowDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { ThenRow(ThenUi(ManeuverType.ROUNDABOUT_EXIT, "Rue Médiane")) } }
