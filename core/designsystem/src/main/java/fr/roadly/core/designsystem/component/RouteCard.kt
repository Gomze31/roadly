package fr.roadly.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme

@Composable
fun RouteCard(
    title: String,
    duration: String,
    distance: String,
    arrival: String,
    costLine: String?,
    tollLine: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(
        modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, c.routeActive, RoadlyShapes.Panel) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) c.routeActive else c.textPrimary, modifier = Modifier.weight(1f))
                Text(duration, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text("$distance · arrivée $arrival", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Text(
                costLine ?: "Renseigner le véhicule pour estimer le coût",
                style = MaterialTheme.typography.bodyMedium,
                color = if (costLine != null) c.textPrimary else c.textSecondary,
            )
            Text(tollLine, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = c.accent)
        }
    }
}

@Composable
private fun RouteCardSample() {
    RouteCard(
        title = "Le plus rapide",
        duration = "1 h 12",
        distance = "98 km",
        arrival = "14:35",
        costLine = "≈ 11,40 €",
        tollLine = "Péages : 8,60 €",
        selected = true,
        onClick = {},
        note = "Trafic dense à l'approche de Lyon",
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun RouteCardNightPreview() {
    RoadlyTheme(darkTheme = true) { RouteCardSample() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun RouteCardDayPreview() {
    RoadlyTheme(darkTheme = false) { RouteCardSample() }
}
