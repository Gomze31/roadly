package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth().padding(RoadlySpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, style = IconTileStyle.Neutral, size = 56.dp)
        Spacer(Modifier.height(RoadlySpacing.m))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth().padding(RoadlySpacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(Icons.Default.ErrorOutline, style = IconTileStyle.Danger, size = 48.dp)
        Spacer(Modifier.height(RoadlySpacing.sm))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(RoadlySpacing.m))
            GlassButton("Réessayer", onClick = onRetry, style = GlassButtonStyle.Secondary)
        }
    }
}

/** Ligne de chargement discrète (panneaux de carte). */
@Composable
fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = c.brand, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(RoadlySpacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().background(c.warning.copy(alpha = 0.18f), RoadlyShapes.Tile).padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = c.warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(RoadlySpacing.s))
        Text("Hors connexion — recherche limitée aux favoris et récents", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
    }
}

@Composable
private fun StatesSamples() {
    Column {
        EmptyState(Icons.Default.Search, "Aucun résultat", "Essayez une autre adresse")
        ErrorState("Pas de connexion réseau.", onRetry = {})
        LoadingRow("Calcul des itinéraires…")
        OfflineBanner()
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun StatesNightPreview() { RoadlyTheme(darkTheme = true) { StatesSamples() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun StatesDayPreview() { RoadlyTheme(darkTheme = false) { StatesSamples() } }
