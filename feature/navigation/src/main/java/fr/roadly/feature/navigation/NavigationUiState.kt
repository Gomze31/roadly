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
import java.util.Locale
import kotlin.math.roundToInt

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

/** Manœuvre à venir ; [distanceM] sert au ruban Horizon (échelle), [distanceText] à l'affichage. */
data class UpcomingUi(val type: ManeuverType, val distanceText: String, val street: String?, val distanceM: Double = 0.0)

/** Valeur et unité séparées pour la barre d'information (« 6 » + « min », « 2,1 » + « km »). */
data class SplitValue(val value: String, val unit: String)

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
    val remainingDurationSplit: SplitValue = SplitValue("", ""),
    val remainingDistanceSplit: SplitValue = SplitValue("", ""),
    val speedKmh: Double? = null,
    val speedLimitKmh: Int? = null,
    val toleranceKmh: Int = GuidanceDefaults.SPEED_TOLERANCE_KMH,
    val gpsLost: Boolean = false,
    val rerouting: Boolean = false,
    val muted: Boolean = false,
    val following: Boolean = true,
    val error: AppError? = null,
)

/** « 6 » / « min » sous l'heure ; « 1 h 12 » / « restant » au-delà d'une heure. */
internal fun splitDuration(seconds: Double): SplitValue {
    val totalMin = (seconds / 60).roundToInt()
    return if (totalMin < 60) SplitValue(totalMin.toString(), "min") else SplitValue(Formatters.duration(seconds), "restant")
}

/** « 850 » / « m » sous 1 km ; « 2,1 » / « km » ensuite ; entier au-delà de 10 km. */
internal fun splitDistance(meters: Double): SplitValue = when {
    meters < 1000 -> SplitValue(((meters / 10).roundToInt() * 10).toString(), "m")
    meters < 10_000 -> SplitValue(String.format(Locale.FRANCE, "%.1f", meters / 1000), "km")
    else -> SplitValue((meters / 1000).roundToInt().toString(), "km")
}

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
                UpcomingUi(u.type, Formatters.distance(acc), u.streetName, acc)
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
                remainingDurationSplit = splitDuration(p.remainingDurationS),
                remainingDistanceSplit = splitDistance(p.remainingDistanceM),
                speedKmh = s.speedKmh,
                speedLimitKmh = s.speedLimitKmh,
                gpsLost = s.gpsLost,
                rerouting = s.rerouting,
            )
        }
    }
}
