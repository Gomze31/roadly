package fr.roadly.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

fun ManeuverType.icon(): ImageVector = when (this) {
    ManeuverType.DEPART -> Icons.Default.TripOrigin
    ManeuverType.ARRIVE -> Icons.Default.Flag
    ManeuverType.CONTINUE -> Icons.Default.Straight
    ManeuverType.TURN_LEFT -> Icons.Default.TurnLeft
    ManeuverType.TURN_RIGHT -> Icons.Default.TurnRight
    ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    ManeuverType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    ManeuverType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    ManeuverType.UTURN -> Icons.Default.UTurnLeft
    ManeuverType.ROUNDABOUT_ENTER, ManeuverType.ROUNDABOUT_EXIT -> Icons.Default.RoundaboutRight
    ManeuverType.MERGE -> Icons.Default.Merge
    ManeuverType.RAMP -> Icons.Default.RampRight
    ManeuverType.EXIT, ManeuverType.KEEP_RIGHT -> Icons.Default.ForkRight
    ManeuverType.KEEP_LEFT -> Icons.Default.ForkLeft
    ManeuverType.FERRY -> Icons.Default.DirectionsBoat
    ManeuverType.OTHER -> Icons.Default.Navigation
}

@Composable
fun ManeuverIcon(type: ManeuverType, modifier: Modifier = Modifier, tint: Color = LocalRoadlyColors.current.textPrimary) {
    Icon(type.icon(), contentDescription = null, tint = tint, modifier = modifier)
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun ManeuverIconNightPreview() {
    RoadlyTheme(darkTheme = true) { ManeuverIcon(ManeuverType.TURN_LEFT, Modifier.size(48.dp)) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun ManeuverIconDayPreview() {
    RoadlyTheme(darkTheme = false) { ManeuverIcon(ManeuverType.TURN_RIGHT, Modifier.size(48.dp)) }
}
