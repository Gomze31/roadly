package fr.roadly.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object RoadlyShapes {
    val Tile = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(24.dp)
    val Chip = RoundedCornerShape(50)
    /** Boutons en pilule (« Continuer », « Rouler »). */
    val Button = RoundedCornerShape(50)
    val Pill = CircleShape
    val Panel = RoundedCornerShape(30.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object RoadlySpacing {
    val xxs = 2.dp; val xs = 4.dp; val s = 8.dp; val sm = 12.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp; val xxl = 48.dp
    val touchTarget = 48.dp
    val touchTargetDriving = 56.dp
    /** Marge entre les panneaux flottants et le bord de l'écran. */
    val screenEdge = 12.dp
}

/** Durées d'animation partagées (ms). */
object RoadlyMotion {
    const val fast = 150
    const val normal = 240
    const val slow = 380
}
