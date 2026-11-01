package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.designsystem.theme.glassHighlight
import fr.roadly.core.designsystem.theme.softShadow

/**
 * Panneau « verre » posé sur la carte : surface translucide, reflet, bord fin, ombre douce.
 * Le flou d'arrière-plan réel n'existe pas nativement en Compose sans bibliothèque tierce ;
 * l'opacité de `glass` est calibrée pour rester lisible sur la carte claire comme sombre.
 * Panneau touchable ([onClick]) : [onClickLabel] et [role] sont annoncés par TalkBack (« Toucher deux fois pour … »).
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoadlyShapes.Panel,
    contentPadding: Dp = RoadlySpacing.m,
    onClick: (() -> Unit)? = null,
    elevated: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalRoadlyColors.current
    Box(
        modifier
            .then(if (elevated) Modifier.softShadow(shape, if (c.isDark) c.background else c.textPrimary, 10.dp) else Modifier)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = role, onClick = onClick) else Modifier)
            .background(c.glass)
            .glassHighlight()
            .border(1.dp, c.glassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Carte opaque pour les écrans de liste (réglages, profil, historique) : pas de transparence, bord fin. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoadlyShapes.Card,
    contentPadding: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalRoadlyColors.current
    Box(
        modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(c.surface)
            .border(1.dp, c.glassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun GlassPanelNightPreview() {
    RoadlyTheme(darkTheme = true) {
        Column {
            GlassPanel { Text("Panneau verre", color = LocalRoadlyColors.current.textPrimary) }
            SurfaceCard(contentPadding = 16.dp) { Text("Carte opaque", color = LocalRoadlyColors.current.textPrimary) }
        }
    }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun GlassPanelDayPreview() {
    RoadlyTheme(darkTheme = false) {
        Column {
            GlassPanel { Text("Panneau verre", color = LocalRoadlyColors.current.textPrimary) }
            SurfaceCard(contentPadding = 16.dp) { Text("Carte opaque", color = LocalRoadlyColors.current.textPrimary) }
        }
    }
}
