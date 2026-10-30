package fr.roadly.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object RoadlyShapes {
    val Chip = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(20.dp)
    val Panel = RoundedCornerShape(28.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

object RoadlySpacing {
    val xs = 4.dp; val s = 8.dp; val m = 16.dp; val l = 24.dp; val xl = 32.dp
    val touchTarget = 48.dp
    val touchTargetDriving = 56.dp
}
