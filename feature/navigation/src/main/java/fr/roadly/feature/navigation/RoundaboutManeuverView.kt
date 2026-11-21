package fr.roadly.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.navigation.RoundaboutGeometry
import fr.roadly.core.navigation.RoundaboutInfo
import kotlin.math.cos
import kotlin.math.sin

private val COMPACT_SIZE = 54.dp
private val LARGE_SIZE = 160.dp

/**
 * D2 : schéma du rond-point visé. Il montre :
 * - l'anneau ;
 * - la branche d'entrée en bas ;
 * - l'arc parcouru dans le sens de circulation (antihoraire), jusqu'à la sortie placée par l'arc réel de l'anneau ([RoundaboutGeometry]) ;
 * - une flèche ;
 * - le numéro de sortie au centre.
 * [compact] = 48 dp sur fond marque (bandeau) ; sinon 160 dp + rue de sortie (aperçu).
 * Sans manœuvre de sortie (arrivée dans l'anneau), pas de branche : la flèche suit l'anneau.
 * LIMITE : les autres sorties ne sont jamais dessinées (absentes de `/route`) ; circulation à gauche non gérée.
 */
@Composable
fun RoundaboutManeuverView(info: RoundaboutInfo, exitNumber: Int?, compact: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val sweep = remember(info) { RoundaboutGeometry.diagramSweepDeg(info) }
    val side = if (compact) COMPACT_SIZE else LARGE_SIZE
    val spec = if (compact) RoundaboutDiagramSpec.Compact else RoundaboutDiagramSpec.Large
    // Bandeau : schéma posé directement sur le panneau (anneau gris, trajet en couleur de marque), sans tuile.
    val ring = if (compact) c.textSecondary.copy(alpha = 0.55f) else c.brandSoft
    val path = if (compact) c.brand else c.brandBright
    val numberColor = c.textPrimary
    val label = when (exitNumber) {
        null -> null
        1 -> stringResource(R.string.nav_roundabout_exit_first)
        else -> stringResource(R.string.nav_roundabout_exit_n, exitNumber)
    }
    val base = label?.let { stringResource(R.string.nav_roundabout_desc, it) } ?: stringResource(R.string.nav_roundabout_desc_unknown)
    // La rue n'est lue que si elle est affichée (variante grande) : une seule annonce, sans doublon du numéro ni de la rue.
    val street = if (compact) null else info.exitStreet
    val description = street?.let { stringResource(R.string.nav_roundabout_desc_street, base, it) } ?: base
    Column(modifier.clearAndSetSemantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(side)) { drawRoundabout(sweep, info.exitBearingDeg != null, ring, path, spec) }
            if (exitNumber != null) {
                Text(
                    exitNumber.toString(),
                    modifier = Modifier.offset(y = -(side * RoundaboutDiagram.CENTER_LIFT)),
                    style = NavDigits.copy(fontSize = if (compact) 17.sp else 30.sp, lineHeight = if (compact) 19.sp else 34.sp),
                    color = numberColor,
                )
            }
        }
        street?.let {
            Text(
                it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.width(side).padding(top = 4.dp),
            )
        }
    }
}

/**
 * Proportions d'une variante du schéma : trait et anneau en part du côté, flèche en part du trait.
 * La variante compacte a un trait plus fin et une flèche plus courte : à 340°, la sortie n'est qu'à 20° de l'entrée,
 * et une flèche plus large toucherait la branche d'entrée (vérifié par `RoundaboutDiagramTest`).
 */
internal class RoundaboutDiagramSpec(
    /** Épaisseur du trait, en part du côté. */
    val strokeRatio: Float,
    /** Rayon de l'anneau (axe du trait), en part du côté. */
    val ringRatio: Float,
    /** Longueur de la flèche, en épaisseurs de trait. */
    val arrowLength: Float,
    /** Demi-largeur de la base de la flèche, en épaisseurs de trait. */
    val arrowHalfWidth: Float,
) {
    companion object {
        val Compact = RoundaboutDiagramSpec(strokeRatio = 0.08f, ringRatio = 0.21f, arrowLength = 1.5f, arrowHalfWidth = 0.9f)
        val Large = RoundaboutDiagramSpec(strokeRatio = 0.07f, ringRatio = 0.22f, arrowLength = 2.0f, arrowHalfWidth = 1.1f)
    }
}

/** Pointe de flèche : sommet et deux coins de la base. */
internal data class ArrowHead(val tip: Offset, val left: Offset, val right: Offset)

