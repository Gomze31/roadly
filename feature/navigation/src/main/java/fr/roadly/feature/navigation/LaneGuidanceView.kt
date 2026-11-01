package fr.roadly.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection

/**
 * D4 : voies à l'approche de la manœuvre. Une voie conseillée est en marque, sur fond doux ; une voie possible est en secondaire ;
 * une voie interdite est estompée. Une voie à plusieurs directions montre toutes ses flèches superposées.
 * Le composant est absent sans données (LIMITE : FOSSGIS public n'en renvoie pas).
 * Beaucoup de voies : la cellule se partage la largeur disponible ([laneCellDp], 24 dp au moins) et les flèches suivent la même échelle.
 */
@Composable
fun LaneGuidanceView(lanes: List<Lane>, emphasized: Boolean, modifier: Modifier = Modifier) {
    if (lanes.isEmpty()) return
    val c = LocalRoadlyColors.current
    val states = remember(lanes) { laneStates(lanes) }
    val maxCell = if (emphasized) LANE_MAX_CELL_DP else 36f
    val advised = states.withIndex().filter { it.value == LaneState.ACTIVE }.joinToString(", ") { (it.index + 1).toString() }
    val description = if (advised.isEmpty()) stringResource(R.string.nav_lanes) else stringResource(R.string.nav_lanes_desc, advised, lanes.size)
    GlassPanel(modifier.semantics { contentDescription = description }, contentPadding = 8.dp) {
        BoxWithConstraints {
            val available = if (constraints.hasBoundedWidth) maxWidth.value else Float.MAX_VALUE
            val cellDp = laneCellDp(available, lanes.size, maxCell)
            val scale = cellDp / maxCell
            val cell = cellDp.dp
            val icon = (if (emphasized) 32.dp else 26.dp) * scale
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                lanes.forEachIndexed { i, lane ->
                    if (i > 0) Box(Modifier.width(1.dp).height(cell).background(c.glassBorder))
                    val state = states[i]
                    val tint = when (state) {
                        LaneState.ACTIVE -> c.brandBright
                        LaneState.VALID -> c.textSecondary
                        LaneState.INVALID -> c.textTertiary.copy(alpha = 0.5f)
                    }
                    Box(
                        Modifier.size(width = cell, height = cell + 8.dp)
                            .then(if (state == LaneState.ACTIVE) Modifier.background(c.brandSoft, RoadlyShapes.Tile) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        lane.directions.sortedBy { it.ordinal }.ifEmpty { listOf(LaneDirection.STRAIGHT) }.forEach { d ->
                            Icon(d.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(if (state == LaneState.ACTIVE) icon + 4.dp * scale else icon))
                        }
                    }
                }
            }
        }
    }
}

/** Largeur maximale d'une cellule de voie (voies mises en avant). */
internal const val LANE_MAX_CELL_DP = 44f

/** Largeur minimale d'une cellule de voie : en deçà, la flèche n'est plus lisible en conduite. */
internal const val LANE_MIN_CELL_DP = 24f

/** Écart entre deux cellules : séparateur de 1 dp et `spacedBy(4.dp)` de part et d'autre. */
internal const val LANE_GAP_DP = 9f

/** D4 : largeur (dp) d'une cellule pour [n] voies sur [availableDp] : min([maxDp], part de la largeur hors écarts), jamais sous [LANE_MIN_CELL_DP]. */
internal fun laneCellDp(availableDp: Float, n: Int, maxDp: Float = LANE_MAX_CELL_DP): Float {
    if (n <= 0) return maxDp
    val shared = (availableDp - (n - 1) * LANE_GAP_DP) / n
    return shared.coerceAtMost(maxDp).coerceAtLeast(LANE_MIN_CELL_DP)
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

private val sampleLanes = listOf(
    Lane(setOf(LaneDirection.LEFT), valid = false),
    Lane(setOf(LaneDirection.STRAIGHT), valid = true),
    Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, active = true),
    Lane(setOf(LaneDirection.RIGHT), valid = true, active = true),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun LaneGuidanceNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { LaneGuidanceView(sampleLanes, emphasized = true) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun LaneGuidanceDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { LaneGuidanceView(sampleLanes, emphasized = false) } }
