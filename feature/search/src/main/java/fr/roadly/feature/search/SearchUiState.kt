package fr.roadly.feature.search

import fr.roadly.core.common.AppError
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.Place

/** Tri des stations-service : distance (défaut) ou prix du carburant choisi. */
enum class FuelSort { DISTANCE, PRICE }

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    /**
     * [category] non nul = résultats « à proximité » (triés par distance).
     * [stations] : détails (prix, services) des stations-service par `Place.id`, quand la catégorie est FUEL.
     */
    data class Content(val places: List<Place>, val category: PoiCategory? = null, val stations: Map<String, FuelStation> = emptyMap()) : SearchResults
    data class Error(val error: AppError) : SearchResults
}

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults.Idle,
    val recents: List<Place> = emptyList(),
    val favorites: List<Place> = emptyList(),
    val isOnline: Boolean = true,
    val selected: Place? = null,
    val selectedIsFavorite: Boolean = false,
    val selectedDistanceMeters: Double? = null,
    /** Catégorie active (puce sélectionnée) ; null = recherche textuelle. */
    val category: PoiCategory? = null,
    /** Position de référence des distances affichées ; null si inconnue. */
    val hereLat: Double? = null,
    val hereLon: Double? = null,
    /** Carburant dont le prix est mis en avant (profil véhicule par défaut) et tri des stations. */
    val fuel: FuelType = FuelType.DIESEL,
    val fuelSort: FuelSort = FuelSort.DISTANCE,
)

/** Options du trajet affichées sous la barre de recherche. [tripOnly] = choisies pour ce trajet (pas encore enregistrées). */
data class TripOptionsUi(
    val preferences: fr.roadly.core.model.RoutePreferences = fr.roadly.core.model.RoutePreferences(),
    val remember: Boolean = false,
    val departure: fr.roadly.core.data.repository.DeparturePlan? = null,
    val tripOnly: Boolean = false,
)
