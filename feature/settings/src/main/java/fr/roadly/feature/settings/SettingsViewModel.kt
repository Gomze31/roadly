package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.config.normalized
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.SpeechService
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

data class SettingsUiState(
    val vehicle: VehicleProfile? = null,
    val themeMode: ThemeMode = ThemeMode.AUTO_SOLAR,
    val providers: ProviderConfig = ProviderConfig.DEFAULT,
    val voice: VoiceSettings = VoiceSettings(),
    val routePreferences: RoutePreferences = RoutePreferences(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepo: VehicleProfileRepository,
    private val prefs: UserPreferencesRepository,
    private val config: ProviderConfigRepository,
    private val recents: RecentSearchRepository,
    private val favorites: FavoritesRepository,
    private val speech: SpeechService,
) : ViewModel() {

    private val voice = combine(prefs.voiceEnabled, prefs.voiceVolume, prefs.speedAlertToleranceKmh, prefs.speedAlertSound) { e, v, t, s -> VoiceSettings(e, v, t, s) }

    val uiState: StateFlow<SettingsUiState> = combine(vehicleRepo.profile, prefs.themeMode, config.config, voice, prefs.routePreferences) { v, t, p, vo, r ->
        SettingsUiState(v, t, p, vo, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** [consumption] et [price] sont des saisies libres ("7,5", "1.89", ""). Prix vide = inconnu (aucun défaut inventé). */
    fun saveVehicle(type: VehicleType, fuel: FuelType, consumption: String, price: String) {
        val cons = consumption.toFrDouble() ?: return
        val pr = price.toFrDouble()?.takeIf { it > 0 }
        viewModelScope.launch { vehicleRepo.save(VehicleProfile(type, fuel, cons, pr)) }
    }

    fun clearVehicle() { viewModelScope.launch { vehicleRepo.clear() } }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }
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

    /** Prononce [phrase] au volume enregistré, même si la voix du guidage est désactivée (test explicite). */
    fun testVoice(phrase: String) {
        viewModelScope.launch {
            speech.setVolume(prefs.voiceVolume.first())
            speech.speak(phrase, flush = true)
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
