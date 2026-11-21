package fr.roadly.core.designsystem.component

import fr.roadly.core.designsystem.theme.RowTitle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Groupe de lignes dans une carte opaque, précédé d'un petit titre en capitales. */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable () -> Unit) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth()) {
        if (title != null) OverlineText(title, Modifier.padding(start = 6.dp, bottom = 8.dp), color = c.textTertiary, dash = false)
        SurfaceCard(Modifier.fillMaxWidth()) { Column { content() } }
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
    }
}

/** Ligne de liste : tuile d'icône, titre, sous-titre, zone de droite (valeur, chevron, interrupteur…). */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconStyle: IconTileStyle = IconTileStyle.Brand,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    divider: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick, role = Role.Button) else Modifier)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = RoadlySpacing.m, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { IconTile(icon, style = iconStyle, size = 36.dp); Spacer(Modifier.width(RoadlySpacing.sm)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = RowTitle, color = if (enabled) c.textPrimary else c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) { Spacer(Modifier.width(RoadlySpacing.s)); Text(value, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1) }
            if (trailing != null) { Spacer(Modifier.width(RoadlySpacing.s)); trailing() }
            if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textTertiary, modifier = Modifier.size(22.dp)) }
        }
        if (divider) RowDivider(inset = if (icon != null) 64.dp else RoadlySpacing.m)
    }
}

@Composable
fun RowDivider(inset: androidx.compose.ui.unit.Dp = RoadlySpacing.m) {
    val c = LocalRoadlyColors.current
    Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(c.divider))
}

/** Ligne avec interrupteur ; toute la ligne bascule la valeur. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconStyle: IconTileStyle = IconTileStyle.Brand,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .defaultMinSize(minHeight = 56.dp).padding(horizontal = RoadlySpacing.m, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { IconTile(icon, style = iconStyle, size = 36.dp); Spacer(Modifier.width(RoadlySpacing.sm)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = RowTitle, color = if (enabled) c.textPrimary else c.textTertiary)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.width(RoadlySpacing.s))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = roadlySwitchColors())
        }
        if (divider) RowDivider(inset = if (icon != null) 64.dp else RoadlySpacing.m)
    }
}

@Composable
fun roadlySwitchColors() = LocalRoadlyColors.current.let { c ->
    SwitchDefaults.colors(
        checkedThumbColor = c.onBrand, checkedTrackColor = c.brand,
        uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.glassBorder, uncheckedBorderColor = c.glassBorder,
    )
}

/** Sélecteur segmenté (clair / sombre / auto ; carburants). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    Row(
        modifier.fillMaxWidth().clip(RoadlyShapes.Button).background(c.glassBorder).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { o ->
            val isSel = o == selected
            Box(
                Modifier.weight(1f).defaultMinSize(minHeight = 38.dp).clip(RoadlyShapes.Tile)
                    .background(if (isSel) c.brand else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(o) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (isSel) c.onBrand else c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ListRowSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroup("Conduite") {
            ListRow("Véhicule", subtitle = "Berline · Gazole · 5,6 L/100", icon = Icons.Default.DirectionsCar, onClick = {})
            ListRow("Apparence", value = "Auto", icon = Icons.Default.Palette, onClick = {})
            ToggleRow("Voix", checked = true, onCheckedChange = {}, icon = Icons.Default.RecordVoiceOver, divider = false)
        }
        SegmentedControl(listOf("Clair", "Sombre", "Auto"), "Auto", {}, { it })
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun ListRowNightPreview() { RoadlyTheme(darkTheme = true) { ListRowSamples() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 380)
@Composable
private fun ListRowDayPreview() { RoadlyTheme(darkTheme = false) { ListRowSamples() } }
