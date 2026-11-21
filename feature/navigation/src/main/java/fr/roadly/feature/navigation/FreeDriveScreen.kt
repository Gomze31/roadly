package fr.roadly.feature.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import fr.roadly.core.data.repository.RadarSettingsRepository
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.designsystem.component.DangerZonePill
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.component.SpeedLimitSign
import fr.roadly.core.designsystem.theme.CockpitTint
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.navigation.FreeDriveSession
import fr.roadly.core.navigation.FreeDriveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable object FreeDriveRoute

fun NavGraphBuilder.freeDriveScreen(isDark: Boolean, onExit: () -> Unit) {
    composable<FreeDriveRoute> { FreeDriveScreen(isDark = isDark, onExit = onExit) }
}

@HiltViewModel
class FreeDriveViewModel @Inject constructor(
    private val session: FreeDriveSession,
    config: ProviderConfigRepository,
    prefs: UserPreferencesRepository,
    private val radarSettings: RadarSettingsRepository,
) : ViewModel() {
    /** Fausse alerte signalée : la zone n'est plus annoncée ni affichée. */
    fun hideZone(key: String) { viewModelScope.launch { radarSettings.hideZone(key) } }

    private val isDark = MutableStateFlow(true)
    val state: StateFlow<FreeDriveState> = session.state
    val styleUrl: StateFlow<String> = combine(config.config, isDark) { c, dark -> if (dark) c.mapStyleDarkUrl else c.mapStyleLightUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val toleranceKmh: StateFlow<Int> = prefs.speedAlertToleranceKmh.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceDefaults.SPEED_TOLERANCE_KMH)

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    /** Faux si la conduite libre ne peut pas démarrer (guidage en cours, localisation refusée). */
    fun start(): Boolean = session.start()
    fun stop() = session.stop()
}

/** Zoom et inclinaison de la caméra qui suit le véhicule (pas d'itinéraire : vue un peu plus large que le guidage). */
private const val FREE_DRIVE_ZOOM = 16.3
private const val FREE_DRIVE_TILT = 45.0

/**
 * 1.0 « Conduite libre » : compteur, limitation de la route (OSM), zones de vigilance et relevé du trajet, sans itinéraire.
 * Retour = arrêter. La session continue écran éteint (service de premier plan) jusqu'à « Arrêter ».
 */
@Composable
fun FreeDriveScreen(isDark: Boolean, onExit: () -> Unit, vm: FreeDriveViewModel = hiltViewModel()) {
    // Interface : même clair / sombre que l'app (sombre en permanence sauf choix « Clair ») ; la carte suit [isDark].
    val uiDark = LocalRoadlyColors.current.isDark
    val tint = LocalRoadlyColors.current.brand
    val cockpit = CockpitTint.entries.firstOrNull { (if (uiDark) it.night else it.day).first == tint } ?: CockpitTint.MAGENTA
    RoadlyTheme(darkTheme = uiDark, drivingMode = true, tint = cockpit) { FreeDriveContent(isDark, onExit, vm) }
}

@Composable
private fun FreeDriveContent(isDark: Boolean, onExit: () -> Unit, vm: FreeDriveViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val styleUrl by vm.styleUrl.collectAsStateWithLifecycle()
    val tolerance by vm.toleranceKmh.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    KeepScreenOn()
    BackHandler { vm.stop() }
    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(Unit) { if (!vm.start()) onExit() }
    // Sortie dès que la session n'est plus active (« Arrêter », notification, guidage lancé) — jamais avant son premier état actif.
    var seenActive by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is FreeDriveState.Active) seenActive = true else if (seenActive) onExit()
    }
    val active = state as? FreeDriveState.Active
    val fix = active?.lastFix
    LaunchedEffect(fix) {
        map.setUserLocation(fix)
        if (fix != null) map.moveTo(fix.point, FREE_DRIVE_ZOOM, bearing = (fix.bearingDeg ?: 0f).toDouble(), tilt = FREE_DRIVE_TILT)
    }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1_000); value = System.currentTimeMillis() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val landscape = maxWidth > maxHeight
        if (styleUrl.isNotEmpty()) RoadlyMap(styleUrl = styleUrl, controller = map, modifier = Modifier.fillMaxSize())

        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth(if (landscape) 0.5f else 1f).statusBarsPadding().padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            GlassPanel(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.Speed, style = IconTileStyle.BrandFilled, size = 44.dp)
                    Spacer(Modifier.width(RoadlySpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.free_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Text(
                            active?.roadName ?: stringResource(R.string.free_road_unknown),
                            style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Radar Intelligence : zone en cours (reste à parcourir) ou prochaine zone, avec la fiabilité des données.
            val zone = active?.zone
            val next = active?.nextZone
            var zoneToHide by remember { mutableStateOf<String?>(null) }
            val pillClick = Modifier.clip(RoadlyShapes.Chip).clickable(onClickLabel = stringResource(R.string.nav_zone_false_alarm)) { zoneToHide = zone?.id ?: next?.key }
            AnimatedVisibility(zone != null || next != null) {
                if (zone != null) DangerZonePill(
                    stringResource(R.string.nav_zone_active), stringResource(R.string.nav_danger_remaining, Formatters.distance(zone.distanceM)),
                    zone.limitKmh, zone.confidence, inside = true, modifier = pillClick,
                ) else if (next != null) DangerZonePill(
                    stringResource(R.string.nav_zone_active), stringResource(R.string.nav_danger_in, Formatters.distance(next.distanceM)),
                    next.speedLimitKmh, next.confidence, inside = false, modifier = pillClick,
                )
            }
            zoneToHide?.let { key -> HideZoneDialog(onConfirm = { vm.hideZone(key); zoneToHide = null }, onDismiss = { zoneToHide = null }) }
            AnimatedVisibility(active?.gpsLost == true) {
                GlassPanel(contentPadding = 10.dp, shape = RoadlyShapes.Chip) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GpsOff, contentDescription = null, tint = c.warning)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nav_gps_lost), style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
                    }
                }
            }
        }

        Column(
            Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter).fillMaxWidth(if (landscape) 0.5f else 1f)
                .navigationBarsPadding().padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            SpeedBadge(active?.speedKmh, active?.speedLimitKmh, toleranceKmh = tolerance)
            GlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(Formatters.duration(((now - (active?.startedAtMs ?: now)) / 1000.0).coerceAtLeast(0.0)), stringResource(R.string.free_stat_duration))
                        Stat(Formatters.distance(active?.distanceM ?: 0.0), stringResource(R.string.free_stat_distance))
                        Stat(active?.speedLimitKmh?.let { "$it" } ?: "—", stringResource(R.string.free_stat_limit))
                    }
                    Text(stringResource(R.string.free_sources), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    GlassButton(stringResource(R.string.nav_stop), onClick = vm::stop, style = GlassButtonStyle.Danger, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val c = LocalRoadlyColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
}
