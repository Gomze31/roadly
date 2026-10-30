package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.model.Lane
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.RouteProgress

/** État publié par `NavigationSession` (spec §4 ; dans core:navigation car il porte un AppError). */
sealed interface NavigationState {
    data object Idle : NavigationState

    /** [upcoming] = manœuvres next+1..next+3 ; [lanes] = voies de la manœuvre next ; [speedLimitKmh] null = inconnue (« — »). */
    data class Guiding(
        val route: Route,
        val progress: RouteProgress,
        val speedKmh: Double?,
        val speedLimitKmh: Int?,
        val upcoming: List<Maneuver>,
        val lanes: List<Lane>,
        val etaEpochMs: Long,
        val gpsLost: Boolean,
        val rerouting: Boolean,
        /** Virages et zones de vigilance sur tout le tracé (abscisses curvilignes) ; l'écran filtre ce qui est devant. */
        val hazards: List<RouteHazard> = emptyList(),
        /** Zone de vigilance dans laquelle on se trouve, sinon null. */
        val activeZone: RouteHazard? = null,
        /** Pluie détectée (Open-Meteo) ; null = inconnue. */
        val raining: Boolean? = null,
        /** `speedLimitKmh` a été abaissé pour la pluie (option). */
        val limitLoweredByRain: Boolean = false,
    ) : NavigationState

    data class Arrived(val route: Route) : NavigationState
    data class Failed(val error: AppError) : NavigationState
}