/** Branche de sortie : trait de l'anneau ([start]) à la base de la flèche ([base]), puis la flèche. */
internal data class ExitBranch(val start: Offset, val base: Offset, val head: ArrowHead)

/** Géométrie du schéma (Kotlin pur, testée) : 90° = bas (entrée) ; l'arc tourne dans le sens antihoraire à l'écran ; sortie à 90° − arc. */
internal object RoundaboutDiagram {
    /** Longueur de la branche d'entrée, en part du côté. */
    const val ENTRY_RATIO = 0.20f
    /** Centre de l'anneau remonté (part du côté) pour loger la branche d'entrée en bas. */
    const val CENTER_LIFT = 0.04f
    /** Marge entre la pointe de la flèche et le bord du schéma, en part du côté. */
    const val EDGE_MARGIN = 0.03f
    /** Distance maximale centre → pointe, en part du côté : la pointe reste dans les coins arrondis du fond compact. */
    const val MAX_REACH = 0.5f

    fun center(size: Size): Offset = Offset(size.width / 2f, size.height / 2f - size.minDimension * CENTER_LIFT)

    /** Branche d'entrée : du bas (bout libre) au point d'arrivée sur l'anneau. */
    fun entry(size: Size, spec: RoundaboutDiagramSpec): Pair<Offset, Offset> {
        val s = size.minDimension
        val c = center(size)
        val onRing = Offset(c.x, c.y + s * spec.ringRatio)
        return Offset(onRing.x, onRing.y + s * ENTRY_RATIO) to onRing
    }

    /**
     * Branche de sortie pour un arc de [sweepDeg] : elle va jusqu'au bord du schéma (moins [EDGE_MARGIN], au plus [MAX_REACH]).
     * La base de la flèche reste hors de l'anneau, pour que le trait de la branche reste visible.
     */
    fun exitBranch(sweepDeg: Double, size: Size, spec: RoundaboutDiagramSpec): ExitBranch {
        val s = size.minDimension
        val stroke = s * spec.strokeRatio
        val c = center(size)
        val radial = direction(sweepDeg)
        val reach = minOf(s * MAX_REACH, edgeReach(c, radial, size, s * EDGE_MARGIN))
        val length = stroke * spec.arrowLength
        val tip = c + radial * reach
        return ExitBranch(
            start = c + radial * (s * spec.ringRatio),
            base = tip - radial * length,
            head = arrowHead(tip, radial, length, stroke * spec.arrowHalfWidth),
        )
    }

    /** Flèche posée sur l'anneau, sans manœuvre de sortie : elle suit le sens de circulation (angle décroissant à l'écran). */
    fun ringArrow(sweepDeg: Double, size: Size, spec: RoundaboutDiagramSpec): ArrowHead {
        val s = size.minDimension
        val stroke = s * spec.strokeRatio
        val radial = direction(sweepDeg)
        val tangent = Offset(radial.y, -radial.x)
        val onRing = center(size) + radial * (s * spec.ringRatio)
        return arrowHead(onRing + tangent * (stroke * 1.4f), tangent, stroke * spec.arrowLength, stroke * spec.arrowHalfWidth)
    }

    private fun direction(sweepDeg: Double): Offset {
        val theta = Math.toRadians(90.0 - sweepDeg)
        return Offset(cos(theta).toFloat(), sin(theta).toFloat())
    }

    /** Distance de [from] au bord du rectangle réduit de [margin], le long de [dir] (unitaire). */
    private fun edgeReach(from: Offset, dir: Offset, size: Size, margin: Float): Float {
        var t = Float.MAX_VALUE
        if (dir.x > 1e-6f) t = minOf(t, (size.width - margin - from.x) / dir.x)
        if (dir.x < -1e-6f) t = minOf(t, (margin - from.x) / dir.x)
        if (dir.y > 1e-6f) t = minOf(t, (size.height - margin - from.y) / dir.y)
        if (dir.y < -1e-6f) t = minOf(t, (margin - from.y) / dir.y)
        return t
    }

    private fun arrowHead(tip: Offset, dir: Offset, length: Float, halfWidth: Float): ArrowHead {
        val normal = Offset(-dir.y, dir.x)
        val base = tip - dir * length
        return ArrowHead(tip, base + normal * halfWidth, base - normal * halfWidth)
    }
}

