package fr.roadly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.pollNearby
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationMode
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.location.PermissionEvents
import fr.roadly.core.map.CameraMode
import fr.roadly.core.map.CameraSnapshot
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Distance minimale (en mètres) parcourue avant de relancer un géocodage inverse. */
private const val REVERSE_MIN_DISTANCE_M = 50.0

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val location: LocationRepository,
    favorites: FavoritesRepository,
    private val geocoder: GeocodingProvider,
    connectivity: ConnectivityObserver,
    config: ProviderConfigRepository,
    permissionEvents: PermissionEvents,
    private val navigationSession: NavigationSession,
    private val community: CommunityRepository,
    private val prefs: UserPreferencesRepository,
    private val alternatives: RouteAlternativesUseCase,
    private val vehicles: VehicleProfileRepository,
) : ViewModel() {

    /** Service communautaire configuré (Réglages → Communauté) : feuille « Signaler » active, signalements sur la carte. */
    val communityEnabled: StateFlow<Boolean> = community.isConfigured.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** Signalements actifs autour de la position (toutes les 60 s). */
    val reports: StateFlow<List<CommunityReport>> = community.pollNearby({ location.lastKnown?.point }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _reportMessage = MutableStateFlow<String?>(null)
    /** Résultat du dernier signalement (affiché brièvement) ; null = rien à montrer. */
    val reportMessage: StateFlow<String?> = _reportMessage

    /** Envoie un signalement à la position courante ; sans position ou sans service, rien n'est envoyé. */
    fun report(kind: ReportKind) {
        val fix = location.lastKnown ?: return
        viewModelScope.launch {
            _reportMessage.value = when (val r = community.report(kind, fix.point, fix.bearingDeg)) {
                is Outcome.Success -> "sent"
                is Outcome.Failure -> "error:" + r.error.javaClass.simpleName
            }
            delay(4_000)
            _reportMessage.value = null
        }
    }

    private val cameraMode = MutableStateFlow(CameraMode.CENTERED)
    private val address = MutableStateFlow<String?>(null)
    private val picked = MutableStateFlow<Place?>(null)
    private val isDark = MutableStateFlow(true)
    private val mapBearing = MutableStateFlow(0.0)
    /** Dernière caméra de l'accueil ; le VM est lié à l'entrée de pile, elle survit donc à la navigation. */
    var lastCamera: CameraSnapshot? = null
        private set
    private var lastReversePoint: GeoPoint? = null
    private var reverseJob: Job? = null

    init {
        // L'activité signale le retour du dialogue système : on relit l'état de la permission.
        viewModelScope.launch { permissionEvents.changed.collect { location.refreshPermission() } }
    }

    private val fixes = location.fixes
        .onStart { location.setMode(if (cameraMode.value == CameraMode.FOLLOW) LocationMode.FOLLOW else LocationMode.MAP) }
        .onEach { maybeReverse(it) }
        .onCompletion { location.setMode(LocationMode.OFF) }

    val uiState: StateFlow<HomeUiState> = combine(
        fixes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null),
        address, favorites.observeHome(), favorites.observeWork(), cameraMode,
        location.permissionState, connectivity.isOnline, config.config, picked, isDark, mapBearing,
    ) { values ->
        val cfg = values[7] as ProviderConfig
        HomeUiState(
            fix = values[0] as LocationFix?,
            address = values[1] as String?,
            home = values[2] as Place?,
            work = values[3] as Place?,
            cameraMode = values[4] as CameraMode,
            permission = values[5] as LocationPermissionState,
            isOnline = values[6] as Boolean,
            styleUrl = if (values[9] as Boolean) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl,
            pickedPlace = values[8] as Place?,
            mapBearingDeg = values[10] as Double,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Un guidage est en cours : l'accueil propose alors « Reprendre le guidage » (retour possible depuis « Changer de destination »). */
    val isGuiding: StateFlow<Boolean> = navigationSession.state
        .map { it is NavigationState.Guiding }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val resumePhase = MutableStateFlow(ResumePhase.OFFER)
    private val resumable: StateFlow<LastGuidance?> = navigationSession.resumableGuidance.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** E3 : carte « Reprendre le guidage vers … » — course interrompue et session au repos ; null sinon. */
    val resumeCard: StateFlow<ResumeCardUi?> = combine(resumable, resumePhase) { last, phase -> last?.let { ResumeCardUi(it.destinationName, phase) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * E3 « Reprendre » : nouvel itinéraire depuis la position actuelle vers la destination mémorisée (pas le tracé enregistré), avec les
     * préférences et le véhicule du moment, puis départ comme « ROULER » (`session.start` avec le nom). Même calcul que
     * `GuidanceActions.startTo` de Roadly AI. Échec : la carte le dit, la course reste mémorisée. Sans position connue : rien.
     */
    fun resumeLastGuidance() {
        val last = resumable.value ?: return
        val origin = location.lastKnown?.point ?: return
        if (resumePhase.value == ResumePhase.ROUTING) return
        resumePhase.value = ResumePhase.ROUTING
        viewModelScope.launch {
            val p = prefs.routePreferences.first()
            val v = vehicles.profile.first()
            when (val r = alternatives(origin, last.destination, v, System.currentTimeMillis(), p)) {
                is Outcome.Success -> {
                    val best = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                    resumePhase.value = ResumePhase.OFFER
                    navigationSession.start(best, p, v, last.destinationName)
                }
                is Outcome.Failure -> resumePhase.value = ResumePhase.FAILED
            }
        }
    }

    /** E3 « Ignorer » : la course mémorisée est oubliée ; plus de carte jusqu'au prochain guidage interrompu. */
    fun dismissResume() {
        resumePhase.value = ResumePhase.OFFER
        navigationSession.forgetLastGuidance()
    }

    /** Relance le géocodage inverse seulement si l'on s'est déplacé de plus de 50 m. */
    private fun maybeReverse(fix: LocationFix) {
        val last = lastReversePoint
        if (last != null && last.distanceTo(fix.point) < REVERSE_MIN_DISTANCE_M) return
        lastReversePoint = fix.point
        reverseJob?.cancel()
        reverseJob = viewModelScope.launch {
            when (val r = geocoder.reverse(fix.point)) {
                is Outcome.Success -> address.value = r.value?.let { listOfNotNull(it.name, it.address).joinToString(", ") }
                is Outcome.Failure -> Unit // on garde la dernière adresse connue
            }
        }
    }

    fun rememberCamera(s: CameraSnapshot?) { if (s != null) lastCamera = s }

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }

    /** Cap de la carte après chaque mouvement (boussole). */
    fun onCameraIdle(bearingDeg: Double) { mapBearing.value = bearingDeg }

    fun onUserGesture() {
        if (cameraMode.value != CameraMode.FREE) {
            cameraMode.value = CameraMode.FREE
            location.setMode(LocationMode.MAP)
        }
    }

    /** FREE → CENTERED → FOLLOW → CENTERED … */
    fun onRecenter() {
        cameraMode.value = when (cameraMode.value) {
            CameraMode.FREE -> CameraMode.CENTERED
            CameraMode.CENTERED -> CameraMode.FOLLOW
            CameraMode.FOLLOW -> CameraMode.CENTERED
        }
        location.setMode(if (cameraMode.value == CameraMode.FOLLOW) LocationMode.FOLLOW else LocationMode.MAP)
    }

    /** Appui long sur la carte : propose ce point comme destination. */
    fun onMapLongClick(point: GeoPoint) {
        val id = "pick:${point.lat},${point.lon}"
        picked.value = Place(id, "Point sur la carte", "%.5f, %.5f".format(java.util.Locale.ROOT, point.lat, point.lon), point, PlaceKind.COORDINATES, "local")
        viewModelScope.launch {
            val r = geocoder.reverse(point)
            if (r is Outcome.Success && r.value != null) picked.value = r.value!!.copy(id = id)
        }
    }

    fun onDismissPicked() { picked.value = null }
}
