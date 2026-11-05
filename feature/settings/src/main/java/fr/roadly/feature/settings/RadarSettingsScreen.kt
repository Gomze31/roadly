package fr.roadly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.RadarFamily
import fr.roadly.core.data.repository.RadarSettings
import fr.roadly.core.data.repository.RadarSettingsRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.vigilance.RadarSyncStatus
import fr.roadly.core.data.vigilance.RadarVersion
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.RoadlyTopBar
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.radar.RadarSourceIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** État de l'écran Radar Intelligence. */
data class RadarScreenState(
    val enabled: Boolean = GuidanceDefaults.VIGILANCE_ENABLED,
    val sound: Boolean = GuidanceDefaults.VIGILANCE_SOUND,
    val settings: RadarSettings = RadarSettings(),
    val activeCount: Int = 0,
    /** Dernière version appliquée (qualité de la base) et dernière vérification (fraîcheur). */
    val lastApplied: RadarVersion? = null,
    val lastCheck: RadarVersion? = null,
)

@HiltViewModel
class RadarSettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val radar: RadarSettingsRepository,
    private val repo: VigilanceRepository,
) : ViewModel() {
    val state: StateFlow<RadarScreenState> = combine(prefs.vigilanceEnabled, prefs.vigilanceSound, radar.settings, repo.count, repo.versions) { e, s, set, n, versions ->
        RadarScreenState(e, s, set, n, versions.firstOrNull { it.status == RadarSyncStatus.APPLIED }, versions.firstOrNull())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadarScreenState())

    private val _sync = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val sync: StateFlow<RefreshState> = _sync.asStateFlow()

    fun setEnabled(v: Boolean) { viewModelScope.launch { prefs.setVigilanceEnabled(v) } }
    fun setSound(v: Boolean) { viewModelScope.launch { prefs.setVigilanceSound(v) } }
    fun setVoice(v: Boolean) { viewModelScope.launch { radar.setVoice(v) } }
    fun setPrewarn(m: Int) { viewModelScope.launch { radar.setPrewarnM(m) } }
    fun setExit(v: Boolean) { viewModelScope.launch { radar.setAnnounceExit(v) } }
    fun setFamily(f: RadarFamily, v: Boolean) { viewModelScope.launch { radar.setFamily(f, v) } }
    fun showHiddenZones() { viewModelScope.launch { radar.clearHiddenZones() } }

    /** Synchronisation manuelle (téléchargement forcé) ; le résultat détaillé arrive dans le journal. */
    fun syncNow() {
        if (_sync.value == RefreshState.Loading) return
        viewModelScope.launch {
            _sync.value = RefreshState.Loading
            _sync.value = when (val r = repo.refresh()) {
                is Outcome.Success -> RefreshState.Done(r.value)
                is Outcome.Failure -> RefreshState.Failed(r.error)
            }
        }
    }
}

/**
 * Radar Intelligence : activation, voix, bip, préavis, catégories, base locale (nombre, sources, qualité, fraîcheur) et
 * synchronisation. Tout fonctionne hors connexion une fois la base téléchargée.
 */
