package fr.roadly.feature.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.navigation.RoundaboutBranch
import fr.roadly.core.navigation.RoundaboutLayouts
import fr.roadly.core.navigation.RoundaboutLive
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RoundaboutProgressTracker
import kotlin.math.cos
import kotlin.math.sin

internal const val ROUNDABOUT_MODE_TAG = "roundabout_mode"

/** Ce que dit la ligne de décompte du mode rond-point ; [exits] ne sert qu'à [Kind.LET_PASS]. */
internal data class RoundaboutCountdown(val kind: Kind, val exits: Int = 0) {
    enum class Kind {
        /** « Laissez passer 2 sorties ». */
        LET_PASS,
        /** La prochaine sortie rencontrée est la bonne. */
        NEXT_EXIT,
        /** Sortie prise : confirmation. */
        DONE,
    }
}

/**
 * Décompte à afficher, ou null quand rien de fiable n'est connu (topologie absente ou incohérente avec Valhalla) :
 * dans ce cas le panneau montre la sortie et sa rue, sans prétendre compter.
 */
internal fun roundaboutCountdown(live: RoundaboutLive): RoundaboutCountdown? = when {
    live.phase == RoundaboutPhase.EXITED -> RoundaboutCountdown(RoundaboutCountdown.Kind.DONE)
    live.exitsRemaining == null -> null
    live.exitsRemaining == 0 -> RoundaboutCountdown(RoundaboutCountdown.Kind.NEXT_EXIT)
    else -> RoundaboutCountdown(RoundaboutCountdown.Kind.LET_PASS, live.exitsRemaining!!)
}

/**
 * Mode rond-point : schéma vivant sous le bandeau. L'entrée est toujours en bas, comme le conducteur la voit ; chaque sortie à
 * laisser passer est une branche courte qui s'éteint une fois derrière soi ; la sortie à prendre est la seule branche vive, avec
 * sa flèche. Un point suit le véhicule sur l'anneau. À droite, une seule phrase : le décompte (« 2 sorties à passer », puis
 * « Prochaine sortie »). Le rang et la rue de sortie sont déjà dans le bandeau juste au-dessus : ils ne sont pas répétés ici
 * (sans décompte fiable, le rang reprend sa place). Le conducteur n'a plus à compter.
 */
