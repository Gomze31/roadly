package fr.roadly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationMode
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.location.PermissionEvents
import fr.roadly.core.map.CameraMode
import fr.roadly.core.map.CameraSnapshot
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    navigationSession: NavigationSession,
) : ViewModel() {

    private val cameraMode = MutableStateFlow(CameraMode.CENTERED)
    private val address = MutableStateFlow<String?>(null)
    private val picked = MutableStateFlow<Place?>(null)
    private val isDark = MutableStateFlow(true)
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
        location.permissionState, connectivity.isOnline, config.config, picked, isDark,
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Un guidage est en cours : l'accueil propose alors « Reprendre le guidage » (retour possible depuis « Changer de destination »). */
    val isGuiding: StateFlow<Boolean> = navigationSession.state
        .map { it is NavigationState.Guiding }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
