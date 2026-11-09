package fr.roadly.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.ButtonLabel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits

/**
 * Guidage en image dans l'image (fenêtre de ~ 150 dp de large) : pictogramme de manœuvre, distance, rue (ou « 2e sortie · rue »)
 * et heure d'arrivée. Pas d'interaction : toucher la fenêtre ramène l'écran complet (géré par Android).
 */
@Composable
internal fun PipGuidance(state: NavigationUiState, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val banner = state.banner
    Box(modifier.background(Brush.verticalGradient(listOf(c.surfaceElevated, c.background))).padding(8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (banner == null) {
                Text(stringResource(R.string.nav_loading), style = ButtonLabel.copy(fontSize = 13.sp), color = c.textPrimary, textAlign = TextAlign.Center)
                return@Column
            }
            val roundabout = state.roundabout
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (roundabout != null) RoundaboutManeuverView(roundabout, banner.roundaboutExit ?: roundabout.exitNumber, compact = true)
                else Icon(banner.type.icon(), contentDescription = null, tint = c.brand, modifier = Modifier.size(44.dp))
            }
            Text(banner.distanceText, style = NavDigits.copy(fontSize = 26.sp, lineHeight = 28.sp), color = c.brand, maxLines = 1)
            val line = listOfNotNull(
                banner.roundaboutExit?.let { if (it == 1) stringResource(R.string.nav_roundabout_exit_first) else stringResource(R.string.nav_roundabout_exit_n, it) },
                bannerStreet(banner, roundabout),
            ).joinToString(" · ").ifEmpty { banner.instruction }
            Text(line, style = ButtonLabel.copy(fontSize = 12.sp, lineHeight = 15.sp), color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (state.etaClock.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.nav_pip_eta, state.etaClock), style = ButtonLabel.copy(fontSize = 11.sp), color = c.textSecondary, maxLines = 1)
            }
        }
    }
}
