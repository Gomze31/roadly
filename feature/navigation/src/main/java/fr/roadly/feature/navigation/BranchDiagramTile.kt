package fr.roadly.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.navigation.BranchDiagram
import kotlin.math.cos
import kotlin.math.sin

internal const val BRANCH_DIAGRAM_TAG = "branch_diagram"

/**
 * Schéma de bifurcation : la chaussée arrive par le bas, se sépare au nœud ; la branche à prendre est vive et fléchée, les autres
 * sont neutres. Ordre et côté réels, ouverture des angles schématique (voir `BranchDiagrams`). Une bretelle est dessinée plus fine.
 */
@Composable
internal fun BranchDiagramTile(diagram: BranchDiagram, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val c = LocalRoadlyColors.current
    val others = diagram.branches.size - 1
    val description = pluralStringResource(R.plurals.nav_branch_diagram_desc, others, diagram.routeRank + 1, diagram.branches.size)
    val route = c.routeActive
    val other = c.textSecondary.copy(alpha = 0.55f)
    GlassPanel(modifier.testTag(BRANCH_DIAGRAM_TAG).clearAndSetSemantics { contentDescription = description }, contentPadding = 8.dp) {
        Canvas(Modifier.size(size)) { drawBranches(diagram, route, other) }
    }
}

/** Géométrie du schéma (Kotlin pur, testée) : nœud aux deux tiers de la hauteur, branches vers le haut. */
internal object BranchDiagramGeometry {
    const val STROKE = 0.11f
    const val NODE_Y = 0.62f
    const val EDGE_MARGIN = 0.06f

    fun node(size: Size): Offset = Offset(size.width / 2f, size.height * NODE_Y)

    /** Direction à l'écran pour un angle (°, 0 = vers le haut, > 0 = vers la droite). */
    fun direction(angleDeg: Double): Offset {
        val a = Math.toRadians(angleDeg)
        return Offset(sin(a).toFloat(), -cos(a).toFloat())
    }

    /** Bout d'une branche : jusqu'au bord du schéma moins la marge. */
    fun tip(size: Size, angleDeg: Double): Offset {
        val n = node(size)
        val d = direction(angleDeg)
        val m = size.minDimension * EDGE_MARGIN
        var t = Float.MAX_VALUE
        if (d.x > 1e-6f) t = minOf(t, (size.width - m - n.x) / d.x)
        if (d.x < -1e-6f) t = minOf(t, (m - n.x) / d.x)
        if (d.y > 1e-6f) t = minOf(t, (size.height - m - n.y) / d.y)
        if (d.y < -1e-6f) t = minOf(t, (m - n.y) / d.y)
        return n + d * t
    }
}

private fun DrawScope.drawBranches(diagram: BranchDiagram, routeColor: Color, otherColor: Color) {
    val w = size.minDimension * BranchDiagramGeometry.STROKE
    val node = BranchDiagramGeometry.node(size)
    val bottom = Offset(node.x, size.height - size.minDimension * BranchDiagramGeometry.EDGE_MARGIN)
    // Branches à éviter d'abord, pour que la branche à prendre passe par-dessus au nœud.
    for (b in diagram.branches) {
        if (b.isRoute) continue
        drawLine(otherColor, node, BranchDiagramGeometry.tip(size, b.drawnAngleDeg), strokeWidth = if (b.isRamp) w * 0.7f else w, cap = StrokeCap.Round)
    }
    val r = diagram.routeBranch
    val d = BranchDiagramGeometry.direction(r.drawnAngleDeg)
    val tip = BranchDiagramGeometry.tip(size, r.drawnAngleDeg)
    val head = w * 1.5f
    val base = tip - d * head
    drawPath(
        Path().apply { moveTo(bottom.x, bottom.y); lineTo(node.x, node.y); lineTo(base.x, base.y) },
        routeColor, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    val n = Offset(-d.y, d.x) * (w * 1.05f)
    drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo((base + n).x, (base + n).y); lineTo((base - n).x, (base - n).y); close() }, routeColor)
}
