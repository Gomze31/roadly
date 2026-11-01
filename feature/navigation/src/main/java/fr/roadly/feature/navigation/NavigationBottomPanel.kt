package fr.roadly.feature.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.StatTile
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Étiquette de la ligne résumée (ETA) pour les tests instrumentés. */
internal const val PANEL_SUMMARY_TAG = "bottom_panel_summary"

/** Glissement vertical minimal sur le panneau pour le déplier (vers le haut) ou le replier (vers le bas). */
private val DRAG_THRESHOLD = 24.dp

/** Hauteur de la bande de la poignée, touchable sur toute la largeur du panneau. */
private val HANDLE_BAND_HEIGHT = 24.dp

/**
 * D5 : panneau du bas du guidage.
 * - Replié : poignée, heure d'arrivée, temps et distance restants, voix (Recentrer à sa place quand la carte a été déplacée :
 *   trois boutons de 56 dp ne tiennent pas sur ~433 dp) et Arrêter (rouge).
 * - Toucher la ligne ETA ouvre le trajet complet ([onTripDetails], spec D5).
 * - Déplié (toucher la poignée, ou glisser le panneau vers le haut ; vers le bas pour replier) : destination, « Trajet complet », « Itinéraires ».
 * L'Horizon reste au-dessus, hors du panneau.
 */
@Composable
fun NavigationBottomPanel(
    state: NavigationUiState,
    expanded: Boolean,
    destinationName: String?,
    onToggleExpanded: () -> Unit,
    onToggleMute: () -> Unit,
    onRecenter: () -> Unit,
    onStop: () -> Unit,
    onTripDetails: () -> Unit,
    onAlternatives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    val threshold = with(LocalDensity.current) { DRAG_THRESHOLD.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> drag += delta }
    val toggleLabel = stringResource(if (expanded) R.string.nav_panel_collapse else R.string.nav_panel_expand)
    GlassPanel(
        modifier.fillMaxWidth().draggable(dragState, Orientation.Vertical, onDragStopped = {
            if ((drag < -threshold && !expanded) || (drag > threshold && expanded)) onToggleExpanded()
            drag = 0f
        }),
        shape = RoadlyShapes.Panel, contentPadding = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Poignée : bande touchable sur toute la largeur, centrée sur le panneau ; déplie / replie (comme le glissement vertical).
            Box(
                Modifier.fillMaxWidth().height(HANDLE_BAND_HEIGHT).clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleExpanded),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(36.dp).height(4.dp).background(c.textTertiary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                // Spec D5 : toucher l'ETA ouvre le trajet complet.
                Column(
                    Modifier.weight(1f)
                        .testTag(PANEL_SUMMARY_TAG)
                        .defaultMinSize(minHeight = RoadlySpacing.touchTargetDriving)
                        .clickable(onClickLabel = stringResource(R.string.nav_panel_open_trip), role = Role.Button, onClick = onTripDetails),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatTile(state.etaClock, stringResource(R.string.nav_eta_label))
                        Spacer(Modifier.width(16.dp))
                        StatTile(state.remainingDurationSplit.value, state.remainingDurationSplit.unit)
                        Spacer(Modifier.width(16.dp))
                        StatTile(state.remainingDistanceSplit.value, state.remainingDistanceSplit.unit)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.following) {
                        RoundIconButton(Icons.Default.Navigation, stringResource(R.string.nav_recenter), onClick = onRecenter, style = RoundButtonStyle.Brand)
                    } else {
                        RoundIconButton(
                            if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            stringResource(if (state.muted) R.string.nav_unmute else R.string.nav_mute),
                            onClick = onToggleMute,
                        )
                    }
                    RoundIconButton(Icons.Default.Close, stringResource(R.string.nav_stop), onClick = onStop, style = RoundButtonStyle.Danger)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                    RowDivider(inset = 0.dp)
                    destinationName?.let { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Flag, style = IconTileStyle.BrandFilled, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                OverlineText(stringResource(R.string.nav_destination), color = c.textTertiary, dash = false)
                                Text(name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        GlassButton(stringResource(R.string.nav_trip_details), onClick = onTripDetails, icon = Icons.AutoMirrored.Filled.List, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
                        GlassButton(stringResource(R.string.nav_alternatives), onClick = onAlternatives, icon = Icons.Default.AltRoute, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val samplePanel = NavigationUiState(
    phase = NavigationPhase.GUIDING, etaClock = "23:24",
    remainingDurationSplit = SplitValue("19", "min"), remainingDistanceSplit = SplitValue("4,6", "km"),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 440)
@Composable
private fun BottomPanelNightPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) {
        NavigationBottomPanel(samplePanel, expanded = true, destinationName = "Capitole, Toulouse", onToggleExpanded = {}, onToggleMute = {}, onRecenter = {}, onStop = {}, onTripDetails = {}, onAlternatives = {})
    }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 440)
@Composable
private fun BottomPanelDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) {
        NavigationBottomPanel(samplePanel.copy(following = false), expanded = false, destinationName = null, onToggleExpanded = {}, onToggleMute = {}, onRecenter = {}, onStop = {}, onTripDetails = {}, onAlternatives = {})
    }
}
