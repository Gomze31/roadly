package fr.roadly.feature.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Place
import fr.roadly.core.routing.StopOrdering

/** Arrêts intermédiaires : numéro, nom, monter / descendre / retirer ; puis « Ajouter un arrêt » et « Optimiser l'ordre ». */
@Composable
fun StopsList(
    stops: List<Place>,
    optimizing: Boolean,
    onAdd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onOptimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs)) {
        stops.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(c.brandSoft, RoadlyShapes.Pill), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.brandBright)
                }
                Spacer(Modifier.width(RoadlySpacing.s))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    s.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                SmallAction(Icons.Default.KeyboardArrowUp, stringResource(R.string.route_stop_up), enabled = i > 0) { onMove(i, i - 1) }
                SmallAction(Icons.Default.KeyboardArrowDown, stringResource(R.string.route_stop_down), enabled = i < stops.lastIndex) { onMove(i, i + 1) }
                SmallAction(Icons.Default.Close, stringResource(R.string.route_stop_remove)) { onRemove(i) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
            GlassChip(
                stringResource(if (stops.isEmpty()) R.string.route_add_stop else R.string.route_add_stop_n, stops.size, StopOrdering.MAX_STOPS),
                onClick = onAdd, icon = Icons.Default.AddLocationAlt, compact = true, enabled = stops.size < StopOrdering.MAX_STOPS,
            )
            if (stops.size >= 2) {
                if (optimizing) LoadingRow(stringResource(R.string.route_optimizing))
                else GlassChip(stringResource(R.string.route_optimize), onClick = onOptimize, icon = Icons.Default.AutoAwesome, compact = true)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    Box(
        Modifier.size(36.dp).alpha(if (enabled) 1f else 0.3f).clip(RoadlyShapes.Pill).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = c.textSecondary, modifier = Modifier.size(20.dp)) }
}
