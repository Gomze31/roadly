package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Niveau de fiabilité affiché (3 barres) d'après le score de confiance 0–100. */
enum class Reliability(val bars: Int, val label: String) {
    HIGH(3, "fiabilité élevée"), MEDIUM(2, "fiabilité moyenne"), LOW(1, "à confirmer");

    companion object {
        fun of(confidence: Int): Reliability = when {
            confidence >= 80 -> HIGH
            confidence >= 60 -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * Zone de danger (Radar Intelligence) : « Zone de danger · encore 1,2 km » à l'intérieur, « Zone de danger dans 800 m » avant.
 * Jamais l'emplacement ni le type d'équipement (France, R413-15). Limitation si connue, fiabilité en trois barres.
 */
@Composable
fun DangerZonePill(title: String, detail: String?, limitKmh: Int?, confidence: Int, inside: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val reliability = Reliability.of(confidence)
    val description = listOfNotNull(title, detail, limitKmh?.let { "limitation $it" }, reliability.label).joinToString(", ")
    GlassPanel(modifier.semantics(mergeDescendants = true) { contentDescription = description }, contentPadding = 8.dp, shape = RoadlyShapes.Chip) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Default.Shield, style = if (inside) IconTileStyle.Danger else IconTileStyle.Warning, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = if (inside) c.danger else c.warning)
                detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textPrimary) }
            }
            limitKmh?.let { Spacer(Modifier.width(8.dp)); SpeedLimitSign(it, size = 30.dp) }
            Spacer(Modifier.width(8.dp))
            ReliabilityBars(reliability)
        }
    }
}

@Composable
private fun ReliabilityBars(r: Reliability) {
    val c = LocalRoadlyColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..3) {
            Box(
                Modifier.width(4.dp).height((4 + 4 * i).dp)
                    .background(if (i <= r.bars) c.textPrimary else c.glassBorder, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun DangerZonePillPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DangerZonePill("Zone de danger", "dans 800 m", 90, 92, inside = false)
            DangerZonePill("Zone de danger", "encore 1,2 km", 50, 55, inside = true)
            Box(Modifier.size(1.dp))
        }
    }
}
