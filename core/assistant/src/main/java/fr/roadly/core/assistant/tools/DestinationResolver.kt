package fr.roadly.core.assistant.tools

import fr.roadly.core.common.getOrNull
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.Place
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** Destination parlée → lieux : place_id connu, Domicile / Travail, favori ou récent de même nom, puis géocodage près de la position. */
class DestinationResolver @Inject constructor(
    private val favorites: FavoritesRepository,
    private val recents: RecentSearchRepository,
    private val geocoder: GeocodingProvider,
    private val location: LocationRepository,
    private val places: ToolPlaces,
) {
    suspend fun resolve(query: String?, placeId: String?): List<Place> {
        placeId?.let { places[it] }?.let { return listOf(it) }
        val raw = query?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val q = normalize(raw)
        if (HOME.any { q.contains(it) }) favorites.observeHome().first()?.let { return listOf(it) }
        if (WORK.any { q.contains(it) }) favorites.observeWork().first()?.let { return listOf(it) }
        (favorites.observeAll().first() + recents.observe().first()).firstOrNull { normalize(it.name) == q }?.let { places.remember(it); return listOf(it) }
        return geocoder.search(raw, location.lastKnown?.point, 3).getOrNull().orEmpty().take(3).onEach { places.remember(it) }
    }

    private fun normalize(s: String): String = Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private companion object {
        val HOME = listOf("maison", "domicile", "chez moi")
        val WORK = listOf("travail", "boulot", "bureau")
    }
}
