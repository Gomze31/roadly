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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth().padding(RoadlySpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(RoadlySpacing.m))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth().padding(RoadlySpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = c.danger)
        if (onRetry != null) {
            Spacer(Modifier.height(RoadlySpacing.m))
            GlassButton("Réessayer", onClick = onRetry, style = GlassButtonStyle.Secondary)
        }
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().background(c.danger.copy(alpha = 0.9f)).padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(RoadlySpacing.s))
        Text("Hors connexion — recherche limitée aux favoris et récents", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun StatesSamples() {
    Column {
        EmptyState(Icons.Default.Search, "Aucun résultat", "Essayez une autre adresse")
        ErrorState("Pas de connexion réseau.", onRetry = {})
        OfflineBanner()
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun StatesNightPreview() {
    RoadlyTheme(darkTheme = true) { StatesSamples() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun StatesDayPreview() {
    RoadlyTheme(darkTheme = false) { StatesSamples() }
}
