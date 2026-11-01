package fr.roadly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.assistant.settings.KeySource
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Réglages → Roadly AI : activation, consentement aux données, clé (masquée, vérifiable), plafond quotidien, usage du jour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantGroup(vm: AssistantSettingsViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.todayUsage.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val verify by vm.verify.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.settings_group_assistant), footer = stringResource(R.string.settings_assistant_footer)) {
        ToggleRow(stringResource(R.string.settings_assistant_enabled), s.enabled, vm::setEnabled, icon = Icons.Default.Mic)
        ToggleRow(
            stringResource(R.string.settings_assistant_consent), s.consentGranted, vm::setConsent,
            subtitle = stringResource(R.string.settings_assistant_consent_subtitle), icon = Icons.Default.Shield,
        )
        ListRow(
            stringResource(R.string.settings_assistant_key),
            subtitle = stringResource(
                when (source) {
                    KeySource.OVERRIDE -> R.string.settings_assistant_key_override
                    KeySource.BUILD -> R.string.settings_assistant_key_build
                    KeySource.NONE -> R.string.settings_assistant_key_none
                },
            ),
            icon = Icons.Default.Key, onClick = { editing = !editing }, chevron = false, divider = false,
            trailing = { ExpandArrow(editing) },
        )
        ExpandableContent(editing) {
            SettingsField(stringResource(R.string.settings_assistant_key_field), key, { key = it }, KeyboardType.Password, masked = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(stringResource(R.string.settings_save), onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank())
                GlassButton(stringResource(R.string.settings_assistant_verify), onClick = vm::verifyNow, enabled = verify != KeyCheck.Running)
                if (source == KeySource.OVERRIDE) GlassButton(stringResource(R.string.settings_assistant_key_clear), onClick = vm::clearKey, style = GlassButtonStyle.Danger)
            }
            val verifyText = when (val v = verify) {
                KeyCheck.Idle -> null
                KeyCheck.Running -> stringResource(R.string.settings_assistant_verify_running)
                KeyCheck.Ok -> stringResource(R.string.settings_assistant_verify_ok)
                is KeyCheck.Failed -> stringResource(R.string.settings_assistant_verify_failed, v.error.name)
            }
            verifyText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (verify == KeyCheck.Ok) c.success else c.textSecondary) }
        }
        Text(
            stringResource(R.string.settings_assistant_cap), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary,
            modifier = Modifier.padding(start = RoadlySpacing.m, top = RoadlySpacing.s),
        )
        FlowRow(Modifier.padding(horizontal = RoadlySpacing.m), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            listOf(15, 30, 60).forEach { m ->
                GlassChip(stringResource(R.string.settings_assistant_cap_minutes, m), onClick = { vm.setCap(m) }, selected = s.dailyCapMinutes == m)
            }
        }
        Text(
            stringResource(R.string.settings_assistant_usage, (usage.micSeconds / 60).toInt(), s.dailyCapMinutes, usage.tokens.toInt()),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            modifier = Modifier.padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s),
        )
    }
}
