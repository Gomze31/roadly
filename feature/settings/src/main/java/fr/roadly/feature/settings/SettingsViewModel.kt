package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val vehicle: VehicleProfile? = null,
    val themeMode: ThemeMode = ThemeMode.AUTO_SOLAR,
    val providers: ProviderConfig = ProviderConfig.DEFAULT,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepo: VehicleProfileRepository,
    private val prefs: UserPreferencesRepository,
    private val config: ProviderConfigRepository,
    private val recents: RecentSearchRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(vehicleRepo.profile, prefs.themeMode, config.config) { v, t, p -> SettingsUiState(v, t, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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
    fun saveProviders(cfg: ProviderConfig) { viewModelScope.launch { config.override(cfg) } }
    fun resetProviders() { viewModelScope.launch { config.reset() } }

    private fun String.toFrDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()
}
