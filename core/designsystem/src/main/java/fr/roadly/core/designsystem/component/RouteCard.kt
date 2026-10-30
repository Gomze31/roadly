package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.designsystem.theme.StatValue

/**
 * En-tête du panneau « Votre choix » : durée en grand, heure d'arrivée et distance à droite,
 * puis une rangée de puces (péage, coût, trafic) fournie par l'appelant.
 */
@Composable
fun RouteHeadline(
    duration: String,
    arrival: String,
    arrivalLabel: String,
    distance: String,
    distanceLabel: String,
    modifier: Modifier = Modifier,
    chips: (@Composable () -> Unit)? = null,
) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(duration, style = MaterialTheme.typography.displayMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            StatTile(arrival, arrivalLabel, valueStyle = StatValue.copy(fontSize = 18.sp, lineHeight = 22.sp))
            Spacer(Modifier.width(20.dp))
            StatTile(distance, distanceLabel, valueStyle = StatValue.copy(fontSize = 18.sp, lineHeight = 22.sp), align = Alignment.End)
        }
        if (chips != null) { Spacer(Modifier.height(RoadlySpacing.sm)); chips() }
    }
}

/** Alternative compacte : durée, étiquette, à droite distance ; la ligne sélectionnée est surlignée marque. */
@Composable
fun RouteOptionRow(
    duration: String,
    title: String,
    distance: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: (@Composable () -> Unit)? = null,
    note: String? = null,
) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().clip(RoadlyShapes.Tile)
            .background(if (selected) c.brandSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(duration, style = MaterialTheme.typography.titleMedium, color = if (selected) c.brandBright else c.textPrimary, modifier = Modifier.width(72.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tag != null) { Spacer(Modifier.width(6.dp)); tag() }
            }
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, maxLines = 1)
        }
        Text(distance, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

/** Compatibilité : carte complète d'itinéraire (fiche, historique). */
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
    GlassPanel(modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) c.brandBright else c.textPrimary, modifier = Modifier.weight(1f))
                Text(duration, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text("$distance · arrivée $arrival", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Text(costLine ?: "Renseigner le véhicule pour estimer le coût", style = MaterialTheme.typography.bodyMedium, color = if (costLine != null) c.textPrimary else c.textSecondary)
            Text(tollLine, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
    }
}

@Composable
private fun RouteSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RouteHeadline("8 min", "19:53", "arrivée", "2,9 km", "distance", chips = { Row { GlassChip("Sans péage", onClick = {}, compact = true) } })
        RouteOptionRow("7 min", "Le plus court", "2,4 km", selected = false, onClick = {})
        RouteOptionRow("8 min", "Le plus rapide", "2,9 km", selected = true, onClick = {})
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun RouteNightPreview() { RoadlyTheme(darkTheme = true) { RouteSamples() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 380)
@Composable
private fun RouteDayPreview() { RoadlyTheme(darkTheme = false) { RouteSamples() } }
