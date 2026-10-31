package fr.roadly.feature.route

import fr.roadly.core.common.AppError
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.routing.RouteOption

sealed interface RouteUiState {
    data object Loading : RouteUiState
    data class NoOrigin(val destinationName: String) : RouteUiState
    data class Error(val error: AppError) : RouteUiState
    /**
     * [arriveAtEpochMs] non nul = mode « Arriver à » : [departAtEpochMs] est déduit de la durée du plus rapide,
     * et les heures d'arrivée des options sont recalées sur ce départ.
     */
    data class Content(
        val origin: GeoPoint,
        val destinationName: String,
        val options: List<RouteOption>,
        val activeId: String,
        val hasVehicleProfile: Boolean,
        val departAtEpochMs: Long,
        val arriveAtEpochMs: Long? = null,
        val preferences: RoutePreferences = RoutePreferences(),
        /** Arrêts intermédiaires dans l'ordre de passage (≤ 10). */
        val stops: List<Place> = emptyList(),
        /** Optimisation de l'ordre (mode tournée) en cours. */
        val optimizing: Boolean = false,
    ) : RouteUiState
}

/** État exposé à l'écran : la carte est affichée dès que [styleUrl] est connu, quelle que soit la [phase]. */
data class RouteScreenState(
    val styleUrl: String = "",
    val phase: RouteUiState = RouteUiState.Loading,
)
