package fr.roadly.feature.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.rememberVoiceInput
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Bouton micro : reconnaissance vocale du téléphone (hors ligne si possible), puis commande exécutée localement. */
@Composable
fun VoiceCommandButton(modifier: Modifier = Modifier, vm: VoiceViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val listen = rememberVoiceInput(stringResource(R.string.voice_prompt), onText = vm::onText, onUnavailable = vm::onUnavailable)
    RoundIconButton(Icons.Default.Mic, stringResource(R.string.voice_button), onClick = listen, modifier = modifier, size = 56.dp, iconTint = c.brand)
}

/** Bulle : « phrase entendue » puis la réponse (7 s, toucher pour fermer). */
@Composable
fun VoiceReplyOverlay(modifier: Modifier = Modifier, vm: VoiceViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val exchange by vm.exchange.collectAsStateWithLifecycle()
    AnimatedVisibility(exchange != null, modifier = modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val e = exchange ?: return@AnimatedVisibility
        GlassPanel(Modifier.fillMaxWidth().clickable(onClick = vm::dismiss).semantics { liveRegion = LiveRegionMode.Polite }, contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when { e.pending -> Icons.Default.GraphicEq; e.ok -> Icons.Default.CheckCircle; else -> Icons.Default.ErrorOutline },
                    contentDescription = null, tint = if (e.ok) c.brand else c.accent, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(RoadlySpacing.sm))
                Column {
                    if (e.heard.isNotBlank()) Text("« ${e.heard} »", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Text(e.reply ?: stringResource(R.string.voice_working), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                }
            }
        }
    }
}
