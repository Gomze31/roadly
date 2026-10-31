package fr.roadly.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.data.repository.statsSince
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class TripsUiState(val trips: List<Trip> = emptyList(), val month: TripStats = TripStats(0, 0.0, 0, 0.0), val all: TripStats = TripStats(0, 0.0, 0, 0.0))

/** Historique local des trajets et bilan du mois (stockage sur l'appareil seulement). */
@HiltViewModel
class TripsViewModel @Inject constructor(private val repo: TripRepository) : ViewModel() {
    val uiState: StateFlow<TripsUiState> = repo.trips.map { trips ->
        val zone = ZoneId.systemDefault()
        val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        TripsUiState(trips, trips.statsSince(monthStart), trips.statsSince(0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
}
