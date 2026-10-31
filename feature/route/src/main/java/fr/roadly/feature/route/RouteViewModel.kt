package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.routing.StopOrdering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Clé du `savedStateHandle` de l'entrée de pile par laquelle la recherche (mode « choisir un arrêt ») renvoie un lieu (JSON). */
const val PICKED_STOP_KEY = "pickedStop"

@HiltViewModel
class RouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alternatives: RouteAlternativesUseCase,
    private val vehicleRepo: VehicleProfileRepository,
    private val location: LocationRepository,
    private val config: ProviderConfigRepository,
    private val prefs: UserPreferencesRepository,
    private val session: NavigationSession,
    private val routing: RoutingProvider,
) : ViewModel() {

    // Arguments lus par nom de champ de RouteRoute : c'est ce que fait toRoute(), mais sans
    // passer par Bundle (indisponible en test unitaire JVM sans Robolectric).
    private val destName: String = savedStateHandle.get<String>("destName").orEmpty()
    private val destination = GeoPoint(
        requireNotNull(savedStateHandle.get<Double>("destLat")) { "destLat manquant" },
        requireNotNull(savedStateHandle.get<Double>("destLon")) { "destLon manquant" },
    )
    private val origin = MutableStateFlow(location.lastKnown?.point)
    private val departAt = MutableStateFlow(System.currentTimeMillis())
    /** Heure d'arrivée souhaitée ; null = mode « Partir à ». */
    private val arriveAt = MutableStateFlow<Long?>(null)
    private val stops = MutableStateFlow<List<Place>>(emptyList())
    private val optimizing = MutableStateFlow(false)
    private val activeId = MutableStateFlow<String?>(null)
    private val isDark = MutableStateFlow(true)
    private val computed = MutableStateFlow<RouteUiState>(RouteUiState.Loading)
    private val reload = MutableStateFlow(0)
    /** Préférences du dernier calcul affiché : transmises à la session (et donc au recalcul). */
    private var computedWith = RoutePreferences()

    private data class Params(val origin: GeoPoint?, val departAt: Long, val arriveAt: Long?, val vehicle: VehicleProfile?, val preferences: RoutePreferences, val stops: List<Place>)

    init {
        viewModelScope.launch {
            combine(origin, departAt, arriveAt, vehicleRepo.profile, prefs.routePreferences, stops, reload) { values ->
                @Suppress("UNCHECKED_CAST")
                Params(values[0] as GeoPoint?, values[1] as Long, values[2] as Long?, values[3] as VehicleProfile?, values[4] as RoutePreferences, values[5] as List<Place>)
            }.collect { (o, d, a, v, p, s) ->
                if (o == null) { computed.value = RouteUiState.NoOrigin(destName); return@collect }
                computed.value = RouteUiState.Loading
                when (val r = alternatives(o, destination, v, d, p, s.map { it.point })) {
                    is Outcome.Success -> {
                        val (options, effectiveDepart) = if (a != null) shiftToArrival(r.value, a) else r.value to d
                        // L'option active doit toujours exister dans la liste affichée.
                        if (activeId.value == null || options.none { it.route.id == activeId.value }) activeId.value = options.first().route.id
                        computedWith = p
                        computed.value = RouteUiState.Content(o, destName, options, activeId.value!!, v != null, effectiveDepart, a, p, s)
                    }
                    is Outcome.Failure -> computed.value = RouteUiState.Error(r.error)
                }
            }
        }
    }

    val uiState: StateFlow<RouteScreenState> = combine(computed, activeId, isDark, config.config, optimizing) { s, id, dark, cfg, opt ->
        val phase = if (s is RouteUiState.Content) s.copy(activeId = id ?: s.activeId, optimizing = opt) else s
        RouteScreenState(styleUrl = if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl, phase = phase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteScreenState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun selectRoute(id: String) { activeId.value = id }
    /** Mode « Partir à » : l'heure d'arrivée souhaitée est oubliée. */
    fun setDepartAt(epochMs: Long) { arriveAt.value = null; departAt.value = epochMs }
    /** Mode « Arriver à » : le départ est déduit de la durée du plus rapide (sans trafic prédictif). */
    fun setArriveAt(epochMs: Long) { arriveAt.value = epochMs }
    fun setPreferences(value: RoutePreferences) { viewModelScope.launch { prefs.setRoutePreferences(value) } }
    fun retry() { reload.value++ }
    /** Origine choisie par appui long sur la carte quand la position est inconnue. */
    fun setOrigin(point: GeoPoint) { origin.value = point }
    val destinationPoint: GeoPoint get() = destination

    // ── Arrêts (≤ 10) ──
    fun addStop(place: Place) {
        val current = stops.value
        if (current.size >= StopOrdering.MAX_STOPS || current.any { it.id == place.id }) return
        stops.value = current + place
    }
    fun removeStop(index: Int) { stops.value = stops.value.filterIndexed { i, _ -> i != index } }
    fun moveStop(from: Int, to: Int) { stops.value = StopOrdering.move(stops.value, from, to) }

    /** Mode tournée : ordre optimal calculé par le fournisseur (repli local plus proche voisin). */
    fun optimizeStops() {
        val o = origin.value ?: return
        val current = stops.value
        if (current.size < 2 || optimizing.value) return
        viewModelScope.launch {
            optimizing.value = true
            val order = when (val r = routing.optimizeOrder(o, current.map { it.point }, destination, computedWith)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> StopOrdering.nearestNeighbour(o, current.map { it.point })
            }
            if (stops.value == current) stops.value = order.map { current[it] }
            optimizing.value = false
        }
    }

    /** Démarre le guidage sur l'itinéraire sélectionné ; false si aucun itinéraire n'est affiché. */
    fun startGuidance(): Boolean {
        val content = computed.value as? RouteUiState.Content ?: return false
        val id = activeId.value ?: content.activeId
        val option = content.options.firstOrNull { it.route.id == id } ?: return false
        session.start(option, computedWith)
        return true
    }

    companion object {
        /** Départ = arrivée souhaitée − durée du plus rapide ; chaque option reçoit l'arrivée correspondante. */
        internal fun shiftToArrival(options: List<RouteOption>, arriveAtMs: Long): Pair<List<RouteOption>, Long> {
            val reference = options.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: options.first()
            val depart = arriveAtMs - (reference.route.durationSeconds * 1000).toLong()
            return options.map { it.copy(arrivalEpochMs = depart + (it.route.durationSeconds * 1000).toLong()) } to depart
        }
    }
}
