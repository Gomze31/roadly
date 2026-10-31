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
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.map.CameraMode3D
import fr.roadly.core.map.CameraSnapshot
import fr.roadly.core.map.MapDetailPolicy
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.Place
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
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
    private val vehicle = vehicleRepo.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val isDark = MutableStateFlow(true)
    private val following = MutableStateFlow(true)
    private var refollowJob: Job? = null
    private var alternativesJob: Job? = null
    private val styleUrl = combine(isDark, config.config) { dark, cfg -> if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl }
    private val routePreferences = prefs.routePreferences.stateIn(viewModelScope, SharingStarted.Eagerly, RoutePreferences())

    private val _alternatives = MutableStateFlow<AlternativesUi>(AlternativesUi.Hidden)
    val alternativesUi: StateFlow<AlternativesUi> = _alternatives

    /** Réglage « Carte 3D en guidage » : mode de départ de la caméra (lu sans attendre par les boutons, d'où `Eagerly`). */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.MAP_3D)

    /** C1 : mode choisi par les boutons pour cette session ; null = mode du réglage. */
    private val cameraChoice = MutableStateFlow<CameraMode3D?>(null)
    /** Mode quitté en passant « nord en haut », rétabli au second appui. */
    private var modeBeforeNorthUp: CameraMode3D = CameraMode3D.FOLLOW_3D

    /** C1 : mode de caméra du guidage (3D, 2D, nord en haut), pour la session seulement. */
    val cameraMode: StateFlow<CameraMode3D> = combine(map3d, cameraChoice) { on, choice -> choice ?: CameraMode3D.default(on) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraMode3D.default(GuidanceDefaults.MAP_3D))

    private fun currentMode(): CameraMode3D = cameraChoice.value ?: CameraMode3D.default(map3d.value)

    /** Bouton 2D / 3D. */
    fun toggleCameraMode() { cameraChoice.value = currentMode().toggled3d() }

    /** Bouton « nord en haut » : bascule, puis retour au mode d'avant. */
    fun toggleNorthUp() {
        val current = currentMode()
        cameraChoice.value = if (current == CameraMode3D.NORTH_UP) modeBeforeNorthUp else { modeBeforeNorthUp = current; CameraMode3D.NORTH_UP }
    }

    /** Dernière caméra de l'écran de guidage, restaurée avec son inclinaison au retour sur l'écran. */
    var lastCamera: CameraSnapshot? = null
        private set

    fun rememberCamera(snapshot: CameraSnapshot?) { if (snapshot != null) lastCamera = snapshot }

    /** C2 : niveau de détail de la carte — réglage, réduit automatiquement au-delà de 90 km/h (retour sous 80 km/h). */
    val mapDetail: StateFlow<MapDetail> = combine(prefs.mapDetail, session.state) { setting, s -> setting to (s as? NavigationState.Guiding)?.speedKmh }
        .scan(MapDetail.NORMAL) { current, (setting, speed) -> MapDetailPolicy.effective(setting, speed, current) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapDetail.NORMAL)

    /** Dernier état produit : hystérésis de l'approche (B1). */
    private var lastUi: NavigationUiState? = null

    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault(), previous = lastUi).also { lastUi = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationUiState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun toggleMute() = session.setMuted(!session.muted.value)
    fun stop() = session.stop()
    fun retry() = session.retry()

    private val _recenterIn = MutableStateFlow<Int?>(null)
    /** C1 : secondes avant la reprise automatique du suivi après un geste ; null = aucun compte à rebours. */
    val recenterInSeconds: StateFlow<Int?> = _recenterIn

    /** L'utilisateur a déplacé la carte : suivi suspendu, repris automatiquement après 8 s (spec §5), compte à rebours affiché. */
    fun onUserGesture() {
        following.value = false
        refollowJob?.cancel()
        refollowJob = viewModelScope.launch {
            for (s in (RECENTER_AFTER_MS / 1_000).toInt() downTo 1) {
                _recenterIn.value = s
                delay(1_000)
            }
            _recenterIn.value = null
            following.value = true
        }
    }

    /** « Annuler » : pas de reprise automatique, la carte reste libre jusqu'à « Recentrer ». */
    fun cancelAutoRecenter() {
        refollowJob?.cancel()
        _recenterIn.value = null
    }

    fun recenter() {
        refollowJob?.cancel()
        _recenterIn.value = null
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

/** Signalement devant soi : [distanceM] le long du tracé depuis la position projetée. */
data class ReportAheadUi(val report: CommunityReport, val distanceM: Double, val distanceText: String)
