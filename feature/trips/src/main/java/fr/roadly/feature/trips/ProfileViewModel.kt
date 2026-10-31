package fr.roadly.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.data.repository.statsSince
import fr.roadly.core.model.CommunityProfile
import fr.roadly.core.model.LeaderboardEntry
import fr.roadly.core.model.Trophies
import fr.roadly.core.model.Trophy
import fr.roadly.core.model.TripStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Partie serveur du profil : absente tant que le service communautaire n'est pas configuré. */
sealed interface ServerSide {
    data object Unconfigured : ServerSide
    data object Loading : ServerSide
    data class Ready(val profile: CommunityProfile, val leaderboard: List<LeaderboardEntry>) : ServerSide
    data class Failed(val error: AppError) : ServerSide
}

data class ProfileUiState(val all: TripStats = TripStats(0, 0.0, 0, 0.0), val trophies: List<Trophy> = emptyList(), val server: ServerSide = ServerSide.Unconfigured)

/** Profil : statistiques et trophées locaux (appareil) + points et classement serveur si configuré. */
@HiltViewModel
class ProfileViewModel @Inject constructor(trips: TripRepository, private val community: CommunityRepository) : ViewModel() {
    private val server = MutableStateFlow<ServerSide>(ServerSide.Unconfigured)

    init {
        viewModelScope.launch {
            community.isConfigured.collect { configured -> if (configured) refresh() else server.value = ServerSide.Unconfigured }
        }
    }

    val uiState: StateFlow<ProfileUiState> = combine(trips.trips, server) { list, s ->
        ProfileUiState(list.statsSince(0), Trophies.compute(list), s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun refresh() {
        viewModelScope.launch {
            server.value = ServerSide.Loading
            val p = community.profile()
            val lb = community.leaderboard()
            server.value = when {
                p is Outcome.Success && lb is Outcome.Success -> ServerSide.Ready(p.value, lb.value)
                p is Outcome.Failure -> ServerSide.Failed(p.error)
                else -> ServerSide.Failed((lb as Outcome.Failure).error)
            }
        }
    }
}
