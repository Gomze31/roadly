package fr.roadly.feature.search

import fr.roadly.core.common.AppError
import fr.roadly.core.model.Place

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    data class Content(val places: List<Place>) : SearchResults
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
)
