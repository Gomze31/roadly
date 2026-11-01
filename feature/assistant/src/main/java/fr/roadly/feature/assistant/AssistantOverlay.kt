package fr.roadly.feature.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.assistant.AssistantPhase
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import kotlinx.coroutines.delay

/** Fenêtre compacte : état, 1 ligne utilisateur + 1 ligne IA, Confirmer / Annuler, carte de résultat (≤ 3 lignes). */
@Composable
fun AssistantOverlay(modifier: Modifier = Modifier, vm: AssistantViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val state by vm.state.collectAsStateWithLifecycle()
    val card by vm.card.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase, state.message) {
        if (state.phase != AssistantPhase.IDLE) visible = true
        else if (visible || state.message != null) { visible = true; delay(4_000); visible = false }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs)) {
                Text(stringResource(phaseLabel(state.phase)), style = MaterialTheme.typography.labelMedium, color = c.brand)
                if (state.userText.isNotBlank()) {
                    Text(state.userText, maxLines = 1, overflow = TextOverflow.StartEllipsis, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                val reply = state.message ?: state.assistantText
                if (reply.isNotBlank()) {
                    Text(reply, maxLines = 1, overflow = TextOverflow.StartEllipsis, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                }
                if (state.pending != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        GlassButton(stringResource(R.string.assistant_confirm), onClick = vm::confirm, style = GlassButtonStyle.Primary)
                        GlassButton(stringResource(R.string.assistant_cancel), onClick = vm::cancel)
                    }
                }
                card?.let { result ->
                    result.rows.forEach { row ->
                        GlassPanel(Modifier.fillMaxWidth(), onClick = row.placeId?.let { id -> { vm.onCardRow(id) } }, elevated = false) {
                            Text("${row.title} · ${row.detail}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun phaseLabel(phase: AssistantPhase): Int = when (phase) {
    AssistantPhase.CONNECTING -> R.string.assistant_phase_connecting
    AssistantPhase.LISTENING, AssistantPhase.IDLE -> R.string.assistant_phase_listening
    AssistantPhase.THINKING -> R.string.assistant_phase_thinking
    AssistantPhase.SPEAKING -> R.string.assistant_phase_speaking
    AssistantPhase.PAUSED_FOR_GUIDANCE -> R.string.assistant_phase_paused
}
