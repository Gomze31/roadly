package fr.roadly.core.designsystem.component

import androidx.compose.material.icons.filled.Construction

import androidx.compose.material.icons.filled.Block

import androidx.compose.foundation.border

import androidx.compose.ui.graphics.compositeOver

import androidx.compose.material.icons.filled.MonitorHeart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.Overline
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import kotlin.math.ln

/** Catégories d'événements de l'Horizon ; la couleur et l'icône dépendent du thème. */
enum class HorizonKind { MANEUVER, CURVE, SLOWDOWN, INCIDENT, ROADWORK, CLOSURE, VIGILANCE, SERVICE, SPEED_BUMP, TRAFFIC_LIGHT, TOLL, ARRIVAL }

/** Un événement à venir sur l'itinéraire : [distanceM] depuis la position courante, [label] court (ex. « 800 m »). */
data class HorizonItem(val kind: HorizonKind, val distanceM: Double, val label: String, val contentDescription: String)

/** Densité de la route à venir : 0 = fluide, 1 = saturé. `null` = inconnue (pas de source trafic). */
data class HorizonDensity(val segments: List<Float>)

object HorizonScale {
    /** Portée du ruban (m) : les événements au-delà sont groupés au bord droit. */
    const val RANGE_M = 5_000.0

    /** Position 0..1 sur le ruban, échelle logarithmique : les premiers 500 m occupent environ la moitié de la largeur. */
    fun position(distanceM: Double): Float {
        if (distanceM <= 0) return 0f
        val d = distanceM.coerceAtMost(RANGE_M)
        return (ln(1 + d / 150.0) / ln(1 + RANGE_M / 150.0)).toFloat().coerceIn(0f, 1f)
    }
}

@Composable
fun HorizonKind.icon(): ImageVector = when (this) {
    HorizonKind.MANEUVER -> Icons.Default.TurnRight
    HorizonKind.CURVE -> Icons.Default.Timeline
    HorizonKind.VIGILANCE -> Icons.Default.Shield
    HorizonKind.SLOWDOWN, HorizonKind.INCIDENT, HorizonKind.SPEED_BUMP -> Icons.Default.Warning
    HorizonKind.ROADWORK -> Icons.Default.Construction
    HorizonKind.CLOSURE -> Icons.Default.Block
    HorizonKind.SERVICE -> Icons.Default.LocalGasStation
    HorizonKind.TRAFFIC_LIGHT -> Icons.Default.Traffic
    HorizonKind.TOLL -> Icons.Default.Euro
    HorizonKind.ARRIVAL -> Icons.Default.Flag
}

@Composable
fun HorizonKind.color(): Color {
    val c = LocalRoadlyColors.current
    return when (this) {
        HorizonKind.MANEUVER, HorizonKind.CURVE -> c.brand
        HorizonKind.SLOWDOWN -> c.warning
        HorizonKind.INCIDENT -> c.danger
        HorizonKind.CLOSURE -> c.accent
        HorizonKind.ROADWORK -> c.accent
        HorizonKind.VIGILANCE -> c.warning
        HorizonKind.SERVICE -> c.success
        HorizonKind.SPEED_BUMP -> c.textSecondary
        HorizonKind.TRAFFIC_LIGHT -> c.success
        HorizonKind.TOLL -> c.warning
        HorizonKind.ARRIVAL -> c.brandBright
    }
}

/**
 * Ruban « Horizon » : la route à venir sur une échelle logarithmique (0 → 5 km), événements posés dessus,
 * barre de densité sous le ruban quand une source trafic existe. Sans trafic, [density] = null et le ruban reste neutre.
 */
@Composable
fun HorizonStrip(
    items: List<HorizonItem>,
    title: String,
    modifier: Modifier = Modifier,
    density: HorizonDensity? = null,
    rangeLabel: String = "5 km",
) {
    val c = LocalRoadlyColors.current
    val description = if (items.isEmpty()) title else "$title : " + items.joinToString(", ") { it.contentDescription }
    GlassPanel(modifier.fillMaxWidth().semantics { contentDescription = description }, shape = RoadlyShapes.Panel, contentPadding = 0.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // En-tête : pouls (titre lu par TalkBack) à gauche ; à droite, le prochain événement notable (hors manœuvres) en couleur.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = c.brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                val headline = items.firstOrNull { it.kind != HorizonKind.MANEUVER && it.kind != HorizonKind.ARRIVAL } ?: items.firstOrNull()
                if (headline != null) {
                    val col = headline.kind.color()
                    Box(Modifier.size(10.dp).background(col, RoadlyShapes.Pill))
                    Spacer(Modifier.width(8.dp))
                    Icon(headline.kind.icon(), contentDescription = null, tint = col, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(headline.label, style = Overline.copy(fontSize = 14.sp, letterSpacing = 0.3.sp), color = col, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(rangeLabel, style = Overline, color = c.textTertiary)
                }
            }
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp)) {
                val trackW = maxWidth - 24.dp
                val rail = c.textTertiary.copy(alpha = 0.45f)
                // Rail fin et graduations (1/3, 2/3) ; densité en segments colorés si connue.
                Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 9.dp).width(trackW).height(4.dp).background(rail, RoundedCornerShape(2.dp)))
                listOf(1f / 3f, 2f / 3f).forEach { f ->
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 4.dp).offset(x = trackW * f).width(2.dp).height(14.dp).background(rail, RoundedCornerShape(1.dp)))
                }
                if (density != null && density.segments.isNotEmpty()) {
                    Row(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 9.dp).width(trackW).height(4.dp)) {
                        density.segments.forEach { d ->
                            val col = when {
                                d >= 0.75f -> c.danger
                                d >= 0.45f -> c.warning
                                else -> c.success
                            }
                            Box(Modifier.weight(1f).height(4.dp).background(col.copy(alpha = 0.9f)))
                        }
                    }
                }
                // Position actuelle : point de marque avec halo.
                Box(Modifier.align(Alignment.BottomStart).padding(start = 3.dp).size(22.dp).background(c.brand.copy(alpha = 0.25f), RoadlyShapes.Pill), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(13.dp).background(c.brand, RoadlyShapes.Pill).border(2.dp, Color.White, RoadlyShapes.Pill))
                }
                // Événements : épingles posées au-dessus du rail.
                items.take(8).forEach { item ->
                    val x = trackW * HorizonScale.position(item.distanceM)
                    val col = item.kind.color()
                    Box(
                        Modifier.align(Alignment.TopStart).offset(x = x).size(28.dp).background(col.copy(alpha = 0.22f).compositeOver(c.surface), RoadlyShapes.Pill)
                            .border(2.5.dp, col, RoadlyShapes.Pill),
                        contentAlignment = Alignment.Center,
                    ) { Icon(item.kind.icon(), contentDescription = null, tint = col, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HorizonSample() {
    HorizonStrip(
        items = listOf(
            HorizonItem(HorizonKind.MANEUVER, 180.0, "180 m · à droite", "Tourner à droite dans 180 m"),
            HorizonItem(HorizonKind.SLOWDOWN, 900.0, "900 m · ralenti", "Ralentissement dans 900 m"),
            HorizonItem(HorizonKind.SERVICE, 2600.0, "2,6 km · station", "Station-service dans 2,6 km"),
        ),
        title = "La route à venir",
        density = HorizonDensity(listOf(0.1f, 0.2f, 0.6f, 0.8f, 0.3f, 0.1f)),
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun HorizonNightPreview() { RoadlyTheme(darkTheme = true) { HorizonSample() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 380)
@Composable
private fun HorizonDayPreview() { RoadlyTheme(darkTheme = false) { HorizonSample() } }
