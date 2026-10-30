package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalDrivingMode
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

enum class GlassButtonStyle { Primary, Secondary }

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val c = LocalRoadlyColors.current
    val minHeight = if (LocalDrivingMode.current) RoadlySpacing.touchTargetDriving else RoadlySpacing.touchTarget
    val bg = if (style == GlassButtonStyle.Primary) c.accent else c.glass
    val fg = if (style == GlassButtonStyle.Primary) c.onAccent else c.textPrimary
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = minHeight)
            .clip(RoadlyShapes.Button)
            .background(bg)
            .border(1.dp, c.glassBorder, RoadlyShapes.Button)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = RoadlySpacing.l, vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(RoadlySpacing.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalRoadlyColors.current.textPrimary,
    enabled: Boolean = true,
) {
    val c = LocalRoadlyColors.current
    val size = if (LocalDrivingMode.current) RoadlySpacing.touchTargetDriving else RoadlySpacing.touchTarget
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(size + 8.dp)
            .clip(RoadlyShapes.Button)
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoadlyShapes.Button)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun GlassButtonSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
        GlassButton("Démarrer", onClick = {}, icon = Icons.Default.Navigation)
        GlassButton("Options", onClick = {}, style = GlassButtonStyle.Secondary)
        GlassIconButton(Icons.Default.Settings, contentDescription = "Réglages", onClick = {})
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun GlassButtonNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassButtonSamples() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun GlassButtonDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassButtonSamples() }
}