@Composable
internal fun RoundaboutModePanel(live: RoundaboutLive, modifier: Modifier = Modifier, diagramSize: Dp = 116.dp, distanceText: String? = null) {
    val c = LocalRoadlyColors.current
    val rank = live.layout.targetRank
    val rankLabel = when (rank) {
        null -> null
        1 -> stringResource(R.string.nav_roundabout_exit_first)
        else -> stringResource(R.string.nav_roundabout_exit_n, rank)
    }
    val countdown = roundaboutCountdown(live)
    val countdownText = when (countdown?.kind) {
        RoundaboutCountdown.Kind.LET_PASS -> pluralStringResource(R.plurals.nav_roundabout_let_pass, countdown.exits, countdown.exits)
        RoundaboutCountdown.Kind.NEXT_EXIT -> stringResource(if (live.phase == RoundaboutPhase.INSIDE) R.string.nav_roundabout_next_exit_now else R.string.nav_roundabout_next_exit)
        RoundaboutCountdown.Kind.DONE -> stringResource(R.string.nav_roundabout_done)
        null -> null
    }
    val street = live.layout.exitStreet
    val description = listOfNotNull(
        stringResource(R.string.nav_roundabout_desc_unknown), rankLabel, countdownText, street?.let { stringResource(R.string.nav_roundabout_toward, it) },
    ).joinToString(", ")
    // Le point du véhicule glisse entre deux positions GPS (1 Hz) ; lu au dessin seulement, sans recomposition.
    val target by animateFloatAsState(
        (live.vehicleAngleDeg ?: 0.0).toFloat(), tween(durationMillis = 900, easing = LinearEasing), label = "roundaboutVehicle",
    )
    val urgent = countdown?.kind == RoundaboutCountdown.Kind.NEXT_EXIT && live.phase == RoundaboutPhase.INSIDE
    val done = countdown?.kind == RoundaboutCountdown.Kind.DONE
    GlassPanel(modifier.testTag(ROUNDABOUT_MODE_TAG).clearAndSetSemantics { contentDescription = description }, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(diagramSize), contentAlignment = Alignment.Center) {
                val colors = RoundaboutModeColors(
                    ring = c.textSecondary.copy(alpha = 0.26f), ahead = if (done) c.success else c.routeActive, behind = c.textSecondary.copy(alpha = 0.55f),
                    otherExit = c.textSecondary.copy(alpha = 0.7f), passedExit = c.textSecondary.copy(alpha = 0.22f), vehicle = c.textPrimary, vehicleHalo = c.brand,
                )
                Canvas(Modifier.size(diagramSize)) { drawRoundaboutMode(live, { target.toDouble() }, colors) }
                if (rank != null) {
                    Text(
                        rank.toString(), modifier = Modifier.offset(y = -(diagramSize * RoundaboutModeDiagram.CENTER_LIFT)),
                        style = NavDigits.copy(fontSize = 30.sp, lineHeight = 34.sp), color = c.textPrimary,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (countdownText != null) {
                    val bg = when { done -> c.success; urgent -> c.accent; else -> c.brandSoft }
                    val fg = when { done -> c.background; urgent -> c.onAccent; else -> c.textPrimary }
                    Box(Modifier.background(bg, RoadlyShapes.Chip).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(countdownText, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp), color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else if (rankLabel != null) {
                    Text(rankLabel, style = NavDigits.copy(fontSize = 26.sp, lineHeight = 30.sp, textAlign = TextAlign.Start), color = c.textPrimary, maxLines = 1)
                }
                // Position douteuse : le décompte affiché est le dernier sûr, et l'écran le dit plutôt que de faire semblant.
                if (live.confidence == fr.roadly.core.navigation.RoundaboutConfidence.LOW && live.phase == RoundaboutPhase.INSIDE) {
                    Text(stringResource(R.string.nav_roundabout_unsure), style = MaterialTheme.typography.labelLarge, color = c.warning, maxLines = 1)
                }
                if (distanceText != null && live.phase == RoundaboutPhase.APPROACH) {
                    Text(stringResource(R.string.nav_roundabout_in, distanceText), style = MaterialTheme.typography.titleMedium, color = c.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

internal class RoundaboutModeColors(
    val ring: Color, val ahead: Color, val behind: Color, val otherExit: Color, val passedExit: Color, val vehicle: Color, val vehicleHalo: Color,
)

/** Géométrie du schéma vivant (Kotlin pur, testée) : 0° = bas (entrée), les angles croissent dans le sens de circulation (antihoraire). */
internal object RoundaboutModeDiagram {
    const val STROKE = 0.08f
    const val RING = 0.235f
    const val CENTER_LIFT = 0.02f
    /** Longueur d'une branche à laisser passer, au-delà de l'anneau (part du côté). */
    const val STUB = 0.11f
    const val ENTRY = 0.2f
    const val EDGE_MARGIN = 0.03f

    fun center(size: Size): Offset = Offset(size.width / 2f, size.height / 2f - size.minDimension * CENTER_LIFT)

    fun direction(angleDeg: Double): Offset {
        val theta = Math.toRadians(90.0 - angleDeg)
        return Offset(cos(theta).toFloat(), sin(theta).toFloat())
    }

    fun onRing(size: Size, angleDeg: Double): Offset = center(size) + direction(angleDeg) * (size.minDimension * RING)

    /** Bout libre d'une branche à laisser passer. */
    fun stubEnd(size: Size, angleDeg: Double): Offset = center(size) + direction(angleDeg) * (size.minDimension * (RING + STUB))

    /** Pointe de la sortie à prendre : jusqu'au bord du schéma (moins la marge), jamais au-delà de la moitié du côté. */
    fun targetTip(size: Size, angleDeg: Double): Offset {
        val s = size.minDimension
        val c = center(size)
        val d = direction(angleDeg)
        var t = s * 0.5f
        val m = s * EDGE_MARGIN
        if (d.x > 1e-6f) t = minOf(t, (size.width - m - c.x) / d.x)
        if (d.x < -1e-6f) t = minOf(t, (m - c.x) / d.x)
        if (d.y > 1e-6f) t = minOf(t, (size.height - m - c.y) / d.y)
        if (d.y < -1e-6f) t = minOf(t, (m - c.y) / d.y)
        return c + d * t
    }

    /** Position du véhicule : sur la branche d'entrée à l'approche, sur l'anneau ensuite, sur la branche de sortie une fois sorti. */
    fun vehicle(size: Size, live: RoundaboutLive, angleDeg: Double): Offset = when (live.phase) {
        RoundaboutPhase.APPROACH -> onRing(size, 0.0) + Offset(0f, size.minDimension * ENTRY * 0.7f)
        RoundaboutPhase.INSIDE -> onRing(size, angleDeg)
        RoundaboutPhase.EXITED -> {
            val sweep = live.layout.sweepDeg ?: angleDeg
            val from = onRing(size, sweep)
            from + (targetTip(size, sweep) - from) * 0.45f
        }
    }
}

private fun DrawScope.drawRoundaboutMode(live: RoundaboutLive, vehicleAngle: () -> Double, colors: RoundaboutModeColors) {
    val s = size.minDimension
    val w = s * RoundaboutModeDiagram.STROKE
    val r = s * RoundaboutModeDiagram.RING
    val c = RoundaboutModeDiagram.center(size)
    val sweep = live.layout.sweepDeg
    val angle = when (live.phase) {
        RoundaboutPhase.APPROACH -> 0.0
        RoundaboutPhase.INSIDE -> vehicleAngle().coerceIn(0.0, sweep ?: 360.0)
        RoundaboutPhase.EXITED -> sweep ?: 0.0
    }
    val inside = live.phase != RoundaboutPhase.APPROACH
    val exited = live.phase == RoundaboutPhase.EXITED
    drawCircle(colors.ring, radius = r, center = c, style = Stroke(w))
    // Branches à laisser passer : courtes, neutres ; celles déjà derrière soi s'éteignent.
    for (b in live.layout.branches) {
        if (b.isTarget) continue
        val passed = b.rank <= live.exitsPassed
        val from = c + RoundaboutModeDiagram.direction(b.angleDeg) * (r + w / 2f)
        drawLine(if (passed) colors.passedExit else colors.otherExit, from, RoundaboutModeDiagram.stubEnd(size, b.angleDeg), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
    }
    val entryOnRing = RoundaboutModeDiagram.onRing(size, 0.0)
    val entryEnd = entryOnRing + Offset(0f, s * RoundaboutModeDiagram.ENTRY)
    drawLine(if (inside && !exited) colors.behind else colors.ahead, entryEnd, entryOnRing, strokeWidth = w, cap = StrokeCap.Round)
    if (sweep != null) {
        val box = Offset(c.x - r, c.y - r)
        val arc = Size(2 * r, 2 * r)
        // Trajet restant en couleur vive, trajet déjà fait en neutre : l'œil suit ce qu'il reste à faire.
        // Sortie prise : tout le trajet se colore d'un seul tenant, c'est la confirmation.
        if (angle > 0.5) drawArc(if (exited) colors.ahead else colors.behind, 90f, -angle.toFloat(), false, box, arc, style = Stroke(w, cap = StrokeCap.Round))
        if (sweep - angle > 0.5) drawArc(colors.ahead, (90.0 - angle).toFloat(), -(sweep - angle).toFloat(), false, box, arc, style = Stroke(w, cap = StrokeCap.Round))
        val target = live.layout.branches.lastOrNull()?.takeIf { it.isTarget }
        if (target != null) {
            val d = RoundaboutModeDiagram.direction(sweep)
            val tip = RoundaboutModeDiagram.targetTip(size, sweep)
            val head = w * 1.7f
            val base = tip - d * head
            drawLine(colors.ahead, c + d * r, base, strokeWidth = w, cap = StrokeCap.Round)
            val n = Offset(-d.y, d.x) * (w * 1.05f)
            drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo((base + n).x, (base + n).y); lineTo((base - n).x, (base - n).y); close() }, colors.ahead)
        }
    }
    if (exited) return
    val v = RoundaboutModeDiagram.vehicle(size, live, angle)
    drawCircle(colors.vehicleHalo, radius = w * 0.95f, center = v)
    drawCircle(colors.vehicle, radius = w * 0.58f, center = v)
}

/** Rond-point de démonstration pour les aperçus et les tests : [exits] sorties régulièrement réparties, la dernière est la cible. */
internal fun previewRoundaboutLive(exits: Int, passed: Int, phase: RoundaboutPhase, street: String? = "Avenue des Champs-Élysées", counted: Boolean = true): RoundaboutLive {
    val sweep = (exits * 300.0 / (exits + 1)).coerceIn(60.0, 320.0)
    val info = previewRoundabout(sweep, exits, street)
    val base = RoundaboutLayouts.build(info, null)
    val branches = (1..exits).map { k -> RoundaboutBranch(k, base.ringLengthM * k / exits, sweep * k / exits, isTarget = k == exits) }
    val layout = if (counted) base.copy(branches = branches, counted = true, targetRank = exits) else base
    val tracker = RoundaboutProgressTracker()
    return when (phase) {
        RoundaboutPhase.APPROACH -> tracker.update("preview", 0, layout, inside = false, distanceToNextM = 120.0)
        RoundaboutPhase.INSIDE -> {
            val offset = if (passed <= 0) layout.ringLengthM * 0.08 else layout.ringLengthM * (passed + 0.35) / exits
            tracker.update("preview", 0, layout, inside = true, distanceToNextM = (layout.ringLengthM - offset).coerceAtLeast(0.0))
        }
        RoundaboutPhase.EXITED -> tracker.exited(layout, 15.0)
    }
}

@Preview(name = "Approche, 3e sortie — Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun RoundaboutModeApproachPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutModePanel(previewRoundaboutLive(3, 0, RoundaboutPhase.APPROACH), distanceText = "120 m") }
}

@Preview(name = "Dans l'anneau, 1 passée sur 3 — Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun RoundaboutModeInsidePreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutModePanel(previewRoundaboutLive(4, 1, RoundaboutPhase.INSIDE)) }
}

@Preview(name = "Prochaine sortie — Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 380)
@Composable
private fun RoundaboutModeNextExitPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) { RoundaboutModePanel(previewRoundaboutLive(3, 2, RoundaboutPhase.INSIDE, street = "D 916")) }
}

@Preview(name = "Sortie prise / sans décompte — Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun RoundaboutModeExitedPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundaboutModePanel(previewRoundaboutLive(2, 1, RoundaboutPhase.EXITED))
            RoundaboutModePanel(previewRoundaboutLive(5, 0, RoundaboutPhase.INSIDE, street = null, counted = false))
        }
    }
}
