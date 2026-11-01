package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

/** D1 : feuille « Aperçu de la manœuvre ». Son contenu suit l'état en direct ; la carte et le guidage continuent derrière. */
@Composable
fun ManeuverPreviewSheet(state: NavigationUiState, onDismiss: () -> Unit) {
    if (state.banner == null) return
    GlassBottomSheet(onDismiss = onDismiss) { ManeuverPreviewContent(state, onDismiss) }
}

/**
 * Contenu de l'aperçu :
 * - grande icône ou schéma de rond-point ;
 * - distance, instruction, rue ;
 * - panneau de direction, ou voies ;
 * - les 3 manœuvres suivantes (avec leur panneau « Sortie suivante » s'il existe) ;
 * - « Fermer ».
 */
@Composable
internal fun ManeuverPreviewContent(state: NavigationUiState, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    val banner = state.banner ?: return
    Column(
        Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
    ) {
        OverlineText(stringResource(R.string.nav_preview_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val roundabout = state.roundabout
            if (roundabout != null) {
                RoundaboutManeuverView(roundabout, banner.roundaboutExit ?: roundabout.exitNumber, compact = false)
            } else {
                IconTile(banner.type.icon(), style = IconTileStyle.BrandFilled, size = 96.dp)
            }
            Spacer(Modifier.width(RoadlySpacing.m))
            Column(Modifier.weight(1f)) {
                Text(banner.distanceText, style = NavDigits.copy(fontSize = 34.sp, lineHeight = 38.sp, textAlign = TextAlign.Start), color = c.textPrimary)
                Text(banner.instruction, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                banner.street?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }
        }
        val sign = state.sign
        if (sign != null) {
            ExitSignPanel(banner.type, sign.exitNumber, sign.toward, sign.branch, state.lanes, banner.distanceText, isNext = true, Modifier.fillMaxWidth())
        } else {
            LaneGuidanceView(state.lanes, emphasized = true)
        }
        if (state.upcoming.isNotEmpty()) {
            Text(stringResource(R.string.nav_preview_next), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            state.upcoming.forEach { u ->
                ListRow(title = u.street ?: u.instruction ?: stringResource(R.string.nav_horizon_maneuver), icon = u.type.icon(), value = u.distanceText, divider = false)
                u.sign?.let { s -> ExitSignPanel(u.type, s.exitNumber, s.toward, s.branch, emptyList(), u.distanceText, isNext = false, Modifier.fillMaxWidth()) }
            }
        }
        GlassButton(stringResource(R.string.nav_close), onClick = onDismiss, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

private val sampleState = NavigationUiState(
    phase = NavigationPhase.GUIDING,
    banner = BannerUi(ManeuverType.ROUNDABOUT_ENTER, "150 m", "Au rond-point, prenez la 2e sortie.", "Avenue des Champs-Élysées", null, 2),
    roundabout = previewRoundabout(180.0, 2, "Avenue des Champs-Élysées"),
    upcoming = listOf(
        UpcomingUi(ManeuverType.ROUNDABOUT_EXIT, "200 m", "Avenue des Champs-Élysées", 200.0),
        UpcomingUi(ManeuverType.EXIT_RIGHT, "2,4 km", null, 2_400.0, "Prenez la sortie 19.", ExitSignUi("19", "Balma", null)),
    ),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF1F1829, widthDp = 420)
@Composable
private fun ManeuverPreviewNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { ManeuverPreviewContent(sampleState, onDismiss = {}) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 420)
@Composable
private fun ManeuverPreviewDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { ManeuverPreviewContent(sampleState, onDismiss = {}) } }
