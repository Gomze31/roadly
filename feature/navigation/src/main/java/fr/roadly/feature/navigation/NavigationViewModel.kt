package fr.roadly.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.pollNearby
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.Place
import fr.roadly.core.model.ReportKind
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.routing.RouteOption
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
    private val guidance: GuidanceActions,
    private val community: CommunityRepository,
) : ViewModel() {
    val communityEnabled: StateFlow<Boolean> = community.isConfigured.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** Signalements autour de la position projetée (toutes les 60 s), affichés sur la carte. */
    val reports: StateFlow<List<CommunityReport>> = community.pollNearby({ (session.state.value as? NavigationState.Guiding)?.progress?.snapped }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _reportMessage = MutableStateFlow<String?>(null)
    val reportMessage: StateFlow<String?> = _reportMessage

    /** Signalement à la position projetée, avec le cap ; résultat affiché 4 s. */
    fun report(kind: ReportKind) {
        val g = session.state.value as? NavigationState.Guiding ?: return
        viewModelScope.launch {
            _reportMessage.value = when (val r = community.report(kind, g.progress.snapped, g.progress.bearingDeg)) {
                is Outcome.Success -> "sent"
                is Outcome.Failure -> "error:" + r.error.javaClass.simpleName
            }
            delay(4_000)
            _reportMessage.value = null
        }
    }

    /** Signalements projetés sur le tracé courant, devant soi (≤ 60 m du tracé, ≤ 5 km) : alimentent l'Horizon. */
    val reportsAhead: StateFlow<List<ReportAheadUi>> = combine(reports, session.state) { list, s ->
        val g = s as? NavigationState.Guiding ?: return@combine emptyList()
        val geo = RouteGeometry(g.route.geometry)
        list.mapNotNull { r ->
            val p = geo.project(r.point) ?: return@mapNotNull null
            val d = p.alongM - g.progress.distanceAlongM
            if (p.offsetM > 60.0 || d < -50.0 || d > 5_000.0) null else ReportAheadUi(r, d.coerceAtLeast(0.0), Formatters.distance(d.coerceAtLeast(0.0)))
        }.sortedBy { it.distanceM }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val isDark = MutableStateFlow(true)
    private val following = MutableStateFlow(true)
    private var refollowJob: Job? = null
    private var alternativesJob: Job? = null
    private val styleUrl = combine(isDark, config.config) { dark, cfg -> if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl }

    private val _alternatives = MutableStateFlow<AlternativesUi>(AlternativesUi.Hidden)
    val alternativesUi: StateFlow<AlternativesUi> = _alternatives

    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationUiState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun toggleMute() = session.setMuted(!session.muted.value)
    fun stop() = guidance.stop()
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
            when (val r = guidance.alternativesFromHere()) {
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
        if (session.state.value !is NavigationState.Guiding || _addingStop.value) return
        viewModelScope.launch {
            _addingStop.value = true
            (guidance.addStop(place) as? Outcome.Failure)?.let { _alternatives.value = AlternativesUi.Error(it.error) }
            _addingStop.value = false
        }
    }

    /** Bascule le guidage sur [option] : la course est remplacée, la voix et le service continuent. */
    fun switchTo(option: RouteOption) {
        viewModelScope.launch { guidance.select(option) }
        dismissAlternatives()
    }

    companion object { const val RECENTER_AFTER_MS = 8_000L }
}

/** Signalement devant soi : [distanceM] le long du tracé depuis la position projetée. */
data class ReportAheadUi(val report: CommunityReport, val distanceM: Double, val distanceText: String)
