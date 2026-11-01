package fr.roadly.core.data.testing

import fr.roadly.core.data.repository.BatteryReading
import fr.roadly.core.data.repository.MobilityPreferencesRepository
import fr.roadly.core.data.repository.ScooterProfileRepository
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Double de test en mémoire de [MobilityPreferencesRepository]. */
class FakeMobilityPreferencesRepository(mode: TravelMode? = null) : MobilityPreferencesRepository {
    val modeState = MutableStateFlow(mode)
    val prefsState = MutableStateFlow(ScooterRoutePreferences())
    val profileState = MutableStateFlow<Long?>(null)
    val batteryState = MutableStateFlow<BatteryReading?>(null)
    val osmState = MutableStateFlow(true)
    override val travelMode = modeState
    override suspend fun setTravelMode(mode: TravelMode) { modeState.value = mode }
    override val scooterRoutePreferences = prefsState
    override suspend fun setScooterRoutePreferences(value: ScooterRoutePreferences) { prefsState.value = value }
    override val activeScooterProfileId = profileState
    override suspend fun setActiveScooterProfileId(id: Long?) { profileState.value = id }
    override val scooterBattery = batteryState
    override suspend fun setScooterBattery(reading: BatteryReading?) { batteryState.value = reading }
    override val scooterOsmEnrichment = osmState
    override suspend fun setScooterOsmEnrichment(value: Boolean) { osmState.value = value }
}

/** Double de test en mémoire de [ScooterProfileRepository]. */
class FakeScooterProfileRepository(initial: List<ElectricScooterProfile> = emptyList()) : ScooterProfileRepository {
    val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1
    override val profiles = state.map { it }
    override suspend fun save(profile: ElectricScooterProfile): Long {
        val id = if (profile.id == 0L) nextId++ else profile.id
        state.value = state.value.filterNot { it.id == id } + profile.copy(id = id)
        return id
    }
    override suspend fun delete(id: Long) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun seedIfEmpty(preset: ElectricScooterProfile): Long? = if (state.value.isEmpty()) save(preset.copy(id = 0)) else null
}
