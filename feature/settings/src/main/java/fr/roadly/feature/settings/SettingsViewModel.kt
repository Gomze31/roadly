package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityConfig
import fr.roadly.core.data.community.CommunityConfigRepository
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.config.normalized
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.model.CritAir
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import fr.roadly.core.model.VehicleDimensions
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.SpeechService
import fr.roadly.core.navigation.SpeechPriority
import fr.roadly.core.navigation.VoiceChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceSettings(
    val enabled: Boolean = GuidanceDefaults.VOICE_ENABLED,
    val volume: Int = GuidanceDefaults.VOICE_VOLUME,
    val toleranceKmh: Int = GuidanceDefaults.SPEED_TOLERANCE_KMH,
    val overSpeedSound: Boolean = GuidanceDefaults.SPEED_ALERT_SOUND,
)

/** Réglages des alertes : zones de vigilance (base locale : nombre, date), bip, pluie. */
data class AlertSettings(
    val vigilanceEnabled: Boolean = GuidanceDefaults.VIGILANCE_ENABLED,
    val vigilanceSound: Boolean = GuidanceDefaults.VIGILANCE_SOUND,
    val rainLowersLimit: Boolean = GuidanceDefaults.RAIN_LOWERS_LIMIT,
    val zoneCount: Int = 0,
    val zonesUpdatedAtMs: Long? = null,
)

sealed interface RefreshState {
    data object Idle : RefreshState
    data object Loading : RefreshState
    data class Done(val count: Int) : RefreshState
    data class Failed(val error: AppError) : RefreshState
}

data class SettingsUiState(
    val vehicle: VehicleProfile? = null,
    val themeMode: ThemeMode = ThemeMode.AUTO_SOLAR,
    val providers: ProviderConfig = ProviderConfig.DEFAULT,
    val voice: VoiceSettings = VoiceSettings(),
    val routePreferences: RoutePreferences = RoutePreferences(),
    val alerts: AlertSettings = AlertSettings(),
)

