package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalDrivingMode
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.designsystem.theme.brandGradient
import fr.roadly.core.designsystem.theme.softShadow

/**
 * Styles : [Primary] = orange d'appel à l'action ; [Brand] = dégradé violet (« Rouler », navigation) ;
 * [Secondary] = verre ; [Danger] = rouge doux ; [Ghost] = texte seul.
 */
enum class GlassButtonStyle { Primary, Brand, Secondary, Danger, Ghost }

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val c = LocalRoadlyColors.current
    val minHeight = if (LocalDrivingMode.current) RoadlySpacing.touchTargetDriving else RoadlySpacing.touchTarget
    val fg = when (style) {
        GlassButtonStyle.Primary -> c.onAccent
        GlassButtonStyle.Brand -> c.onBrand
        GlassButtonStyle.Secondary, GlassButtonStyle.Ghost -> c.textPrimary
        GlassButtonStyle.Danger -> c.danger
    }
    val shape = RoadlyShapes.Button
    val bgModifier = when (style) {
        GlassButtonStyle.Primary -> Modifier.softShadow(shape, c.accent, 8.dp).background(c.accent)
        GlassButtonStyle.Brand -> Modifier.softShadow(shape, c.brand, 10.dp).background(brandGradient())
        GlassButtonStyle.Secondary -> Modifier.background(c.glass).border(1.dp, c.glassBorder, shape)
        GlassButtonStyle.Danger -> Modifier.background(c.dangerSoft)
        GlassButtonStyle.Ghost -> Modifier
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .then(bgModifier)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = RoadlySpacing.l, vertical = RoadlySpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(RoadlySpacing.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        if (trailingIcon != null) {
            Spacer(Modifier.width(RoadlySpacing.s))
            Icon(trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

/** Bouton carré arrondi en verre (barre du haut, actions secondaires). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalRoadlyColors.current.textPrimary,
    enabled: Boolean = true,
    badge: Boolean = false,
) {
    val c = LocalRoadlyColors.current
    val size = if (LocalDrivingMode.current) RoadlySpacing.touchTargetDriving else RoadlySpacing.touchTarget
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(size)
            .softShadow(RoadlyShapes.Button, if (c.isDark) c.background else c.textPrimary, 8.dp)
            .clip(RoadlyShapes.Button)
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoadlyShapes.Button)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).background(c.accent, RoadlyShapes.Pill))
    }
}

enum class RoundButtonStyle { Brand, Danger, Glass, Accent }

/** Bouton rond plein (recentrer = marque, arrêter = rouge, replier = verre). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RoundButtonStyle = RoundButtonStyle.Glass,
    size: Dp = if (LocalDrivingMode.current) RoadlySpacing.touchTargetDriving else RoadlySpacing.touchTarget,
    enabled: Boolean = true,
) {
    val c = LocalRoadlyColors.current
    val (bg, fg, shadow) = when (style) {
        RoundButtonStyle.Brand -> Triple(c.brand, c.onBrand, c.brand)
        RoundButtonStyle.Danger -> Triple(c.danger, Color.White, c.danger)
        RoundButtonStyle.Accent -> Triple(c.accent, c.onAccent, c.accent)
        RoundButtonStyle.Glass -> Triple(c.glass, c.textPrimary, if (c.isDark) c.background else c.textPrimary)
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(size)
            .softShadow(RoadlyShapes.Pill, shadow, 8.dp)
            .clip(RoadlyShapes.Pill)
            .background(bg)
            .then(if (style == RoundButtonStyle.Glass) Modifier.border(1.dp, c.glassBorder, RoadlyShapes.Pill) else Modifier)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(size * 0.46f)) }
}

@Composable
private fun GlassButtonSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
            GlassButton("Démarrer", onClick = {}, icon = Icons.Default.Navigation)
            GlassButton("Rouler", onClick = {}, style = GlassButtonStyle.Brand, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
            GlassButton("Options", onClick = {}, style = GlassButtonStyle.Secondary)
            GlassButton("Supprimer", onClick = {}, style = GlassButtonStyle.Danger)
            GlassIconButton(Icons.Default.Settings, contentDescription = "Réglages", onClick = {}, badge = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Default.Navigation, "Recentrer", onClick = {}, style = RoundButtonStyle.Brand)
            RoundIconButton(Icons.Default.Close, "Arrêter", onClick = {}, style = RoundButtonStyle.Danger)
            RoundIconButton(Icons.Default.Settings, "Réglages", onClick = {})
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun GlassButtonNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassButtonSamples() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun GlassButtonDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassButtonSamples() }
}
