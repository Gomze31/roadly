package fr.roadly.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Test

/** B3 : l'icône suit le côté donné par Valhalla (scénario 3 : virage à gauche). */
class ManeuverIconTest {
    @Test fun `left and right variants get their own icon`() {
        ManeuverType.entries.forEach { it.icon() }
        assertEquals(Icons.Default.TurnLeft.name, ManeuverType.TURN_LEFT.icon().name)
        assertEquals(Icons.Default.RampLeft.name, ManeuverType.RAMP_LEFT.icon().name)
        assertEquals(Icons.Default.RampRight.name, ManeuverType.RAMP_RIGHT.icon().name)
        assertEquals(Icons.Default.ForkLeft.name, ManeuverType.EXIT_LEFT.icon().name)
        assertEquals(Icons.Default.UTurnRight.name, ManeuverType.UTURN_RIGHT.icon().name)
    }
}
