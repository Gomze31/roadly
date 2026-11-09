package fr.roadly.feature.home

import fr.roadly.core.data.repository.DeparturePlan
import fr.roadly.core.data.repository.DeparturePlanner
import fr.roadly.core.model.RoutePreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.pollNearby
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.DriveMemoryRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.ParkedVehicle
import fr.roadly.core.data.repository.TollReminder
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
import fr.roadly.core.navigation.FreeDriveSession
import fr.roadly.core.navigation.FreeDriveState
import fr.roadly.core.navigation.NavigationSession
import kotlinx.coroutines.flow.flowOf
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.common.AppError
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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

/** Identifiant du point « Mon véhicule » dans la fiche de l'accueil. */
internal const val PARKED_PLACE_ID = "parked-vehicle"

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
    private val driveMemory: DriveMemoryRepository,
    /** Conduite libre (null dans les tests existants) : sa limitation alimente le badge de l'accueil quand elle tourne. */
    private val freeDrive: FreeDriveSession? = null,
    /** Routage (null dans les tests existants) : trajet à pied vers la voiture garée. */
    private val routing: RoutingProvider? = null,
    /** Carte « Départ » (null dans les tests existants : départ immédiat, carte inactive). */
    private val departurePlanner: DeparturePlanner? = null,
) : ViewModel() {

    /** Départ planifié sur la carte « Départ » ; null = partir maintenant. */
    val departurePlan: StateFlow<DeparturePlan?> = departurePlanner?.plan ?: MutableStateFlow(null)
    fun setDeparturePlan(plan: DeparturePlan?) { departurePlanner?.set(plan) }

    /** Options d'itinéraire (bouton « réglages » de la barre Domicile / Travail) : les mêmes que l'écran d'itinéraires. */
    val routePreferences: StateFlow<RoutePreferences> = prefs.routePreferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutePreferences())
    fun setRoutePreferences(value: RoutePreferences) { viewModelScope.launch { prefs.setRoutePreferences(value) } }

    /**
     * Limitation de la conduite libre en cours (lancée depuis Android Auto alors que le téléphone montre l'accueil, par exemple) ;
     * null hors conduite libre ou limitation inconnue. Hors conduite libre, l'accueil n'a pas de source de limitation.
     */
    val freeDriveLimitKmh: StateFlow<Int?> = (freeDrive?.state?.map { (it as? FreeDriveState.Active)?.speedLimitKmh } ?: flowOf(null))
        .distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 1.0 : péages en flux libre franchis et pas encore marqués « payé », du plus récent au plus ancien. */
    val tollReminders: StateFlow<List<TollReminder>> = driveMemory.tollReminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 1.0 : position du véhicule (mémorisée à l'arrivée d'un guidage motorisé, ou « Je suis garé ici »). */
    val parkedVehicle: StateFlow<ParkedVehicle?> = driveMemory.parkedVehicle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissTollReminder(id: Long) { viewModelScope.launch { driveMemory.dismissTollReminder(id) } }

    /** Mémorise la position actuelle comme place de stationnement ; sans position connue, rien. */
    fun parkHere() {
        val fix = location.lastKnown ?: return
        viewModelScope.launch { driveMemory.setParkedVehicle(ParkedVehicle(fix.point, System.currentTimeMillis(), null)) }
    }

    fun forgetParkedVehicle() {
        if (picked.value?.id == PARKED_PLACE_ID) picked.value = null
        clearWalkToCar()
        viewModelScope.launch { driveMemory.setParkedVehicle(null) }
    }

    /** Montre le véhicule sur la carte (marqueur et fiche) ; la caméra passe en mode libre pour ne pas revenir sur la position. */
    fun showParkedVehicle(name: String, near: String?) {
        val v = parkedVehicle.value ?: return
        onUserGesture()
        picked.value = Place(PARKED_PLACE_ID, name, near, v.point, PlaceKind.COORDINATES, "local")
    }

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
    private var resumeJob: Job? = null
    private val resumable: StateFlow<LastGuidance?> = navigationSession.resumableGuidance.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** E3 : carte « Reprendre le guidage vers … » — course interrompue et session au repos ; null sinon. */
    val resumeCard: StateFlow<ResumeCardUi?> = combine(resumable, resumePhase) { last, phase -> last?.let { ResumeCardUi(it.destinationName, phase) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * E3 « Reprendre » : nouvel itinéraire depuis la position actuelle vers la destination mémorisée (pas le tracé enregistré), avec les
     * préférences et le véhicule du moment, puis départ comme « ROULER » (`session.start` avec le nom). Même calcul que
     * `GuidanceActions.startTo` de l'assistant vocal. Échec : la carte le dit, la course reste mémorisée. Sans position connue : rien.
     * @return vrai si une reprise est en cours (l'écran attend alors le guidage), faux si rien n'a été lancé.
     */
    fun resumeLastGuidance(): Boolean {
        val last = resumable.value ?: return false
        val origin = location.lastKnown?.point ?: return false
        if (resumePhase.value == ResumePhase.ROUTING) return true
        resumePhase.value = ResumePhase.ROUTING
        resumeJob = viewModelScope.launch {
            val p = prefs.routePreferences.first()
            val v = vehicles.profile.first()
            val r = alternatives(origin, last.destination, v, System.currentTimeMillis(), p)
            ensureActive() // « Ignorer » pendant le calcul : un résultat tardif ne démarre rien.
            when (r) {
                is Outcome.Success -> {
                    val best = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                    resumePhase.value = ResumePhase.OFFER
                    navigationSession.start(best, p, v, destinationName = last.destinationName)
                }
                is Outcome.Failure -> resumePhase.value = ResumePhase.FAILED
            }
        }
        return true
    }

    /** E3 « Ignorer » : annule une reprise en cours de calcul et oublie la course mémorisée ; plus de carte jusqu'au prochain guidage interrompu. */
    fun dismissResume() {
        resumeJob?.cancel()
        resumeJob = null
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

    fun onDismissPicked() { picked.value = null; clearWalkToCar() }

    // ── S4 : retour à pied vers la voiture garée ──
    private val _walkToCar = MutableStateFlow<WalkToCar?>(null)
    /** Trajet à pied vers la voiture (calcul, résultat ou échec) ; null = rien demandé. */
    val walkToCar: StateFlow<WalkToCar?> = _walkToCar
    private var walkJob: Job? = null

    /** Calcule le trajet à pied (Valhalla `pedestrian`) de la position actuelle à la voiture garée ; sans position, rien. */
    fun requestWalkToCar() {
        val provider = routing ?: return
        val from = location.lastKnown?.point ?: run { _walkToCar.value = WalkToCar.Failed(AppError.PermissionDenied); return }
        val car = parkedVehicle.value ?: return
        walkJob?.cancel()
        if (from.distanceTo(car.point) < NEARBY_CAR_M) { _walkToCar.value = WalkToCar.Nearby; return }
        _walkToCar.value = WalkToCar.Loading
        walkJob = viewModelScope.launch {
            _walkToCar.value = when (val r = provider.route(RouteRequest(from, car.point, costing = RouteCosting.FASTEST, pedestrian = true))) {
                is Outcome.Success -> r.value.firstOrNull()?.let { WalkToCar.Ready(it) } ?: WalkToCar.Failed(AppError.NoResult)
                is Outcome.Failure -> WalkToCar.Failed(r.error)
            }
        }
    }

    fun clearWalkToCar() { walkJob?.cancel(); _walkToCar.value = null }
}

/** En deçà, la voiture est « à côté » (précision GPS de quelques mètres) : aucun trajet n'est demandé. */
internal const val NEARBY_CAR_M = 30.0

/** Trajet à pied vers la voiture garée. */
sealed interface WalkToCar {
    data object Loading : WalkToCar
    /** Moins de [NEARBY_CAR_M] de la voiture : pas de trajet à calculer. */
    data object Nearby : WalkToCar
    data class Ready(val route: Route) : WalkToCar
    data class Failed(val error: AppError) : WalkToCar
}
