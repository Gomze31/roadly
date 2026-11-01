package fr.roadly.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/**
 * E3 : « Reprendre le guidage vers … » (course interrompue par la mort du processus ou un redémarrage) ; « Ignorer » l'oublie.
 * [canResume] : position connue (l'itinéraire part de la position actuelle).
 */
@Composable
internal fun ResumeGuidanceCard(card: ResumeCardUi, canResume: Boolean, onResume: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth()) {
        Text(
            card.destinationName?.let { stringResource(R.string.home_resume_last_to, it) } ?: stringResource(R.string.home_resume_last),
            style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val failed = card.phase == ResumePhase.FAILED
        val status = when {
            failed -> R.string.home_resume_failed
            !canResume -> R.string.home_resume_waiting_position
            else -> null
        }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = if (failed) c.danger else c.textSecondary) }
        Spacer(Modifier.height(RoadlySpacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            val routing = card.phase == ResumePhase.ROUTING
            GlassButton(
                stringResource(if (routing) R.string.home_resume_routing else R.string.home_resume_action),
                onClick = onResume, icon = Icons.Default.Navigation, style = GlassButtonStyle.Brand,
                enabled = canResume && !routing, modifier = Modifier.weight(1f),
            )
            GlassButton(stringResource(R.string.home_resume_dismiss), onClick = onDismiss, style = GlassButtonStyle.Ghost)
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun ResumeGuidanceCardNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassPanel { ResumeGuidanceCard(ResumeCardUi("Capitole"), canResume = true, onResume = {}, onDismiss = {}) } }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun ResumeGuidanceCardDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassPanel { ResumeGuidanceCard(ResumeCardUi("Capitole", ResumePhase.FAILED), canResume = true, onResume = {}, onDismiss = {}) } }
}
