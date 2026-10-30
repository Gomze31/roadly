package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Bas gauche : vitesse + limitation (« — » si inconnue, rouge si dépassement) et badge « GPS perdu ». */
@Composable
fun DrivingHud(speedKmh: Double?, limitKmh: Int?, toleranceKmh: Int, gpsLost: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        SpeedBadge(speedKmh, limitKmh, toleranceKmh = toleranceKmh)
        if (gpsLost) {
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassPanel(contentPadding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsOff, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nav_gps_lost), style = MaterialTheme.typography.labelLarge, color = c.danger)
                }
            }
        }
    }
}