private fun DrawScope.drawRoundabout(sweep: Double?, exitBranch: Boolean, ringColor: Color, pathColor: Color, spec: RoundaboutDiagramSpec) {
    val s = size.minDimension
    val stroke = s * spec.strokeRatio
    val r = s * spec.ringRatio
    val center = RoundaboutDiagram.center(size)
    drawCircle(ringColor, radius = r, center = center, style = Stroke(stroke))
    val (entryStart, entryEnd) = RoundaboutDiagram.entry(size, spec)
    drawLine(pathColor, start = entryStart, end = entryEnd, strokeWidth = stroke, cap = StrokeCap.Round)
    if (sweep == null) return
    drawArc(
        pathColor, startAngle = 90f, sweepAngle = -sweep.toFloat(), useCenter = false,
        topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round),
    )
    if (exitBranch) {
        val branch = RoundaboutDiagram.exitBranch(sweep, size, spec)
        // Le bout arrondi du trait (un demi-trait) est recouvert par la flèche, plus large que le trait.
        drawLine(pathColor, start = branch.start, end = branch.base, strokeWidth = stroke, cap = StrokeCap.Round)
        drawArrowHead(branch.head, pathColor)
    } else {
        drawArrowHead(RoundaboutDiagram.ringArrow(sweep, size, spec), pathColor)
    }
}

private fun DrawScope.drawArrowHead(head: ArrowHead, color: Color) {
    val path = Path().apply {
        moveTo(head.tip.x, head.tip.y)
        lineTo(head.left.x, head.left.y)
        lineTo(head.right.x, head.right.y)
        close()
    }
    drawPath(path, color)
}

/**
 * Rond-point de démonstration : anneau parfait de 15 m parcouru sur [sweepDeg], entrée au sud. Il sert uniquement
 * aux aperçus Compose et aux tests instrumentés, jamais au guidage. La correction n/(n−1) donne un arc mesuré égal à [sweepDeg].
 */
internal fun previewRoundabout(sweepDeg: Double, exitNumber: Int?, street: String?, withExit: Boolean = true): RoundaboutInfo {
    val n = 24
    val total = sweepDeg * n / (n - 1)
    val lat0 = 43.6045; val lon0 = 1.4440; val r = 15.0
    val mLat = 111_320.0; val mLon = 111_320.0 * cos(Math.toRadians(lat0))
    val ring = (0..n).map { k ->
        val phi = Math.toRadians(-90.0 + total * k / n)
        GeoPoint(lat0 + r * sin(phi) / mLat, lon0 + r * cos(phi) / mLon)
    }
    val exitBearing = if (withExit) ((180.0 - sweepDeg) % 360.0 + 360.0) % 360.0 else null
    return RoundaboutInfo(exitCount = null, exitNumber = exitNumber, entryBearingDeg = 0.0, exitBearingDeg = exitBearing, ringGeometry = ring, exitStreet = street)
}

@Preview(name = "2e sortie — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutSecondExitPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(180.0, 2, "Avenue des Champs-Élysées"), 2, compact = false) }
}

@Preview(name = "3e sortie — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutThirdExitPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(282.0, 3, "D 916"), 3, compact = false) }
}

@Preview(name = "Demi-tour — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutUTurnPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(355.0, 4, "Route de Revel"), 4, compact = false) }
}

@Preview(name = "Sans numéro — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutNoNumberPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(90.0, null, null), null, compact = false) }
}

/** Bornes de l'arc dessiné : 40° (1re sortie de l'autoroute, 20° mesurés) et 340° (sortie à 20° de l'entrée). */
@Preview(name = "Bornes 40° / 340° — Nuit", showBackground = true, backgroundColor = 0xFF1F1829, widthDp = 480)
@Composable
private fun RoundaboutClampPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            RoundaboutManeuverView(previewRoundabout(40.0, 1, null), 1, compact = false)
            RoundaboutManeuverView(previewRoundabout(340.0, 4, null), 4, compact = false)
            RoundaboutManeuverView(previewRoundabout(40.0, 1, null), 1, compact = true)
            RoundaboutManeuverView(previewRoundabout(340.0, 4, null), 4, compact = true)
        }
    }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 600)
@Composable
private fun RoundaboutDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            RoundaboutManeuverView(previewRoundabout(180.0, 2, "Avenue des Champs-Élysées"), 2, compact = false)
            RoundaboutManeuverView(previewRoundabout(282.0, 3, null), 3, compact = false)
            RoundaboutManeuverView(previewRoundabout(180.0, 2, null), 2, compact = true)
            RoundaboutManeuverView(previewRoundabout(168.0, 5, null, withExit = false), 5, compact = true)
        }
    }
}
