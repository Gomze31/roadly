package fr.roadly.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.Place
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Itinéraires alternatifs calculés en cours de trajet (depuis la position projetée vers la destination). */
sealed interface AlternativesUi {
    data object Hidden : AlternativesUi
    data object Loading : AlternativesUi
    data class Content(val options: List<RouteOption>, val currentRouteId: String) : AlternativesUi
    data class Error(val error: AppError) : AlternativesUi
}

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val session: NavigationSession,
    prefs: UserPreferencesRepository,
    config: ProviderConfigRepository,
    private val alternatives: RouteAlternativesUseCase,
    private val routing: RoutingProvider,
    vehicleRepo: VehicleProfileRepository,
) : ViewModel() {
    private val vehicle = vehicleRepo.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val isDark = MutableStateFlow(true)
    private val following = MutableStateFlow(true)
    private var refollowJob: Job? = null
    private var alternativesJob: Job? = null
    private val styleUrl = combine(isDark, config.config) { dark, cfg -> if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl }
    private val routePreferences = prefs.routePreferences.stateIn(viewModelScope, SharingStarted.Eagerly, RoutePreferences())

    private val _alternatives = MutableStateFlow<AlternativesUi>(AlternativesUi.Hidden)
    val alternativesUi: StateFlow<AlternativesUi> = _alternatives

    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationUiState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun toggleMute() = session.setMuted(!session.muted.value)
    fun stop() = session.stop()
    fun retry() = session.retry()

    /** L'utilisateur a déplacé la carte : suivi suspendu, repris automatiquement après 8 s (spec §5). */
    fun onUserGesture() {
        following.value = false
        refollowJob?.cancel()
        refollowJob = viewModelScope.launch {
            delay(RECENTER_AFTER_MS)
            following.value = true
        }
    }

    fun recenter() {
        refollowJob?.cancel()
        following.value = true
    }

    /** Calcule rapide / court / sans péage depuis la position projetée jusqu'à la destination courante. */
    fun loadAlternatives() {
        val g = session.state.value as? NavigationState.Guiding ?: return
        alternativesJob?.cancel()
        _alternatives.value = AlternativesUi.Loading
        alternativesJob = viewModelScope.launch {
            when (val r = alternatives(g.progress.snapped, g.route.geometry.last(), vehicle.value, System.currentTimeMillis(), routePreferences.value)) {
                is Outcome.Success -> _alternatives.value = AlternativesUi.Content(r.value, g.route.id)
                is Outcome.Failure -> _alternatives.value = AlternativesUi.Error(r.error)
            }
        }
    }

    fun dismissAlternatives() { alternativesJob?.cancel(); _alternatives.value = AlternativesUi.Hidden }

    private val _addingStop = MutableStateFlow(false)
    /** Calcul en cours d'un itinéraire via l'arrêt ajouté. */
    val addingStop: StateFlow<Boolean> = _addingStop

    /** Ajoute un arrêt en cours de trajet : position projetée → arrêt → destination courante, puis bascule du guidage. */
    fun addStop(place: Place) {
        val g = session.state.value as? NavigationState.Guiding ?: return
        if (_addingStop.value) return
        viewModelScope.launch {
            _addingStop.value = true
            val request = RouteRequest(
                origin = g.progress.snapped, destination = g.route.geometry.last(), waypoints = listOf(place.point),
                costing = g.route.costing, preferences = routePreferences.value, originHeadingDeg = g.progress.bearingDeg, vehicle = vehicle.value,
            )
            when (val r = routing.route(request)) {
                is Outcome.Success -> r.value.firstOrNull()?.let { route ->
                    session.start(RouteOption(route.copy(costing = g.route.costing), "Via ${place.name}", System.currentTimeMillis() + (route.durationSeconds * 1000).toLong(), null, sameAsFastest = false), routePreferences.value, vehicle.value)
                }
                is Outcome.Failure -> _alternatives.value = AlternativesUi.Error(r.error)
            }
            _addingStop.value = false
        }
    }

    /** Bascule le guidage sur [option] : la course est remplacée, la voix et le service continuent. */
    fun switchTo(option: RouteOption) {
        session.start(option, routePreferences.value, vehicle.value)
        dismissAlternatives()
    }

    companion object { const val RECENTER_AFTER_MS = 8_000L }
}
