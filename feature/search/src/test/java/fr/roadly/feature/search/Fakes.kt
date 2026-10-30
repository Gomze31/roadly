package fr.roadly.feature.search

import fr.roadly.core.common.Outcome
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGeocoder : GeocodingProvider {
    override val id = "fake"
    var next: Outcome<List<Place>> = Outcome.Success(emptyList())
    val queries = mutableListOf<String>()
    override suspend fun search(query: String, near: GeoPoint?, limit: Int): Outcome<List<Place>> { queries += query; return next }
    override suspend fun reverse(point: GeoPoint): Outcome<Place?> = Outcome.Success(null)
}

class FakeRecents : RecentSearchRepository {
    val items = MutableStateFlow<List<Place>>(emptyList())
    override fun observe() = items
    override suspend fun record(place: Place) { items.value = listOf(place) + items.value.filter { it.id != place.id } }
    override suspend fun clear() { items.value = emptyList() }
}

class FakeFavorites : FavoritesRepository {
    val items = MutableStateFlow<List<Place>>(emptyList())
    var home = MutableStateFlow<Place?>(null)
    var work = MutableStateFlow<Place?>(null)
    override fun observeAll() = items
    override fun observeHome() = home
    override fun observeWork() = work
    override suspend fun isFavorite(id: String) = items.value.any { it.id == id }
    override suspend fun toggle(place: Place) { items.value = if (isFavorite(place.id)) items.value.filter { it.id != place.id } else items.value + place }
    override suspend fun setHome(place: Place) { home.value = place }
    override suspend fun setWork(place: Place) { work.value = place }
    override suspend fun clear() { items.value = emptyList() }
}

class FakeConnectivity(initial: Boolean = true) : ConnectivityObserver {
    val online = MutableStateFlow(initial)
    override val isOnline = online
}