/** E2 : groupe « Navigation » — vue au départ, zoom, inclinaison, rotation, détail, bâtiments 3D, ligne « puis », économie d'énergie. */
data class NavigationSettings(
    val defaultCameraMode: CameraMode3D = GuidanceDefaults.DEFAULT_CAMERA_MODE,
    val zoomOffset: Int = GuidanceDefaults.ZOOM_OFFSET,
    val pitchPreset: PitchPreset = GuidanceDefaults.PITCH_PRESET,
    val autoRotate: Boolean = GuidanceDefaults.AUTO_ROTATE,
    val mapDetail: MapDetail = GuidanceDefaults.MAP_DETAIL,
    val buildings3d: Boolean = GuidanceDefaults.MAP_3D,
    val showNextManeuverPreview: Boolean = GuidanceDefaults.SHOW_NEXT_MANEUVER,
    val powerSaveMode: PowerSaveMode = GuidanceDefaults.POWER_SAVE,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepo: VehicleProfileRepository,
    private val prefs: UserPreferencesRepository,
    private val config: ProviderConfigRepository,
    private val recents: RecentSearchRepository,
    private val favorites: FavoritesRepository,
    private val speech: SpeechService,
    private val vigilanceRepo: VigilanceRepository,
    private val communityConfig: CommunityConfigRepository,
) : ViewModel() {
    /** Réglages → Communauté : URL et clé du projet Supabase de l'exploitant. */
    val community: StateFlow<CommunityConfig> = communityConfig.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunityConfig())
    fun saveCommunity(url: String, key: String) { viewModelScope.launch { communityConfig.save(CommunityConfig(url, key)) } }
    fun clearCommunity() { viewModelScope.launch { communityConfig.clear() } }

    private val alerts = combine(prefs.vigilanceEnabled, prefs.vigilanceSound, prefs.rainLowersLimit, vigilanceRepo.count, vigilanceRepo.updatedAtMs) { e, s, r, n, u -> AlertSettings(e, s, r, n, u) }
    private val _vigilanceRefresh = MutableStateFlow<RefreshState>(RefreshState.Idle)
    /** État du dernier téléchargement manuel des zones de vigilance. */
    val vigilanceRefresh: StateFlow<RefreshState> = _vigilanceRefresh.asStateFlow()

    /** E2 « Bâtiments 3D » (clé `map.3d`, ex-« Carte 3D en guidage ») ; l'écran passe par [navigation]. */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceDefaults.MAP_3D)
    /** Niveau de détail de la carte en guidage (Normal / Réduit). */
    val mapDetail: StateFlow<MapDetail> = prefs.mapDetail.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceDefaults.MAP_DETAIL)
    /** E2 : réglages du groupe « Navigation », appliqués au prochain guidage. */
    val navigation: StateFlow<NavigationSettings> = combine(
        prefs.defaultCameraMode, prefs.zoomOffset, prefs.pitchPreset, prefs.autoRotate,
        prefs.mapDetail, prefs.map3dEnabled, prefs.showNextManeuverPreview, prefs.powerSaveMode,
    ) { v ->
        NavigationSettings(v[0] as CameraMode3D, v[1] as Int, v[2] as PitchPreset, v[3] as Boolean, v[4] as MapDetail, v[5] as Boolean, v[6] as Boolean, v[7] as PowerSaveMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationSettings())

    fun setDefaultCameraMode(value: CameraMode3D) { viewModelScope.launch { prefs.setDefaultCameraMode(value) } }
    fun setZoomOffset(value: Int) {
        if (value !in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS) return
        viewModelScope.launch { prefs.setZoomOffset(value) }
    }
    fun setPitchPreset(value: PitchPreset) { viewModelScope.launch { prefs.setPitchPreset(value) } }
    fun setAutoRotate(value: Boolean) { viewModelScope.launch { prefs.setAutoRotate(value) } }
    fun setShowNextManeuverPreview(value: Boolean) { viewModelScope.launch { prefs.setShowNextManeuverPreview(value) } }
    fun setPowerSaveMode(value: PowerSaveMode) { viewModelScope.launch { prefs.setPowerSaveMode(value) } }

    private val voice = combine(prefs.voiceEnabled, prefs.voiceVolume, prefs.speedAlertToleranceKmh, prefs.speedAlertSound) { e, v, t, s -> VoiceSettings(e, v, t, s) }

    val uiState: StateFlow<SettingsUiState> = combine(vehicleRepo.profile, prefs.themeMode, config.config, voice, prefs.routePreferences, alerts) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(values[0] as VehicleProfile?, values[1] as ThemeMode, values[2] as ProviderConfig, values[3] as VoiceSettings, values[4] as RoutePreferences, values[5] as AlertSettings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** [consumption] et [price] sont des saisies libres ("7,5", "1.89", ""). Prix vide = inconnu (aucun défaut inventé). */
    fun saveVehicle(type: VehicleType, fuel: FuelType, consumption: String, price: String, dimensions: VehicleDimensions = VehicleDimensions(), critAir: CritAir? = null) {
        val cons = consumption.toFrDouble() ?: return
        val pr = price.toFrDouble()?.takeIf { it > 0 }
        // Le gabarit n'est gardé que pour les véhicules lourds (il n'influence le routage que là).
        val dims = if (type.isHeavy || type == VehicleType.VAN) dimensions else VehicleDimensions()
        viewModelScope.launch { vehicleRepo.save(VehicleProfile(type, fuel, cons, pr, dims, critAir)) }
    }

    fun clearVehicle() { viewModelScope.launch { vehicleRepo.clear() } }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }
    /** Teinte du cockpit (personnalisation) ; VIOLET = défaut (clé retirée). */
    val tint: StateFlow<String?> = prefs.cockpitTint.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setTint(name: String?) { viewModelScope.launch { prefs.setCockpitTint(name) } }
    fun clearHistory() { viewModelScope.launch { recents.clear() } }
    fun clearFavorites() { viewModelScope.launch { favorites.clear() } }

    // ── Voix et sons, alertes de vitesse ──
    fun setVoiceEnabled(enabled: Boolean) { viewModelScope.launch { prefs.setVoiceEnabled(enabled) } }
    fun setVoiceVolume(percent: Int) { viewModelScope.launch { prefs.setVoiceVolume(percent) } }
    fun setOverSpeedSound(enabled: Boolean) { viewModelScope.launch { prefs.setSpeedAlertSound(enabled) } }
    fun setSpeedTolerance(kmh: Int) {
        if (kmh !in GuidanceDefaults.ALLOWED_TOLERANCES_KMH) return
        viewModelScope.launch { prefs.setSpeedAlertToleranceKmh(kmh) }
    }

    /**
     * Prononce [phrase] au volume enregistré, même si la voix du guidage est désactivée (test explicite). E1 : sur le canal de l'assistant,
     * en priorité basse — la sourdine du guidage ne la coupe pas, une annonce du guidage la préempte (essai de l'arbitrage sur appareil).
     */
    fun testVoice(phrase: String) {
        viewModelScope.launch {
            speech.setVolume(prefs.voiceVolume.first())
            speech.speak(phrase, flush = true, priority = SpeechPriority.LOW, channel = VoiceChannel.ASSISTANT)
        }
    }

    // ── Alertes (zones de vigilance, pluie) ──
    fun setVigilanceEnabled(value: Boolean) { viewModelScope.launch { prefs.setVigilanceEnabled(value) } }
    fun setVigilanceSound(value: Boolean) { viewModelScope.launch { prefs.setVigilanceSound(value) } }
    /** E2 « Bâtiments 3D » : la vue au départ, qui suivait l'ancien réglage tant qu'elle n'était pas choisie, est d'abord figée (écart 8). */
    fun setMap3d(value: Boolean) {
        viewModelScope.launch {
            prefs.setDefaultCameraMode(prefs.defaultCameraMode.first())
            prefs.setMap3dEnabled(value)
        }
    }
    fun setMapDetail(value: MapDetail) { viewModelScope.launch { prefs.setMapDetail(value) } }
    fun setRainLowersLimit(value: Boolean) { viewModelScope.launch { prefs.setRainLowersLimit(value) } }
    fun refreshVigilance() {
        if (_vigilanceRefresh.value == RefreshState.Loading) return
        viewModelScope.launch {
            _vigilanceRefresh.value = RefreshState.Loading
            _vigilanceRefresh.value = when (val r = vigilanceRepo.refresh()) {
                is Outcome.Success -> RefreshState.Done(r.value)
                is Outcome.Failure -> RefreshState.Failed(r.error)
            }
        }
    }

    // ── Préférences d'itinéraire ──
    fun setAvoidTolls(value: Boolean) = updateRoutePreferences { it.copy(avoidTolls = value) }
    fun setAvoidHighways(value: Boolean) = updateRoutePreferences { it.copy(avoidHighways = value) }
    fun setAvoidFerries(value: Boolean) = updateRoutePreferences { it.copy(avoidFerries = value) }

    private fun updateRoutePreferences(change: (RoutePreferences) -> RoutePreferences) {
        viewModelScope.launch { prefs.setRoutePreferences(change(prefs.routePreferences.first())) }
    }

    private val _providerError = MutableStateFlow<String?>(null)
    /** URL fautive du dernier enregistrement refusé (null si tout va bien) ; l'écran la formate en « URL invalide : … ». */
    val providerError: StateFlow<String?> = _providerError.asStateFlow()

    fun saveProviders(cfg: ProviderConfig) {
        viewModelScope.launch {
            val clean = runCatching { cfg.normalized() }.onFailure { _providerError.value = it.message ?: "?" }.getOrNull() ?: return@launch
            config.override(clean)
            _providerError.value = null
        }
    }
    fun resetProviders() { viewModelScope.launch { config.reset(); _providerError.value = null } }

    private fun String.toFrDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()
}
