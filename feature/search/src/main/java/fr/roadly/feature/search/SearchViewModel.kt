package fr.roadly.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.Place
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val geocoder: GeocodingProvider,
    private val recents: RecentSearchRepository,
    private val favorites: FavoritesRepository,
    private val location: LocationRepository,
    connectivity: ConnectivityObserver,
    private val nearby: NearbyPoiProvider,
    private val fuelStations: FuelStationProvider,
    vehicle: VehicleProfileRepository,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val query = MutableStateFlow("")
    // Catégorie initiale passée par la route (puces de l'accueil) ; valeur inconnue ignorée.
    private val category = MutableStateFlow(savedStateHandle.get<String>("category")?.let { name -> PoiCategory.entries.firstOrNull { it.name == name } })
    private val selected = MutableStateFlow<Place?>(null)
    // Dérivés : le statut favori suit la base, la distance suit la dernière position connue.
    private val selectedIsFavorite = combine(selected, favorites.observeAll()) { s, favs -> s != null && favs.any { it.id == s.id } }
    private val selectedDistance = selected.map { s -> s?.let { location.lastKnown?.point?.distanceTo(it.point) } }
    private val online = connectivity.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    /** Carburant choisi par l'utilisateur ; null = celui du profil véhicule (ou gazole sans profil, jamais électrique : pas de prix). */
    private val fuelChoice = MutableStateFlow<FuelType?>(null)
    private val fuel = combine(fuelChoice, vehicle.profile) { choice, profile -> choice ?: profile?.fuel?.takeIf { it != FuelType.ELECTRIC } ?: FuelType.DIESEL }
    private val fuelSort = MutableStateFlow(FuelSort.DISTANCE)

    // Compteur de relance : permet de rejouer la même requête (le distinctUntilChanged l'ignorerait sinon).
    private val retryTick = MutableStateFlow(0)

    private data class Request(val query: String, val category: PoiCategory?)

    // `combine` attend une première valeur de chaque flux : la requête débounçée démarre vide pour ne pas retarder les catégories.
    private val rawResults = combine(query.debounce(300).onStart { emit("") }.distinctUntilChanged(), category, retryTick) { q, cat, _ -> Request(q.trim(), cat) }
        .distinctUntilChanged()
        .flatMapLatest { r ->
            when {
                r.category == PoiCategory.FUEL -> fuelFlow()
                r.category != null -> nearbyFlow(r.category)
                else -> searchFlow(r.query)
            }
        }
        .onStart { emit(SearchResults.Idle) }

    /** Les stations sont réordonnées localement selon le tri et le carburant choisis (aucun nouvel appel réseau). */
    private val results = combine(rawResults, fuel, fuelSort) { r, f, sort ->
        if (r is SearchResults.Content && r.category == PoiCategory.FUEL && sort == FuelSort.PRICE) {
            r.copy(places = r.places.sortedWith(compareBy(nullsLast()) { r.stations[it.id]?.price(f)?.eurPerLitre }))
        } else r
    }

    val uiState: StateFlow<SearchUiState> = combine(
        query, results, recents.observe(), favorites.observeAll(), online, selected, selectedIsFavorite, selectedDistance, category, fuel, fuelSort,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val here = location.lastKnown?.point
        SearchUiState(
            query = values[0] as String,
            results = values[1] as SearchResults,
            recents = values[2] as List<Place>,
            favorites = values[3] as List<Place>,
            isOnline = values[4] as Boolean,
            selected = values[5] as Place?,
            selectedIsFavorite = values[6] as Boolean,
            selectedDistanceMeters = values[7] as Double?,
            category = values[8] as PoiCategory?,
            hereLat = here?.lat,
            hereLon = here?.lon,
            fuel = values[9] as FuelType,
            fuelSort = values[10] as FuelSort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private fun searchFlow(q: String) = flow {
        if (q.length < 2) { emit(SearchResults.Idle); return@flow }
        // Hors ligne : aucun appel réseau, on remonte directement l'erreur réseau.
        if (!online.value) { emit(SearchResults.Error(AppError.Network)); return@flow }
        emit(SearchResults.Loading)
        when (val r = geocoder.search(q, location.lastKnown?.point)) {
            is Outcome.Success -> emit(SearchResults.Content(r.value))
            is Outcome.Failure -> emit(SearchResults.Error(r.error))
        }
    }

    /** « À proximité » exige une position : sans fix connu, l'erreur `NoResult` invite à attendre le GPS. */
    private fun nearbyFlow(cat: PoiCategory) = flow {
        val here = location.lastKnown?.point
        if (here == null) { emit(SearchResults.Error(AppError.NoResult)); return@flow }
        if (!online.value) { emit(SearchResults.Error(AppError.Network)); return@flow }
        emit(SearchResults.Loading)
        when (val r = nearby.nearby(cat, here)) {
            is Outcome.Success -> emit(SearchResults.Content(r.value, cat))
            is Outcome.Failure -> emit(SearchResults.Error(r.error))
        }
    }

    /** Stations-service avec prix réels (flux du ministère de l'Économie) ; repli sur les lieux OSM si le flux échoue. */
    private fun fuelFlow() = flow {
        val here = location.lastKnown?.point
        if (here == null) { emit(SearchResults.Error(AppError.NoResult)); return@flow }
        if (!online.value) { emit(SearchResults.Error(AppError.Network)); return@flow }
        emit(SearchResults.Loading)
        when (val r = fuelStations.nearby(here, radiusKm = PoiCategory.FUEL.radiusKm)) {
            is Outcome.Success -> {
                val stations = r.value.associateBy { it.toPlace().id }
                emit(SearchResults.Content(r.value.map { it.toPlace() }, PoiCategory.FUEL, stations))
            }
            is Outcome.Failure -> when (val fallback = nearby.nearby(PoiCategory.FUEL, here)) {
                is Outcome.Success -> emit(SearchResults.Content(fallback.value, PoiCategory.FUEL))
                is Outcome.Failure -> emit(SearchResults.Error(r.error))
            }
        }
    }

    fun onQueryChange(value: String) { category.value = null; query.value = value }

    fun onClear() { query.value = ""; category.value = null }

    /** Une puce de catégorie : sélection exclusive, nouvelle pression = désélection. */
    fun onCategory(cat: PoiCategory) { category.value = if (category.value == cat) null else cat }

    fun onFuel(type: FuelType) { fuelChoice.value = type }
    fun onFuelSort(sort: FuelSort) { fuelSort.value = sort }

    // Force une nouvelle recherche : le distinctUntilChanged ignorerait sinon la même requête.
    fun retry() { retryTick.value++ }

    fun onPlaceSelected(place: Place) {
        selected.value = place
        viewModelScope.launch {
            // Une erreur de stockage ne doit pas faire planter l'app.
            runCatching { recents.record(place) }
        }
    }

    /** Station liée au lieu sélectionné (prix, services), si la liste courante en contient. */
    fun stationFor(place: Place): FuelStation? = (uiState.value.results as? SearchResults.Content)?.stations?.get(place.id)

    fun onDismissPlace() { selected.value = null }

    fun onToggleFavorite() {
        val p = selected.value ?: return
        viewModelScope.launch { favorites.toggle(p) }
    }

    fun onSetHome() { val p = selected.value ?: return; viewModelScope.launch { favorites.setHome(p) } }
    fun onSetWork() { val p = selected.value ?: return; viewModelScope.launch { favorites.setWork(p) } }
}
