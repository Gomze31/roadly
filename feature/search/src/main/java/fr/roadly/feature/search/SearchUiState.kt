package fr.roadly.feature.search

import fr.roadly.core.common.AppError
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.Place

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    /** [category] non nul = résultats « à proximité » d'une catégorie (triés par distance). */
    data class Content(val places: List<Place>, val category: PoiCategory? = null) : SearchResults
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
)
