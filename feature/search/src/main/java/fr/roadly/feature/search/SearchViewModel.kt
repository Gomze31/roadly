package fr.roadly.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.Place
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Place?>(null)
    // Dérivés : le statut favori suit la base, la distance suit la dernière position connue.
    private val selectedIsFavorite = combine(selected, favorites.observeAll()) { s, favs -> s != null && favs.any { it.id == s.id } }
    private val selectedDistance = selected.map { s -> s?.let { location.lastKnown?.point?.distanceTo(it.point) } }
    private val online = connectivity.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Compteur de relance : permet de rejouer la même requête (le distinctUntilChanged l'ignorerait sinon).
    private val retryTick = MutableStateFlow(0)

    // Le debounce n'émet rien avant 300 ms : on démarre sur Idle pour que l'état (récents, favoris…)
    // soit disponible immédiatement.
    private val results = combine(query.debounce(300).distinctUntilChanged(), retryTick) { q, _ -> q }
        .flatMapLatest { q -> searchFlow(q.trim()) }
        .onStart { emit(SearchResults.Idle) }

    val uiState: StateFlow<SearchUiState> = combine(
        query, results, recents.observe(), favorites.observeAll(), online, selected, selectedIsFavorite, selectedDistance,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SearchUiState(
            query = values[0] as String,
            results = values[1] as SearchResults,
            recents = values[2] as List<Place>,
            favorites = values[3] as List<Place>,
            isOnline = values[4] as Boolean,
            selected = values[5] as Place?,
            selectedIsFavorite = values[6] as Boolean,
            selectedDistanceMeters = values[7] as Double?,
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

    fun onQueryChange(value: String) { query.value = value }

    fun onClear() { query.value = "" }

    // Force une nouvelle recherche : le distinctUntilChanged ignorerait sinon la même requête.
    fun retry() { retryTick.value++ }

    fun onPlaceSelected(place: Place) {
        selected.value = place
        viewModelScope.launch {
            // Une erreur de stockage ne doit pas faire planter l'app.
            runCatching { recents.record(place) }
        }
    }

    fun onDismissPlace() { selected.value = null }

    fun onToggleFavorite() {
        val p = selected.value ?: return
        viewModelScope.launch { favorites.toggle(p) }
    }

    fun onSetHome() { val p = selected.value ?: return; viewModelScope.launch { favorites.setHome(p) } }
    fun onSetWork() { val p = selected.value ?: return; viewModelScope.launch { favorites.setWork(p) } }
}
