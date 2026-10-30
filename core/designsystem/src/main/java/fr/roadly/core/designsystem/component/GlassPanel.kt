package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/**
 * Panneau « verre » : surface translucide + reflet dégradé + bord fin.
 * Le flou d'arrière-plan réel n'existe pas nativement en Compose sans bibliothèque tierce ;
 * l'opacité des couleurs `glass` est calibrée pour rester lisible sur la carte.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoadlyShapes.Panel,
    contentPadding: Dp = RoadlySpacing.m,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalRoadlyColors.current
    Box(
        modifier
            .clip(shape)
            .background(c.glass)
            .background(Brush.verticalGradient(0f to c.glassHighlight, 0.4f to Color.Transparent))
            .border(1.dp, c.glassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun GlassPanelNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassPanel { Text("Panneau verre", color = LocalRoadlyColors.current.textPrimary) } }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun GlassPanelDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassPanel { Text("Panneau verre", color = LocalRoadlyColors.current.textPrimary) } }
}