@Composable
fun RadarSettingsScreen(onBack: () -> Unit, vm: RadarSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE)
    RoadlyScreen {
        RoadlyTopBar(stringResource(R.string.radar_title), onBack = onBack, backDescription = stringResource(R.string.settings_back))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(stringResource(R.string.radar_group_alerts), footer = stringResource(R.string.radar_legal_footer)) {
                ToggleRow(stringResource(R.string.radar_enabled), checked = s.enabled, onCheckedChange = vm::setEnabled, icon = Icons.Default.Shield, iconStyle = IconTileStyle.Warning, subtitle = stringResource(R.string.radar_enabled_subtitle))
                ToggleRow(stringResource(R.string.radar_voice), checked = s.settings.voice, onCheckedChange = vm::setVoice, icon = Icons.Default.RecordVoiceOver, enabled = s.enabled, subtitle = stringResource(R.string.radar_voice_subtitle))
                ToggleRow(stringResource(R.string.radar_sound), checked = s.sound, onCheckedChange = vm::setSound, icon = Icons.AutoMirrored.Filled.VolumeUp, enabled = s.enabled)
                ToggleRow(stringResource(R.string.radar_exit), checked = s.settings.announceExit, onCheckedChange = vm::setExit, icon = Icons.Default.ExitToApp, enabled = s.enabled, divider = false)
            }
            SettingsGroup(stringResource(R.string.radar_group_prewarn), footer = stringResource(R.string.radar_prewarn_footer)) {
                SegmentedControl(
                    options = RadarSettings.PREWARN_CHOICES, selected = s.settings.prewarnM, onSelect = vm::setPrewarn,
                    label = { if (it == 0) "Aucun" else if (it >= 1000) "${it / 1000} km" else "$it m" },
                    modifier = Modifier.fillMaxWidth().padding(RoadlySpacing.m),
                )
            }
            SettingsGroup(stringResource(R.string.radar_group_kinds), footer = stringResource(R.string.radar_kinds_footer)) {
                val rows = listOf(
                    Triple(RadarFamily.SPEED, R.string.radar_kind_speed, Icons.Default.Speed),
                    Triple(RadarFamily.SECTION, R.string.radar_kind_section, Icons.Default.Timeline),
                    Triple(RadarFamily.RED_LIGHT, R.string.radar_kind_red_light, Icons.Default.Traffic),
                    Triple(RadarFamily.LEVEL_CROSSING, R.string.radar_kind_level_crossing, Icons.Default.Train),
                )
                rows.forEachIndexed { i, (family, label, icon) ->
                    ToggleRow(stringResource(label), checked = family in s.settings.families, onCheckedChange = { vm.setFamily(family, it) }, icon = icon, enabled = s.enabled, divider = i < rows.lastIndex)
                }
            }
            // Fausses alertes signalées depuis la pastille (téléphone) ou l'alerte Android Auto.
            SettingsGroup(stringResource(R.string.radar_group_hidden), footer = stringResource(R.string.radar_hidden_footer)) {
                val hidden = s.settings.hiddenZones.size
                ListRow(
                    pluralStringResource(R.plurals.radar_hidden_count, hidden, hidden),
                    subtitle = if (hidden > 0) stringResource(R.string.radar_hidden_reset) else null,
                    icon = Icons.Default.VisibilityOff, iconStyle = IconTileStyle.Neutral,
                    onClick = if (hidden > 0) vm::showHiddenZones else null, chevron = false, divider = false,
                )
            }
            SettingsGroup(stringResource(R.string.radar_group_base), footer = stringResource(R.string.radar_sources_footer, RadarSourceIds.ATTRIBUTION_FR)) {
                val applied = s.lastApplied
                ListRow(
                    stringResource(R.string.radar_count, s.activeCount),
                    subtitle = applied?.sourceModified?.take(10)?.let { stringResource(R.string.radar_source_version, it) } ?: stringResource(R.string.radar_legacy_base),
                    icon = Icons.Default.Dns, iconStyle = IconTileStyle.Neutral,
                )
                if (applied != null && applied.total > 0) {
                    fun pct(n: Int) = n * 100 / applied.total
                    ListRow(
                        stringResource(R.string.radar_quality),
                        subtitle = stringResource(R.string.radar_quality_detail, pct(applied.highPrecision), pct(applied.withDirection), pct(applied.enriched)),
                        icon = Icons.Default.Verified, iconStyle = IconTileStyle.Brand,
                    )
                    ListRow(
                        stringResource(R.string.radar_changes),
                        subtitle = stringResource(R.string.radar_changes_detail, applied.diff.added, applied.diff.removed, applied.diff.moved, applied.diff.limitChanged),
                        icon = Icons.Default.Info, iconStyle = IconTileStyle.Neutral,
                    )
                }
                val check = s.lastCheck
                val status = when {
                    sync == RefreshState.Loading -> stringResource(R.string.radar_sync_loading)
                    sync is RefreshState.Failed -> stringResource(R.string.radar_sync_failed)
                    check == null -> stringResource(R.string.radar_sync_never)
                    else -> stringResource(
                        when (check.status) {
                            RadarSyncStatus.APPLIED -> R.string.radar_status_applied
                            RadarSyncStatus.UNCHANGED -> R.string.radar_status_unchanged
                            RadarSyncStatus.REJECTED -> R.string.radar_status_rejected
                            RadarSyncStatus.FAILED -> R.string.radar_status_failed
                        },
                        df.format(Date(check.fetchedAtMs)),
                    ) + (check.message?.takeIf { check.status == RadarSyncStatus.REJECTED }?.let { " ($it)" } ?: "")
                }
                ListRow(
                    stringResource(R.string.radar_sync), subtitle = status, icon = Icons.Default.Refresh, iconStyle = IconTileStyle.Brand,
                    onClick = vm::syncNow, chevron = false, enabled = sync != RefreshState.Loading, divider = false,
                    trailing = { if (sync == RefreshState.Loading) CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Icon(Icons.Default.Refresh, null, tint = c.brandBright, modifier = Modifier.size(20.dp)) },
                )
            }
            Text(stringResource(R.string.radar_offline_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(horizontal = RoadlySpacing.m))
        }
    }
}
