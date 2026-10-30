package fr.roadly.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ombre douce colorée (API 28+ : couleurs d'ombre ambiante et ponctuelle). */
fun Modifier.softShadow(shape: Shape, color: Color, elevation: Dp = 12.dp): Modifier =
    shadow(elevation = elevation, shape = shape, ambientColor = color.copy(alpha = 0.35f), spotColor = color.copy(alpha = 0.45f))

/** Dégradé de marque (boutons « Rouler », tuiles mises en avant). */
@Composable
fun brandGradient(): Brush {
    val c = LocalRoadlyColors.current
    return Brush.linearGradient(listOf(c.brand, c.brandBright))
}

/** Reflet discret en haut d'un panneau translucide. */
@Composable
fun Modifier.glassHighlight(): Modifier {
    val c = LocalRoadlyColors.current
    return background(Brush.verticalGradient(0f to c.glassHighlight, 0.45f to Color.Transparent))
}
