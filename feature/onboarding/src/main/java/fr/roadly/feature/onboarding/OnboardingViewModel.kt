package fr.roadly.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.CritAir
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Réglages réels modifiés par l'accueil : chaque interrupteur écrit directement dans les préférences (pas de copie locale). */
data class OnboardingSettings(
    val route: RoutePreferences = RoutePreferences(),
    val voice: Boolean = true,
    val vigilance: Boolean = true,
    val vigilanceSound: Boolean = true,
    val speedAlertSound: Boolean = false,
    val limitDropVoice: Boolean = true,
    val rainLowersLimit: Boolean = false,
    val breakReminder: Boolean = true,
    val vehicle: VehicleProfile? = null,
    val permission: LocationPermissionState = LocationPermissionState.UNKNOWN,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val vehicles: VehicleProfileRepository,
    location: LocationRepository,
) : ViewModel() {

    /** null tant que la préférence n'est pas lue : l'accueil n'apparaît pas par erreur une fraction de seconde. */
    val done: StateFlow<Boolean?> = prefs.onboardingDone.map<Boolean, Boolean?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<OnboardingSettings> = combine(
        prefs.routePreferences, prefs.voiceEnabled, prefs.vigilanceEnabled, prefs.vigilanceSound, prefs.speedAlertSound,
        prefs.limitDropVoice, prefs.rainLowersLimit, prefs.breakReminder, vehicles.profile, location.permissionState,
    ) { v ->
        OnboardingSettings(
            route = v[0] as RoutePreferences, voice = v[1] as Boolean, vigilance = v[2] as Boolean, vigilanceSound = v[3] as Boolean,
            speedAlertSound = v[4] as Boolean, limitDropVoice = v[5] as Boolean, rainLowersLimit = v[6] as Boolean, breakReminder = v[7] as Boolean,
            vehicle = v[8] as VehicleProfile?, permission = v[9] as LocationPermissionState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingSettings())

    fun setRoute(value: RoutePreferences) = launch { prefs.setRoutePreferences(value) }
    fun setVoice(value: Boolean) = launch { prefs.setVoiceEnabled(value) }
    fun setVigilance(value: Boolean) = launch { prefs.setVigilanceEnabled(value) }
    fun setVigilanceSound(value: Boolean) = launch { prefs.setVigilanceSound(value) }
    fun setSpeedAlertSound(value: Boolean) = launch { prefs.setSpeedAlertSound(value) }
    fun setLimitDropVoice(value: Boolean) = launch { prefs.setLimitDropVoice(value) }
    fun setRainLowersLimit(value: Boolean) = launch { prefs.setRainLowersLimit(value) }
    fun setBreakReminder(value: Boolean) = launch { prefs.setBreakReminder(value) }

    /**
     * Enregistre le véhicule si la consommation est lisible ; le prix, le gabarit et les autres champs déjà saisis sont conservés.
     * Renvoie false (rien n'est écrit) sans consommation valable.
     */
    fun saveVehicle(type: VehicleType, fuel: FuelType, consumption: String, critAir: CritAir?): Boolean {
        val cons = OnboardingFlow.parseConsumption(consumption) ?: return false
        launch {
            val existing = vehicles.profile.first()
            vehicles.save(
                existing?.copy(type = type, fuel = fuel, consumptionPer100 = cons, critAir = critAir ?: existing.critAir)
                    ?: VehicleProfile(type = type, fuel = fuel, consumptionPer100 = cons, fuelPriceEur = null, critAir = critAir),
            )
        }
        return true
    }

    fun finish() = launch { prefs.setOnboardingDone(true) }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}
