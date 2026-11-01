package fr.roadly.core.assistant.tools

import fr.roadly.core.common.Outcome
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
    /** Succès (éventuellement vide) pour tous les chemins ; échec seulement si la recherche de lieu échoue. */
    suspend fun resolve(query: String?, placeId: String?): Outcome<List<Place>> {
        placeId?.let { places[it] }?.let { return Outcome.Success(listOf(it)) }
        val raw = query?.trim()?.takeIf { it.isNotEmpty() } ?: return Outcome.Success(emptyList())
        val q = normalize(raw)
        if (HOME.matches(q)) favorites.observeHome().first()?.let { return Outcome.Success(listOf(it.also(places::remember))) }
        if (WORK.matches(q)) favorites.observeWork().first()?.let { return Outcome.Success(listOf(it.also(places::remember))) }
        (favorites.observeAll().first() + recents.observe().first()).firstOrNull { normalize(it.name) == q }?.let {
            places.remember(it)
            return Outcome.Success(listOf(it))
        }
        return when (val r = geocoder.search(raw, location.lastKnown?.point, 3)) {
            is Outcome.Success -> Outcome.Success(r.value.take(3).onEach { places.remember(it) })
            is Outcome.Failure -> r
        }
    }

    private fun normalize(s: String): String = Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private companion object {
        /** Requête normalisée entière : « maison », « à la maison », « chez moi »… mais pas « Maison Carrée ». */
        val HOME = Regex("^((a |au )?(la |ma |mon |le )?(maison|domicile)|chez moi)$")
        val WORK = Regex("^(a |au )?(la |ma |mon |le )?(travail|boulot|bureau)$")
    }
}
