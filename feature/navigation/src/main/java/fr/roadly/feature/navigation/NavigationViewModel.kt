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
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.CommunityReport
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
import fr.roadly.core.data.repository.BatteryReading
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Trip
import fr.roadly.core.navigation.ScooterGuidanceConfig
import fr.roadly.core.navigation.ScooterServices
import fr.roadly.core.routing.ScooterRouteOptionsUseCase
import fr.roadly.core.scooter.assistant.AssistantAction
import fr.roadly.core.scooter.assistant.ScooterAssistantContext
import fr.roadly.core.scooter.osm.ScooterPoi
import fr.roadly.core.scooter.osm.ScooterPoiKind
import kotlinx.coroutines.flow.first
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
    private val community: CommunityRepository,
    /** Services du mode trottinette ; null (tests existants) = guidage voiture / moto inchangé. */
    private val scooter: ScooterServices? = null,
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
            val sc = scooter
            val cfg = session.scooterConfig
            val result = if (sc != null && cfg != null) {
                // Trottinette : variantes trottinette depuis la position (jamais les variantes voiture).
                sc.options(scooterParams(sc, cfg, g.progress.snapped, g.route.geometry.last(), emptyList()))
            } else alternatives(g.progress.snapped, g.route.geometry.last(), vehicle.value, System.currentTimeMillis(), routePreferences.value)
            when (val r = result) {
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
            val cfg = session.scooterConfig
            val request = RouteRequest(
                origin = g.progress.snapped, destination = g.route.geometry.last(), waypoints = listOf(place.point),
                costing = g.route.costing, preferences = if (cfg != null) RoutePreferences() else routePreferences.value,
                originHeadingDeg = g.progress.bearingDeg, vehicle = if (cfg != null) null else vehicle.value, scooter = cfg?.request,
            )
            when (val r = routing.route(request)) {
                is Outcome.Success -> r.value.firstOrNull()?.let { route ->
                    val cfgNow = session.scooterConfig?.let { it.copy(batteryPct = g.scooter?.range?.batteryNowPct ?: it.batteryPct) }
                    session.start(RouteOption(route.copy(costing = g.route.costing), "Via ${place.name}", System.currentTimeMillis() + (route.durationSeconds * 1000).toLong(), null, sameAsFastest = false), routePreferences.value, vehicle.value, cfgNow)
                }
                is Outcome.Failure -> _alternatives.value = AlternativesUi.Error(r.error)
            }
            _addingStop.value = false
        }
    }

    /** Bascule le guidage sur [option] : la course est remplacée, la voix et le service continuent. */
    fun switchTo(option: RouteOption) {
        val g = session.state.value as? NavigationState.Guiding
        val cfg = session.scooterConfig?.let { it.copy(batteryPct = g?.scooter?.range?.batteryNowPct ?: it.batteryPct) }
        if (cfg != null) session.start(option, RoutePreferences(), null, cfg) else session.start(option, routePreferences.value, vehicle.value)
        dismissAlternatives()
    }

    // ── Mode trottinette ──
    val scooterMode: Boolean get() = session.scooterConfig != null

    private suspend fun scooterParams(sc: ScooterServices, cfg: ScooterGuidanceConfig, from: GeoPoint, to: GeoPoint, waypoints: List<GeoPoint>): ScooterRouteOptionsUseCase.Params {
        val g = session.state.value as? NavigationState.Guiding
        return ScooterRouteOptionsUseCase.Params(
            from, to, cfg.request.profile, sc.mobility.scooterRoutePreferences.first(), System.currentTimeMillis(), waypoints,
            batteryPct = g?.scooter?.range?.batteryNowPct ?: cfg.batteryPct, temperatureC = cfg.temperatureC, calibration = cfg.calibration, withOsmTags = cfg.withOsmTags,
        )
    }

    /** Niveau de batterie lu sur la trottinette et saisi en route : devient la référence de l'estimation. */
    fun setBattery(pct: Int?) {
        val sc = scooter ?: return
        if (pct == null) return
        session.setScooterBattery(pct.toDouble())
        viewModelScope.launch { sc.mobility.setScooterBattery(BatteryReading(pct.coerceIn(0, 100), System.currentTimeMillis())) }
    }

    private val _assistantReply = MutableStateFlow<String?>(null)
    val assistantReply: StateFlow<String?> = _assistantReply

    /** Roadly AI : réponse calculée sur l'état réel du guidage et du profil, prononcée puis affichée 8 s. */
    fun onAssistantText(text: String) {
        val sc = scooter ?: return
        val g = session.state.value as? NavigationState.Guiding
        viewModelScope.launch {
            val ui = g?.scooter
            val range = ui?.range
            val estimate = range?.let { r ->
                fr.roadly.core.scooter.range.RangeEstimate(r.risk, 0.0, 0.0, null, r.batteryNowPct, r.arrivalPct, r.arrivalPct, r.remainingRangeKm, session.scooterConfig?.calibration ?: fr.roadly.core.scooter.range.RangeCalibration(), emptyList())
            }
            val ctx = ScooterAssistantContext(
                preferences = sc.mobility.scooterRoutePreferences.first(), rangeToDestination = estimate,
                nextCycleInfrastructure = ui?.nextCycleInfrastructure, guiding = g != null, analysisAvailable = ui?.analysisAvailable == true,
            )
            val reply = sc.assistant(text, ctx, g?.progress?.snapped, session)
            sc.speech.speak(reply.speech, flush = true)
            _assistantReply.value = reply.speech
            when (val a = reply.action) {
                is AssistantAction.ApplyPreferences -> if (g != null) loadAlternatives()
                is AssistantAction.SearchWithinRange -> searchPois(ScooterPoiKind.CHARGING, a.maxDistanceM.toInt())
                else -> Unit
            }
            delay(8_000)
            if (_assistantReply.value == reply.speech) _assistantReply.value = null
        }
    }

    private val _pois = MutableStateFlow<PoisUi>(PoisUi.Hidden)
    val pois: StateFlow<PoisUi> = _pois

    /** Parkings près de l'arrivée, ou bornes autour de soi (rayon [radiusM]) ; uniquement les lieux présents dans OSM. */
    fun searchPois(kind: ScooterPoiKind, radiusM: Int? = null) {
        val sc = scooter ?: return
        val g = session.state.value as? NavigationState.Guiding ?: return
        val center = if (kind == ScooterPoiKind.PARKING) g.route.geometry.last() else g.progress.snapped
        _pois.value = PoisUi.Loading(kind)
        viewModelScope.launch {
            _pois.value = when (val r = sc.pois.around(kind, center, radiusM ?: if (kind == ScooterPoiKind.PARKING) 400 else 3_000)) {
                is Outcome.Success -> PoisUi.Content(kind, r.value.take(20).map { PoiUi(it, it.point.distanceTo(center)) })
                is Outcome.Failure -> PoisUi.Error(kind, r.error)
            }
        }
    }
    fun dismissPois() { _pois.value = PoisUi.Hidden }

    /** Nouvelle destination (lieu trouvé dans OSM) : itinéraire trottinette depuis la position, guidage remplacé. */
    fun navigateToPoi(poi: PoiUi) {
        val sc = scooter ?: return
        val cfg = session.scooterConfig ?: return
        val g = session.state.value as? NavigationState.Guiding ?: return
        dismissPois()
        viewModelScope.launch {
            when (val r = sc.options(scooterParams(sc, cfg, g.progress.snapped, poi.poi.point, emptyList()).copy(variants = 1))) {
                is Outcome.Success -> r.value.firstOrNull()?.let { session.start(it.copy(label = poi.poi.name ?: "Lieu OSM"), RoutePreferences(), null, cfg.copy(batteryPct = g.scooter?.range?.batteryNowPct ?: cfg.batteryPct)) }
                is Outcome.Failure -> _alternatives.value = AlternativesUi.Error(r.error)
            }
        }
    }

    /** Dernier trajet enregistré (écran récapitulatif après l'arrivée). */
    val lastTrip: StateFlow<Trip?> = (scooter?.tripRecorder?.lastSaved ?: MutableStateFlow(null)).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Batterie lue à l'arrivée : enregistrée avec le trajet pour calibrer les prochaines estimations. */
    fun setArrivalBattery(pct: Int?) {
        val sc = scooter ?: return
        val trip = lastTrip.value ?: return
        viewModelScope.launch {
            sc.trips.setBatteryEnd(trip.id, pct)
            if (pct != null) sc.mobility.setScooterBattery(BatteryReading(pct, System.currentTimeMillis()))
        }
    }

    companion object { const val RECENTER_AFTER_MS = 8_000L }
}

/** Lieu trottinette trouvé dans OSM, avec sa distance au point de recherche. */
data class PoiUi(val poi: ScooterPoi, val distanceM: Double)

sealed interface PoisUi {
    data object Hidden : PoisUi
    data class Loading(val kind: ScooterPoiKind) : PoisUi
    data class Content(val kind: ScooterPoiKind, val items: List<PoiUi>) : PoisUi
    data class Error(val kind: ScooterPoiKind, val error: AppError) : PoisUi
}

/** Signalement devant soi : [distanceM] le long du tracé depuis la position projetée. */
data class ReportAheadUi(val report: CommunityReport, val distanceM: Double, val distanceText: String)
