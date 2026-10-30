package fr.roadly.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.Inter
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.Overline
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import kotlin.math.roundToInt

/**
 * Compteur : vitesse en grand à gauche, panneau de limitation (cercle rouge) à droite.
 * [speedKmh] null = pas de fix GPS. [limitKmh] null = limitation inconnue (affiche « — »).
 * Les chiffres passent en rouge au-delà de limite + [toleranceKmh].
 */
@Composable
fun SpeedBadge(speedKmh: Double?, limitKmh: Int?, modifier: Modifier = Modifier, toleranceKmh: Int = 5) {
    val c = LocalRoadlyColors.current
    val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh + toleranceKmh
    val digitsColor by animateColorAsState(if (over) c.danger else c.textPrimary, label = "speedColor")
    val description = "Vitesse ${speedKmh?.roundToInt() ?: "inconnue"} km/h, limitation ${limitKmh ?: "inconnue"}"
    GlassPanel(modifier.semantics { contentDescription = description }, shape = RoadlyShapes.Panel, contentPadding = 0.dp) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(speedKmh?.roundToInt()?.toString() ?: "–", style = NavDigits.copy(fontSize = 40.sp, lineHeight = 42.sp), color = digitsColor)
                Text("KM/H", style = Overline, color = c.textTertiary)
            }
            Spacer(Modifier.width(12.dp))
            SpeedLimitSign(limitKmh, size = 48.dp)
        }
    }
}

/** Panneau de limitation : cercle blanc à bord rouge, chiffre noir ; « — » si inconnue. */
@Composable
fun SpeedLimitSign(limitKmh: Int?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val c = LocalRoadlyColors.current
    Box(
        modifier.size(size).background(Color.White, CircleShape).border(size * 0.11f, c.speedRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            limitKmh?.toString() ?: "—",
            fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp, color = Color.Black,
            maxLines = 1,
        )
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun SpeedBadgeNightPreview() {
    RoadlyTheme(darkTheme = true) { SpeedBadge(speedKmh = 93.0, limitKmh = 80) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun SpeedBadgeDayPreview() {
    RoadlyTheme(darkTheme = false) { SpeedBadge(speedKmh = 72.0, limitKmh = null) }
}
