package fr.roadly.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Place

@Composable
fun PlaceSheet(
    place: Place,
    isFavorite: Boolean,
    distanceMeters: Double?,
    onDismiss: () -> Unit,
    onRoute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetHome: () -> Unit,
    onSetWork: () -> Unit,
) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            Text(place.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            if (place.address != null) Text(place.address!!, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (distanceMeters != null) Text(stringResource(R.string.place_distance, Formatters.distance(distanceMeters)), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(RoadlySpacing.m))
            GlassButton(stringResource(R.string.place_route), onClick = onRoute, icon = Icons.Default.Directions, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(RoadlySpacing.s))
            Row {
                GlassChip(stringResource(if (isFavorite) R.string.place_favorite_remove else R.string.place_favorite_add), onClick = onToggleFavorite, icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, selected = isFavorite)
            }
            Spacer(Modifier.height(RoadlySpacing.s))
            Row {
                GlassChip(stringResource(R.string.place_set_home), onClick = onSetHome, icon = Icons.Default.Home)
                Spacer(Modifier.width(RoadlySpacing.s))
                GlassChip(stringResource(R.string.place_set_work), onClick = onSetWork, icon = Icons.Default.Work)
            }
        }
    }
}
