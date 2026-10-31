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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

private val COMPACT_SIZE = 48.dp
private val LARGE_SIZE = 160.dp
/** Rayon de l'anneau, en part du côté du schéma. */
private const val RING_RATIO = 0.22f
/** Longueur des branches d'entrée et de sortie, en part du côté. */
private const val STUB_RATIO = 0.20f
/** Centre de l'anneau remonté (part du côté) pour loger la branche d'entrée en bas. */
private const val CENTER_LIFT = 0.04f

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
    val ring = if (compact) c.onBrand.copy(alpha = 0.35f) else c.brandSoft
    val path = if (compact) c.onBrand else c.brandBright
    val numberColor = if (compact) c.onBrand else c.textPrimary
    val label = when (exitNumber) {
        null -> null
        1 -> stringResource(R.string.nav_roundabout_exit_first)
        else -> stringResource(R.string.nav_roundabout_exit_n, exitNumber)
    }
    val description = label?.let { stringResource(R.string.nav_roundabout_desc, it) } ?: stringResource(R.string.nav_roundabout_desc_unknown)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(side).then(if (compact) Modifier.background(c.brand, RoadlyShapes.Tile) else Modifier), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(side)) { drawRoundabout(sweep, info.exitBearingDeg != null, ring, path, if (compact) 0.10f else 0.07f) }
            if (exitNumber != null) {
                Text(
                    exitNumber.toString(),
                    modifier = Modifier.offset(y = -(side * CENTER_LIFT)),
                    style = NavDigits.copy(fontSize = if (compact) 13.sp else 30.sp, lineHeight = if (compact) 15.sp else 34.sp),
                    color = numberColor,
                )
            }
        }
        if (!compact) {
            info.exitStreet?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.width(side).padding(top = 4.dp),
                )
            }
        }
    }
}

/** Dessin : 90° = bas (entrée) ; l'arc négatif tourne dans le sens antihoraire à l'écran ; sortie à 90° − arc. */
private fun DrawScope.drawRoundabout(sweep: Double?, exitBranch: Boolean, ringColor: Color, pathColor: Color, strokeRatio: Float) {
    val s = size.minDimension
    val stroke = s * strokeRatio
    val r = s * RING_RATIO
    val stub = s * STUB_RATIO
    val center = Offset(size.width / 2f, size.height / 2f - s * CENTER_LIFT)
    drawCircle(ringColor, radius = r, center = center, style = Stroke(stroke))
    drawLine(pathColor, start = Offset(center.x, center.y + r + stub), end = Offset(center.x, center.y + r), strokeWidth = stroke, cap = StrokeCap.Round)
    if (sweep == null) return
    drawArc(
        pathColor, startAngle = 90f, sweepAngle = -sweep.toFloat(), useCenter = false,
        topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val theta = Math.toRadians(90.0 - sweep)
    val radial = Offset(cos(theta).toFloat(), sin(theta).toFloat())
    val onRing = center + radial * r
    if (exitBranch) {
        val tip = onRing + radial * stub
        drawLine(pathColor, start = onRing, end = tip - radial * stroke, strokeWidth = stroke, cap = StrokeCap.Round)
        drawArrowHead(tip, radial, stroke * 2.2f, pathColor)
    } else {
        // Tangente dans le sens antihoraire à l'écran (angle décroissant).
        val tangent = Offset(sin(theta).toFloat(), -cos(theta).toFloat())
        drawArrowHead(onRing + tangent * (stroke * 1.4f), tangent, stroke * 2.0f, pathColor)
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, dir: Offset, length: Float, color: Color) {
    val normal = Offset(-dir.y, dir.x)
    val base = tip - dir * length
    val half = length * 0.6f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x * half, base.y + normal.y * half)
        lineTo(base.x - normal.x * half, base.y - normal.y * half)
        close()
    }
    drawPath(head, color)
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
