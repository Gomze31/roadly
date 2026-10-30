package fr.roadly.feature.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.model.Lane
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.NavigationState
import java.time.ZoneId

/** LOADING = avant la première émission ; IDLE = guidage terminé → l'écran se ferme. */
enum class NavigationPhase { LOADING, GUIDING, ARRIVED, FAILED, IDLE }

data class BannerUi(
    val type: ManeuverType,
    val distanceText: String,
    val instruction: String,
    val street: String?,
    val exitNumber: String?,
    val roundaboutExit: Int?,
)

data class UpcomingUi(val type: ManeuverType, val distanceText: String, val street: String?)

data class NavigationUiState(
    val phase: NavigationPhase = NavigationPhase.LOADING,
    val styleUrl: String = "",
    val route: Route? = null,
    val progress: RouteProgress? = null,
    val banner: BannerUi? = null,
    val lanes: List<Lane> = emptyList(),
    val upcoming: List<UpcomingUi> = emptyList(),
    val etaClock: String = "",
    val remainingDuration: String = "",
    val remainingDistance: String = "",
    val speedKmh: Double? = null,
    val speedLimitKmh: Int? = null,
    val toleranceKmh: Int = GuidanceDefaults.SPEED_TOLERANCE_KMH,
    val gpsLost: Boolean = false,
    val rerouting: Boolean = false,
    val muted: Boolean = false,
    val following: Boolean = true,
    val error: AppError? = null,
)

/** Formatage pur (testé via le ViewModel) : distances et durées par `Formatters`, comme la v1. */
internal fun buildUiState(s: NavigationState, muted: Boolean, following: Boolean, toleranceKmh: Int, styleUrl: String, zone: ZoneId): NavigationUiState {
    val base = NavigationUiState(styleUrl = styleUrl, muted = muted, following = following, toleranceKmh = toleranceKmh)
    return when (s) {
        NavigationState.Idle -> base.copy(phase = NavigationPhase.IDLE)
        is NavigationState.Failed -> base.copy(phase = NavigationPhase.FAILED, error = s.error)
        is NavigationState.Arrived -> base.copy(phase = NavigationPhase.ARRIVED, route = s.route)
        is NavigationState.Guiding -> {
            val p = s.progress
            val ms = s.route.maneuvers
            val next = p.nextManeuverIndex.coerceIn(0, ms.lastIndex)
            val m = ms[next]
            // Distance jusqu'à la manœuvre next+1+i = distance à next + longueurs Valhalla de next … next+i.
            var acc = p.distanceToNextManeuverM
            val upcoming = s.upcoming.mapIndexed { i, u ->
                acc += ms[next + i].lengthMeters
                UpcomingUi(u.type, Formatters.distance(acc), u.streetName)
            }
            base.copy(
                phase = NavigationPhase.GUIDING,
                route = s.route,
                progress = p,
                banner = BannerUi(m.type, Formatters.distance(p.distanceToNextManeuverM), m.instruction, m.streetName, m.exitNumber, m.roundaboutExit),
                lanes = s.lanes,
                upcoming = upcoming,
                etaClock = Formatters.clockTime(s.etaEpochMs, zone),
                remainingDuration = Formatters.duration(p.remainingDurationS),
                remainingDistance = Formatters.distance(p.remainingDistanceM),
                speedKmh = s.speedKmh,
                speedLimitKmh = s.speedLimitKmh,
                gpsLost = s.gpsLost,
                rerouting = s.rerouting,
            )
        }
    }
}
