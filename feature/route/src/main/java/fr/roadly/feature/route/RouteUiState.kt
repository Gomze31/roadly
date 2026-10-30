package fr.roadly.feature.route

import fr.roadly.core.common.AppError
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.routing.RouteOption

sealed interface RouteUiState {
    data object Loading : RouteUiState
    data class NoOrigin(val destinationName: String) : RouteUiState
    data class Error(val error: AppError) : RouteUiState
    data class Content(
        val origin: GeoPoint,
        val destinationName: String,
        val options: List<RouteOption>,
        val activeId: String,
        val hasVehicleProfile: Boolean,
        val departAtEpochMs: Long,
    ) : RouteUiState
}

/** État exposé à l'écran : la carte est affichée dès que [styleUrl] est connu, quelle que soit la [phase]. */
data class RouteScreenState(
    val styleUrl: String = "",
    val phase: RouteUiState = RouteUiState.Loading,
)
