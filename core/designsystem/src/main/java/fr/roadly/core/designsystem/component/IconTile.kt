package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme

enum class IconTileStyle { Brand, BrandFilled, Accent, Warning, Danger, Neutral }

/** Tuile carrée arrondie avec icône : bandeau de manœuvre, lignes de liste, catégories. */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    style: IconTileStyle = IconTileStyle.Brand,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    val c = LocalRoadlyColors.current
    val (bg, fg) = when (style) {
        IconTileStyle.Brand -> c.brandSoft to c.brandBright
        IconTileStyle.BrandFilled -> c.brand to c.onBrand
        IconTileStyle.Accent -> c.accentSoft to c.accent
        IconTileStyle.Warning -> c.warning.copy(alpha = 0.18f) to c.warning
        IconTileStyle.Danger -> c.dangerSoft to c.danger
        IconTileStyle.Neutral -> c.glassBorder to c.textSecondary
    }
    Box(modifier.size(size).background(bg, RoadlyShapes.Tile), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}

/** Pastille ronde avec une couleur imposée (avatars de catégories, événements d'Horizon). */
@Composable
fun ColorDotIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp, contentDescription: String? = null) {
    Box(modifier.size(size).background(color, RoadlyShapes.Pill), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
private fun IconTileSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(Icons.Default.TurnRight)
        IconTile(Icons.Default.TurnRight, style = IconTileStyle.BrandFilled)
        IconTile(Icons.Default.LocalGasStation, style = IconTileStyle.Accent)
        IconTile(Icons.Default.Warning, style = IconTileStyle.Warning)
        IconTile(Icons.Default.Warning, style = IconTileStyle.Danger)
        IconTile(Icons.Default.Warning, style = IconTileStyle.Neutral)
        ColorDotIcon(Icons.Default.Warning, LocalRoadlyColors.current.warning)
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun IconTileNightPreview() { RoadlyTheme(darkTheme = true) { IconTileSamples() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun IconTileDayPreview() { RoadlyTheme(darkTheme = false) { IconTileSamples() } }
