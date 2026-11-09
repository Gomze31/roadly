package fr.roadly.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.pollNearby
import fr.roadly.core.data.traffic.asReport
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.map.CameraSnapshot
import fr.roadly.core.map.MapDetailPolicy
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import fr.roadly.core.model.Place
import fr.roadly.core.model.ReportKind
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.data.repository.DefaultRadarSettingsRepository
import fr.roadly.core.data.repository.RadarSettingsRepository
import fr.roadly.core.navigation.NavigationState
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
import fr.roadly.core.navigation.reportsAhead
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val guidance: GuidanceActions,
    private val community: CommunityRepository,
    /** Services du mode trottinette ; null (tests existants) = guidage voiture / moto inchangé. */
    private val scooter: ScooterServices? = null,
    /** Fausses alertes (zones masquées) ; défaut immuable pour les tests existants. */
    private val radarSettings: RadarSettingsRepository = DefaultRadarSettingsRepository,
    /** Incidents officiels Bison Futé ; null (tests existants) = aucun. */
    private val traffic: fr.roadly.core.data.traffic.TrafficSource? = null,
) : ViewModel() {
    /** Zone de danger dont l'utilisateur veut signaler une fausse alerte (confirmation affichée) ; null = aucune. */
    private val _zoneToHide = MutableStateFlow<String?>(null)
    val zoneToHide: StateFlow<String?> = _zoneToHide

    /** Appui sur la pastille : zone traversée, sinon la prochaine. */
    fun askHideZone() {
        val g = session.state.value as? NavigationState.Guiding ?: return
        _zoneToHide.value = g.radarActive?.key ?: g.radarNext?.key
    }

    fun confirmHideZone() {
        val key = _zoneToHide.value ?: return
        _zoneToHide.value = null
        viewModelScope.launch { radarSettings.hideZone(key) }
    }

    fun cancelHideZone() { _zoneToHide.value = null }
    val communityEnabled: StateFlow<Boolean> = community.isConfigured.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** Signalements autour de la position projetée (toutes les 60 s), affichés sur la carte. */
    private val communityReports = community.pollNearby({ (session.state.value as? NavigationState.Guiding)?.progress?.snapped })

    /**
     * Incidents officiels (Bison Futé) sur le tracé courant, dans le sens de circulation, rafraîchis toutes les 5 minutes
     * pendant le guidage ; montrés comme des signalements (carte, Horizon).
     */
    private val officialReports: kotlinx.coroutines.flow.Flow<List<CommunityReport>> = traffic?.let { src ->
        kotlinx.coroutines.flow.flow {
            while (true) {
                val g = session.state.value as? NavigationState.Guiding
                if (g != null) {
                    val events = try { src.events() } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) { emptyList() }
                    val now = System.currentTimeMillis()
                    emit(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        fr.roadly.core.data.traffic.TrafficOnRoute.along(g.route.geometry, events).map { it.asReport(now) }
                    })
                } else emit(emptyList())
                kotlinx.coroutines.delay(fr.roadly.core.data.traffic.TrafficRepository.MAX_AGE_MS)
            }
        }
    } ?: kotlinx.coroutines.flow.flowOf(emptyList())

    val reports: StateFlow<List<CommunityReport>> = combine(communityReports, officialReports) { a, b -> a + b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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

    private val reportsProjector = ReportsAheadProjector()

    /** Signalements projetés sur le tracé courant, devant soi (≤ 60 m du tracé, ≤ 5 km) : alimentent l'Horizon. C3 : projection en cache par tracé. */
    val reportsAhead: StateFlow<List<ReportAheadUi>> = combine(reports, session.state) { list, s ->
        val g = s as? NavigationState.Guiding ?: return@combine emptyList()
        reportsProjector.ahead(g.route, g.progress.distanceAlongM, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val isDark = MutableStateFlow(true)
    private val following = MutableStateFlow(true)
    private var refollowJob: Job? = null
    private var alternativesJob: Job? = null
    private val styleUrl = combine(isDark, config.config) { dark, cfg -> if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl }

    private val _alternatives = MutableStateFlow<AlternativesUi>(AlternativesUi.Hidden)
    val alternativesUi: StateFlow<AlternativesUi> = _alternatives

    /** E2 « Bâtiments 3D » (clé `map.3d`, ex-« Carte 3D en guidage ») : bâtiments extrudés en vue 3D ; lu sans attendre, d'où `Eagerly`. */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.MAP_3D)

    /** E2 : vue au départ (3D / 2D ; suit `map.3d` tant qu'elle n'a pas été choisie) et rotation automatique (non = nord en haut). */
    private val startView: StateFlow<CameraMode3D> = prefs.defaultCameraMode.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.DEFAULT_CAMERA_MODE)
    private val autoRotate: StateFlow<Boolean> = prefs.autoRotate.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.AUTO_ROTATE)

    /** C1 : mode choisi par les boutons pour cette session ; null = mode des réglages. */
    private val cameraChoice = MutableStateFlow<CameraMode3D?>(null)
    /** Mode quitté en passant « nord en haut », rétabli au second appui ; null = vue au départ des réglages. */
    private var modeBeforeNorthUp: CameraMode3D? = null

    /** C1 / E2 : mode de caméra du guidage (3D, 2D, nord en haut), pour la session seulement ; départ = réglages. */
    val cameraMode: StateFlow<CameraMode3D> = combine(startView, autoRotate, cameraChoice) { view, rotate, choice -> choice ?: CameraMode3D.initial(view, rotate) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraMode3D.initial(GuidanceDefaults.DEFAULT_CAMERA_MODE, GuidanceDefaults.AUTO_ROTATE))

    private fun currentMode(): CameraMode3D = cameraChoice.value ?: CameraMode3D.initial(startView.value, autoRotate.value)

    /** Bouton 2D / 3D. */
    fun toggleCameraMode() { cameraChoice.value = currentMode().toggled3d() }

    /** Bouton « nord en haut » : bascule, puis retour au mode d'avant (session partie en nord en haut : la vue au départ). */
    fun toggleNorthUp() {
        val current = currentMode()
        cameraChoice.value = if (current == CameraMode3D.NORTH_UP) {
            modeBeforeNorthUp ?: startView.value.takeIf { it != CameraMode3D.NORTH_UP } ?: CameraMode3D.FOLLOW_3D
        } else {
            modeBeforeNorthUp = current
            CameraMode3D.NORTH_UP
        }
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

    /** E2 : réglages lus par l'écran (caméra, ligne « puis », économie d'énergie), en `StateFlow` séparées : le `combine` de `uiState` n'est pas touché. */
    val zoomOffset: StateFlow<Int> = prefs.zoomOffset.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.ZOOM_OFFSET)
    val pitchPreset: StateFlow<PitchPreset> = prefs.pitchPreset.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.PITCH_PRESET)
    val showNextManeuverPreview: StateFlow<Boolean> = prefs.showNextManeuverPreview.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.SHOW_NEXT_MANEUVER)
    val powerSaveMode: StateFlow<PowerSaveMode> = prefs.powerSaveMode.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.POWER_SAVE)

    private val _sheet = MutableStateFlow(GuidanceSheet.NONE)
    /**
     * D1 / D5 : feuille ouverte au-dessus du guidage. Elle est dans le ViewModel, pas en `rememberSaveable` : les overlays, à signature figée,
     * n'ont que `vm` pour l'ouvrir. Elle est gardée à la rotation.
     */
    val guidanceSheet: StateFlow<GuidanceSheet> = _sheet

    fun openManeuverPreview() { _sheet.value = GuidanceSheet.MANEUVER_PREVIEW }
    fun openTripDetails() { _sheet.value = GuidanceSheet.TRIP_DETAILS }
    fun closeSheet() { _sheet.value = GuidanceSheet.NONE }

    private val _panelExpanded = MutableStateFlow(false)
    /** D5 : panneau du bas déplié (destination, « Trajet complet », « Itinéraires ») ; gardé à la rotation. */
    val bottomPanelExpanded: StateFlow<Boolean> = _panelExpanded

    fun toggleBottomPanel() { _panelExpanded.value = !_panelExpanded.value }
    /** Fin du guidage (arrivée, échec, arrêt) : le guidage suivant repart avec le panneau replié. */
    fun collapseBottomPanel() { _panelExpanded.value = false }

    private val _panelFraction = MutableStateFlow<Float?>(null)
    /** D5 : part de l'écran couverte par le bloc du bas, mesurée par l'écran (`PanelGeometry`) → marge basse de la caméra. */
    val bottomPanelFraction: StateFlow<Float?> = _panelFraction

    fun setBottomPanelFraction(fraction: Float?) { _panelFraction.value = fraction }

    private val _bottomBlockHeight = MutableStateFlow(0)
    /**
     * Passe appareil : distance (dp, arrondie au-dessus) entre le haut du bloc du bas et le bas de l'écran, mesurée avec la fraction ;
     * la colonne d'actions portrait se pose au-dessus. 0 avant la première mesure.
     */
    val bottomBlockHeightDp: StateFlow<Int> = _bottomBlockHeight

    fun setBottomBlockHeightDp(heightDp: Int) { _bottomBlockHeight.value = heightDp }

    private val _topStackFraction = MutableStateFlow<Float?>(null)
    /** Passe appareil : part de l'écran couverte par la pile du haut (portrait, `PanelGeometry.topFraction`) → marge haute de la caméra. */
    val topStackFraction: StateFlow<Float?> = _topStackFraction

    fun setTopStackFraction(fraction: Float?) { _topStackFraction.value = fraction }

    /** D5 : nom de la destination mémorisé par la session (B6, `lastGuidance`) ; null si la course a démarré sans nom. */
    val destinationName: StateFlow<String?> = prefs.lastGuidance.map { it?.destinationName }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Dernier état produit : hystérésis de l'approche (B1). */
    private var lastUi: NavigationUiState? = null

    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault(), previous = lastUi).also { lastUi = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationUiState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun toggleMute() = session.setMuted(!session.muted.value)
    fun stop() = guidance.stop()
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
            val sc = scooter
            val cfg = session.scooterConfig
            val result = if (sc != null && cfg != null) {
                // Trottinette : variantes trottinette depuis la position (jamais les variantes voiture).
                sc.options(scooterParams(sc, cfg, g.progress.snapped, g.route.geometry.last(), emptyList()))
            } else guidance.alternativesFromHere()
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

    /** l'assistant vocal : réponse calculée sur l'état réel du guidage et du profil, prononcée puis affichée 8 s. */
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
