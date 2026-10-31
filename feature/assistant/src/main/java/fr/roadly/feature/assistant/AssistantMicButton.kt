package fr.roadly.feature.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.assistant.AssistantPhase
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.theme.LocalRoadlyColors

/** Bouton micro de la colonne d'actions : ouvre / ferme Roadly AI ; halo animé selon l'état et le niveau sonore. */
@Composable
fun AssistantMicButton(modifier: Modifier = Modifier, vm: AssistantViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mic by vm.micLevel.collectAsStateWithLifecycle()
    val voice by vm.voiceLevel.collectAsStateWithLifecycle()
    val consent by vm.consentGranted.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var askConsent by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (consent == true) vm.onMic() else askConsent = true }
    }
    val active = state.phase != AssistantPhase.IDLE
    Box(modifier, contentAlignment = Alignment.Center) {
        AssistantHalo(state.phase, mic, voice)
        RoundIconButton(
            if (state.phase == AssistantPhase.SPEAKING) Icons.Default.GraphicEq else Icons.Default.Mic,
            stringResource(if (active) R.string.assistant_mic_stop else R.string.assistant_mic),
            onClick = {
                when {
                    active -> vm.onMic()
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    consent != true -> askConsent = true
                    else -> vm.onMic()
                }
            },
            style = if (active) RoundButtonStyle.Brand else RoundButtonStyle.Glass,
        )
    }
    if (askConsent) {
        AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text(stringResource(R.string.assistant_consent_title)) },
            text = { Text(stringResource(R.string.assistant_consent_text)) },
            confirmButton = { TextButton(onClick = { askConsent = false; vm.grantConsentAndStart() }) { Text(stringResource(R.string.assistant_consent_accept)) } },
            dismissButton = { TextButton(onClick = { askConsent = false }) { Text(stringResource(R.string.assistant_consent_refuse)) } },
        )
    }
}

@Composable
private fun AssistantHalo(phase: AssistantPhase, mic: Float, voice: Float) {
    val c = LocalRoadlyColors.current
    val target = when (phase) {
        AssistantPhase.LISTENING -> mic
        AssistantPhase.SPEAKING -> voice
        else -> 0f
    }
    // La parole tourne autour de 0,05-0,2 en RMS : amplifiée pour être visible.
    val level by animateFloatAsState((target * 4f).coerceIn(0f, 1f), label = "assistantLevel")
    val angle by rememberInfiniteTransition(label = "assistantSpin")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "assistantAngle")
    Canvas(Modifier.size(84.dp)) {
        val r = size.minDimension / 2
        when (phase) {
            AssistantPhase.IDLE -> Unit
            AssistantPhase.CONNECTING, AssistantPhase.THINKING ->
                drawArc(c.brand, angle, 110f, useCenter = false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            AssistantPhase.PAUSED_FOR_GUIDANCE -> drawCircle(c.textTertiary.copy(alpha = 0.35f), radius = r * 0.8f)
            AssistantPhase.LISTENING, AssistantPhase.SPEAKING -> drawCircle(c.brand.copy(alpha = 0.18f + 0.3f * level), radius = r * (0.72f + 0.28f * level))
        }
    }
}
