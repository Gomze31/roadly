package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalDrivingMode
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalRoadlyColors.current
    val minHeight = if (LocalDrivingMode.current) RoadlySpacing.touchTargetDriving else RoadlySpacing.touchTarget
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minHeight = minHeight)
            .clip(RoadlyShapes.Chip)
            .background(if (selected) c.accent else c.glass)
            .border(1.dp, c.glassBorder, RoadlyShapes.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) c.onAccent else c.textPrimary
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
    }
}

@Composable
private fun GlassChipSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
        GlassChip("Domicile", onClick = {}, icon = Icons.Default.Home)
        GlassChip("Sans péage", onClick = {}, selected = true)
        GlassChip("Covoiturage", onClick = {}, enabled = false, trailing = { ComingSoonTag() })
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun GlassChipNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassChipSamples() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun GlassChipDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassChipSamples() }
}
