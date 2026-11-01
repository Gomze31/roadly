package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.repository.BatteryReading
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.TripPeriodStats
import fr.roadly.core.navigation.ScooterServices
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.range.EnergyLeg
import fr.roadly.core.scooter.range.RangeCalibration
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import fr.roadly.core.scooter.range.TripCalibration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Fiches trottinette, profil actif, calibration, bilan et réglages de données — totalement séparés du véhicule motorisé. */
data class ScooterSettingsUi(
    val profiles: List<ElectricScooterProfile> = emptyList(),
    val activeId: Long? = null,
    val travelMode: TravelMode? = null,
    val osmEnrichment: Boolean = true,
    val battery: BatteryReading? = null,
    val calibration: RangeCalibration = RangeCalibration(),
    /** Autonomie restante estimée sur le plat à la vitesse de croisière (km), null sans batterie saisie ou capacité. */
    val remainingRangeKm: Double? = null,
    val week: TripPeriodStats? = null,
    val month: TripPeriodStats? = null,
)

@HiltViewModel
class ScooterSettingsViewModel @Inject constructor(private val services: ScooterServices) : ViewModel() {
    init { viewModelScope.launch { services.ensureProfile() } }

    private val m = services.mobility

    val ui: StateFlow<ScooterSettingsUi> = combine(
        combine(services.profiles.profiles, m.activeScooterProfileId, m.travelMode, m.scooterOsmEnrichment, m.scooterBattery) { list, id, mode, osm, battery ->
            ScooterSettingsUi(list, id ?: list.firstOrNull()?.id, mode, osm, battery)
        },
        services.trips.trips,
    ) { base, trips ->
        val active = base.profiles.firstOrNull { it.id == base.activeId }
        val calibration = active?.let { TripCalibration.of(trips, it) } ?: RangeCalibration()
        val remaining = active?.let { p ->
            ScooterRangeEstimator(p, calibration).estimate(listOf(EnergyLeg(1000.0)), base.battery?.pct?.toDouble(), null).remainingRangeKm
        }
        val scooterTrips = trips.filter { it.travelMode == TravelMode.ELECTRIC_SCOOTER }
        val (week, month) = TripPeriodStats.weekAndMonth(scooterTrips, System.currentTimeMillis(), ZoneId.systemDefault())
        base.copy(calibration = calibration, remainingRangeKm = remaining, week = week, month = month)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScooterSettingsUi())

    fun setActive(id: Long) { viewModelScope.launch { m.setActiveScooterProfileId(id) } }
    fun save(profile: ElectricScooterProfile) { viewModelScope.launch { val id = services.profiles.save(profile); if (profile.id == 0L) m.setActiveScooterProfileId(id) } }
    fun delete(id: Long) { viewModelScope.launch { services.profiles.delete(id); if (ui.value.activeId == id) m.setActiveScooterProfileId(null) } }
    fun addPreset() = save(ScooterPresets.NINEBOT_MAX_G2.copy(id = 0))
    fun addBlank() = save(ElectricScooterProfile(name = "Ma trottinette", specSource = "Saisie utilisateur"))
    fun setOsmEnrichment(value: Boolean) { viewModelScope.launch { m.setScooterOsmEnrichment(value) } }
    fun setTravelMode(mode: TravelMode) { viewModelScope.launch { m.setTravelMode(mode) } }
    fun setBattery(pct: Int?) { viewModelScope.launch { m.setScooterBattery(pct?.let { BatteryReading(it, System.currentTimeMillis()) }) } }
}
