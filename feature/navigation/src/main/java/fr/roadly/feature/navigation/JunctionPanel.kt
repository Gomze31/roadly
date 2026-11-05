package fr.roadly.feature.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.map.junction.JunctionPalette
import fr.roadly.core.map.junction.JunctionSceneState
import fr.roadly.core.map.junction.JunctionView
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionPhase

/** Palette de la scène : base jour / nuit, trajectoire et voies conseillées à la teinte du cockpit. */
@Composable
internal fun rememberJunctionPalette(isDark: Boolean): JunctionPalette {
    val c = LocalRoadlyColors.current
    return remember(isDark, c.routeActive, c.brand) {
        (if (isDark) JunctionPalette.NIGHT else JunctionPalette.DAY).copy(
            path = c.routeActive.toArgb(), pathGlow = c.brand.toArgb(), laneActive = c.routeActive.toArgb(),
        )
    }
}

/** La vue 3D est montrée (étape visible et scène prête). */
internal fun junctionPanelVisible(j: JunctionGuidance?, scene: JunctionSceneState?): Boolean = j != null && j.phase.visible && scene != null && scene.routeId == j.routeId

/** Opacité de la vue : apparition progressive pendant REVEAL, disparition pendant RECOVER (bornée pour rester lisible). */
internal fun junctionAlpha(j: JunctionGuidance): Float = when (j.phase) {
    JunctionPhase.PREPARE -> 0f
    JunctionPhase.REVEAL -> (0.35f + 0.65f * j.reveal).coerceIn(0f, 1f)
    JunctionPhase.RECOVER -> j.reveal.coerceIn(0f, 1f)
    else -> 1f
}

/**
 * Vue 3D de carrefour sous le bandeau : rendu OpenGL de la géométrie réelle, titre factuel en haut (« SORTIE 31b », « 2e SORTIE »)
 * et distance en bas, comme le bandeau. Coins arrondis et liseré du design system ; fondu piloté par l'étape d'anticipation.
 */
@Composable
internal fun JunctionPanel(
    scene: JunctionSceneState,
    guidance: JunctionGuidance,
    ui: JunctionUi?,
    maxFps: Int,
    modifier: Modifier = Modifier,
    onUnsupported: () -> Unit = {},
) {
    val c = LocalRoadlyColors.current
    val alpha by animateFloatAsState(junctionAlpha(guidance), tween(450), label = "junctionAlpha")
    val description = listOfNotNull("Vue 3D du carrefour", ui?.title, ui?.distanceText?.takeIf { it.isNotEmpty() }).joinToString(", ")
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha; scaleX = 0.96f + 0.04f * alpha; scaleY = 0.96f + 0.04f * alpha }
            .clip(RoadlyShapes.Panel)
            .border(1.dp, c.glassBorder, RoadlyShapes.Panel)
            .semantics { contentDescription = description },
    ) {
        JunctionView(scene, guidance, maxFps, Modifier.matchParentSize(), onUnsupported)
        ui?.title?.let { title ->
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = c.onBrandAuto,
                modifier = Modifier.align(Alignment.TopStart).padding(RoadlySpacing.sm)
                    .clip(RoadlyShapes.Chip).background(c.brand).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.align(Alignment.BottomStart).padding(RoadlySpacing.sm)) {
            val text = ui?.distanceText.orEmpty()
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    modifier = Modifier.clip(RoadlyShapes.Chip).background(c.glass.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (guidance.degraded) {
                Text(
                    "GPS imprécis",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(start = RoadlySpacing.s).clip(RoadlyShapes.Chip).background(c.glass.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
