package fr.roadly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme

/**
 * Barre d'information du guidage : heure d'arrivée, minutes et kilomètres restants, puis
 * recentrer (marque, affiché seulement si la carte a été déplacée) et arrêter (rouge).
 * [minutesValue] et [kmValue] sont déjà formatés (ex. « 6 », « 2,1 », « 1 h 12 »).
 */
@Composable
fun NavInfoBar(
    etaClock: String,
    minutesValue: String,
    minutesLabel: String,
    kmValue: String,
    kmLabel: String,
    etaLabel: String,
    showRecenter: Boolean,
    onRecenter: () -> Unit,
    onStop: () -> Unit,
    recenterDescription: String,
    stopDescription: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    GlassPanel(modifier.fillMaxWidth(), shape = RoadlyShapes.Panel, contentPadding = 0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
            StatTile(etaClock, etaLabel)
            Spacer(Modifier.width(20.dp))
            StatTile(minutesValue, minutesLabel)
            Spacer(Modifier.width(20.dp))
            StatTile(kmValue, kmLabel)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showRecenter) RoundIconButton(Icons.Default.Navigation, recenterDescription, onClick = onRecenter, style = RoundButtonStyle.Brand, size = 44.dp)
                RoundIconButton(Icons.Default.Close, stopDescription, onClick = onStop, style = RoundButtonStyle.Danger, size = 44.dp)
            }
        }
    }
}

@Composable
private fun NavInfoBarSample() {
    NavInfoBar(
        etaClock = "20:46", minutesValue = "6", minutesLabel = "min", kmValue = "2,1", kmLabel = "km", etaLabel = "arrivée",
        showRecenter = true, onRecenter = {}, onStop = {}, recenterDescription = "Recentrer", stopDescription = "Arrêter",
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun NavInfoBarNightPreview() { RoadlyTheme(darkTheme = true) { NavInfoBarSample() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 380)
@Composable
private fun NavInfoBarDayPreview() { RoadlyTheme(darkTheme = false) { NavInfoBarSample() } }
