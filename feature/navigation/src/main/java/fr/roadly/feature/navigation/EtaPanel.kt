package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Heure d'arrivée, restant, puis actions (≥ 56 dp en mode conduite) : voix, recentrage (si la carte a bougé), destination, Arrêter. */
@Composable
fun EtaPanel(
    etaClock: String,
    remainingDuration: String,
    remainingDistance: String,
    muted: Boolean,
    following: Boolean,
    onStop: () -> Unit,
    onToggleMute: () -> Unit,
    onRecenter: () -> Unit,
    onChangeDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(stringResource(R.string.nav_eta, etaClock), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.nav_remaining, remainingDuration, remainingDistance), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    stringResource(if (muted) R.string.nav_unmute else R.string.nav_mute),
                    onClick = onToggleMute,
                )
                if (!following) GlassIconButton(Icons.Default.MyLocation, stringResource(R.string.nav_recenter), onClick = onRecenter)
                GlassIconButton(Icons.Default.Search, stringResource(R.string.nav_change_destination), onClick = onChangeDestination)
                Spacer(Modifier.weight(1f))
                GlassButton(stringResource(R.string.nav_stop), onClick = onStop, icon = Icons.Default.Close)
            }
        }
    }
}
