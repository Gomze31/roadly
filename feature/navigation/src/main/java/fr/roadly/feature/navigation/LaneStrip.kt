package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection

/** Voies sous la manœuvre courante (si Valhalla les fournit) : flèches, voie valide en accent. */
@Composable
fun LaneStrip(lanes: List<Lane>, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val c = LocalRoadlyColors.current
    val cell = if (emphasized) 40.dp else 32.dp
    val icon = if (emphasized) 34.dp else 28.dp
    val description = stringResource(R.string.nav_lanes)
    GlassPanel(modifier.semantics { contentDescription = description }, contentPadding = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            lanes.forEach { lane ->
                val tint = if (lane.valid) c.brandBright else if (emphasized) c.textTertiary.copy(alpha = 0.6f) else c.textTertiary
                Box(Modifier.size(width = cell, height = cell + 8.dp), contentAlignment = Alignment.Center) {
                    lane.directions.sortedBy { it.ordinal }.ifEmpty { listOf(LaneDirection.STRAIGHT) }.forEach { d ->
                        Icon(d.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(icon))
                    }
                }
            }
        }
    }
}

internal fun LaneDirection.icon(): ImageVector = when (this) {
    LaneDirection.LEFT -> Icons.Default.TurnLeft
    LaneDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    LaneDirection.STRAIGHT -> Icons.Default.Straight
    LaneDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    LaneDirection.RIGHT -> Icons.Default.TurnRight
    LaneDirection.UTURN -> Icons.Default.UTurnLeft
    LaneDirection.MERGE_LEFT, LaneDirection.MERGE_RIGHT -> Icons.Default.Merge
}
