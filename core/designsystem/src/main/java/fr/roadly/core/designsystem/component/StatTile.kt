package fr.roadly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.Overline
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.designsystem.theme.StatValue

/** Valeur + légende en capitales (ETA « 20:46 / ARRIVÉE », « 6 / MIN », « 2,1 / KM »). */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = StatValue,
    valueColor: Color = LocalRoadlyColors.current.textPrimary,
    align: Alignment.Horizontal = Alignment.Start,
) {
    val c = LocalRoadlyColors.current
    Column(modifier, horizontalAlignment = align) {
        Text(value, style = valueStyle, color = valueColor, maxLines = 1)
        Text(label.uppercase(), style = Overline, color = c.textTertiary, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Ligne libellé / valeur (réglages, fiche trajet). */
@Composable
fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
    }
}

@Composable
private fun StatSamples() {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatTile("20:46", "arrivée"); StatTile("6", "min"); StatTile("2,1", "km")
        }
        StatRow("Distance", "98 km")
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun StatNightPreview() { RoadlyTheme(darkTheme = true) { StatSamples() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun StatDayPreview() { RoadlyTheme(darkTheme = false) { StatSamples() } }
