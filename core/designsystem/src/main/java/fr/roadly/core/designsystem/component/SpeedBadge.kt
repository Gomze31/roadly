package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyTheme
import kotlin.math.roundToInt

/** [speedKmh] null = pas de fix GPS. [limitKmh] null = limitation inconnue (affiche « — »). */
@Composable
fun SpeedBadge(speedKmh: Double?, limitKmh: Int?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh + 5
    GlassPanel(modifier, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(speedKmh?.roundToInt()?.toString() ?: "–", style = NavDigits, color = if (over) c.danger else c.textPrimary)
                Text("km/h", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(52.dp).background(Color.White, CircleShape).border(5.dp, Color(0xFFD7263D), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(limitKmh?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            }
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun SpeedBadgeNightPreview() {
    RoadlyTheme(darkTheme = true) { SpeedBadge(speedKmh = 93.0, limitKmh = 80) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun SpeedBadgeDayPreview() {
    RoadlyTheme(darkTheme = false) { SpeedBadge(speedKmh = 72.0, limitKmh = 80) }
}
