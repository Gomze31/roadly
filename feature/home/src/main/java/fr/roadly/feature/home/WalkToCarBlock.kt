package fr.roadly.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/**
 * S4 — rejoindre la voiture garée à pied : bouton de calcul, puis durée et distance du trajet piéton (tracé sur la carte).
 * Pas de guidage pas à pas : le guidage de Roadly est conçu pour la conduite (radars, vue des carrefours, mémorisation du stationnement).
 */
@Composable
internal fun WalkToCarBlock(walk: WalkToCar?, onRequest: () -> Unit, onClear: () -> Unit) {
    val c = LocalRoadlyColors.current
    when (walk) {
        null -> GlassButton(
            stringResource(R.string.home_parked_walk), onClick = onRequest, style = GlassButtonStyle.Brand,
            icon = Icons.AutoMirrored.Filled.DirectionsWalk, modifier = Modifier.fillMaxWidth(),
        )
        WalkToCar.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.brand, strokeWidth = 2.dp)
            Text(stringResource(R.string.home_parked_walk_loading), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        WalkToCar.Nearby -> Text(stringResource(R.string.home_parked_walk_nearby), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        is WalkToCar.Ready -> Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(
                stringResource(R.string.home_parked_walk_result, Formatters.duration(walk.route.durationSeconds), Formatters.distance(walk.route.distanceMeters)),
                style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
            )
            GlassButton(stringResource(R.string.home_parked_walk_hide), onClick = onClear, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
        is WalkToCar.Failed -> Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(walk.error.userMessage(), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            GlassButton(stringResource(R.string.home_parked_walk_retry), onClick = onRequest, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
        }
    }
}
