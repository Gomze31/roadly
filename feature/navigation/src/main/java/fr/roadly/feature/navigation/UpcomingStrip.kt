package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.ManeuverIcon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Bandeau « à venir » : jusqu'à 3 cartes (icône, distance, rue). Pas de trafic ni de services (Phase 3). */
@Composable
fun UpcomingStrip(items: List<UpcomingUi>, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        items.forEach { u ->
            GlassPanel(Modifier.weight(1f), contentPadding = 10.dp) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ManeuverIcon(u.type, Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(u.distanceText, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
                    }
                    Text(u.street.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
