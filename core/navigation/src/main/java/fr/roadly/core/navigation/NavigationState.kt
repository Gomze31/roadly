package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.model.Lane
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.scooter.guidance.ScooterNavigationUiState

/** Portiques de péage en flux libre franchis pendant une course ; [firstPassedAtEpochMs] fixe l'échéance de paiement. */
data class FreeFlowTollTrip(val gantries: Int, val roads: List<String>, val countryCode: String?, val firstPassedAtEpochMs: Long) {
    /** France : 72 h pour payer après le passage ; ailleurs aucune échéance n'est affichée. */
    val dueAtEpochMs: Long? get() = if (countryCode == "FR") firstPassedAtEpochMs + fr.roadly.core.data.repository.TollReminder.FR_PAYMENT_DELAY_MS else null
}

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
        /** Mode trottinette : voie courante, transitions, avertissements et autonomie estimée ; null sinon. */
        val scooter: ScooterNavigationUiState? = null,
        /** B4 : rond-point de la prochaine manœuvre, ou dans lequel on se trouve ; null sinon. */
        val roundabout: RoundaboutInfo? = null,
        /** 1.0 : prochaine limitation différente sur le tracé (profil `trace_attributes`) ; null = aucune connue à l'horizon. */
        val nextSpeedLimit: SpeedLimitChange? = null,
        /** 1.0 : portiques de péage en flux libre franchis pendant la course ; null = aucun. */
        val freeFlowToll: FreeFlowTollTrip? = null,
        /** Radar Intelligence : zone de danger dans laquelle on se trouve ([ZoneStatus.distanceM] = reste à parcourir). */
        val radarActive: fr.roadly.core.radar.ZoneStatus? = null,
        /** Radar Intelligence : prochaine zone de danger devant (≤ 3 km), [ZoneStatus.distanceM] = distance avant son début. */
        val radarNext: fr.roadly.core.radar.ZoneStatus? = null,
        /** Vue 3D de carrefour : manœuvre retenue et étape d'anticipation ; null = aucune (ou réglage désactivé). */
        val junction: fr.roadly.core.model.junction.JunctionGuidance? = null,
        /** La prochaine manœuvre a lieu à un feu tricolore (profil OSM du tracé). */
        val nextAtTrafficLight: Boolean = false,
    ) : NavigationState

    data class Arrived(val route: Route, val freeFlowToll: FreeFlowTollTrip? = null) : NavigationState
    data class Failed(val error: AppError) : NavigationState
}
