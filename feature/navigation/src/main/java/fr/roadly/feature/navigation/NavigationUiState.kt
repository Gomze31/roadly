package fr.roadly.feature.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.map.GuidanceCameraPolicy
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.Lane
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RoundaboutInfo
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
    /** Direction du panneau de sortie (« Lyon · Mâcon »), si Valhalla la fournit. */
    val exitToward: String? = null,
)

/** Manœuvre à venir ; [distanceM] sert au ruban Horizon (échelle), [distanceText] à l'affichage ; [instruction] et [sign] servent à l'aperçu (D1, D3). */
data class UpcomingUi(
    val type: ManeuverType,
    val distanceText: String,
    val street: String?,
    val distanceM: Double = 0.0,
    val instruction: String? = null,
    val sign: ExitSignUi? = null,
)

/** D1 : manœuvre d'après, montrée sous le bandeau (« puis ↱ Rue X ») quand elle suit de près. */
data class ThenUi(val type: ManeuverType, val label: String)

/** D3 : panneau d'une manœuvre (sortie, bretelle, fourche, fusion), tel que Valhalla le décrit ; [toward] = « A · B » (2 directions au plus). */
data class ExitSignUi(val exitNumber: String?, val toward: String?, val branch: String?)

/** D4 : rendu d'une voie : conseillée, possible mais non conseillée, interdite pour la manœuvre. */
enum class LaneState { ACTIVE, VALID, INVALID }

/** D5 : étape de la feuille « Trajet complet » ; [lengthText] = longueur Valhalla de l'étape (null à l'arrivée), jamais cumulée (B2). */
data class TripStepUi(
    val index: Int,
    val type: ManeuverType,
    val instruction: String,
    val street: String?,
    val lengthText: String?,
    val passed: Boolean,
    val isNext: Boolean,
)

/** Feuille ouverte au-dessus du guidage : aperçu de la manœuvre (D1) ou trajet complet (D5). */
enum class GuidanceSheet { NONE, MANEUVER_PREVIEW, TRIP_DETAILS }

/** Approche d'une intersection (vue serrée, flèche, panneau) ; [maneuverShapeIndex] = point du tracé où dessiner la flèche. */
data class ApproachUi(val type: ManeuverType, val exitNumber: String?, val exitToward: String?, val maneuverShapeIndex: Int)

/** Virage ou zone de vigilance devant soi : [distanceM] jusqu'au début, [limitKmh] si connue, [angleDeg] pour un virage. */
data class HazardUi(val kind: HazardKind, val distanceM: Double, val distanceText: String, val limitKmh: Int?, val angleDeg: Double?)

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
    val approach: ApproachUi? = null,
    /** Virages et zones devant soi (≤ 5 km), triés par distance. */
    val hazards: List<HazardUi> = emptyList(),
    val activeZone: RouteHazard? = null,
    /** Signalements communautaires devant soi (projetés sur le tracé), fournis par le ViewModel. */
    val communityAhead: List<ReportAheadUi> = emptyList(),
    val raining: Boolean? = null,
    val limitLoweredByRain: Boolean = false,
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
    /** D2 : rond-point de la prochaine manœuvre (entrée ou sortie d'anneau) ; null sinon. */
    val roundabout: RoundaboutInfo? = null,
    /** D1 : manœuvre d'après quand elle suit la prochaine de moins de [THEN_WITHIN_M]. */
    val then: ThenUi? = null,
    /** D3 : panneau de la prochaine manœuvre, si Valhalla en fournit un (bandeau à l'approche, aperçu toujours). */
    val sign: ExitSignUi? = null,
    /** D4 : voies montrées sous le bandeau (dans les [LANES_FROM_M] derniers mètres ou à l'approche, jamais sans données). */
    val showLanes: Boolean = false,
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

/** Portée de l'Horizon (m) : au-delà, les événements ne sont pas listés. */
internal const val HORIZON_RANGE_M = 5_000.0

/** Événements dont le début est devant la position (ou en cours), à moins de [HORIZON_RANGE_M] ; limite : virages et zones gardent les abscisses de la géométrie brute, écart < 2 % avec les manœuvres (0,06 % sur Paris, `RouteTrackerTest`). */
internal fun hazardsAhead(hazards: List<RouteHazard>, alongM: Double): List<HazardUi> =
    hazards.filter { it.endAlongM >= alongM && it.startAlongM - alongM <= HORIZON_RANGE_M }
        .map { h ->
            val d = (h.startAlongM - alongM).coerceAtLeast(0.0)
            HazardUi(h.kind, d, Formatters.distance(d), h.speedLimitKmh, h.angleDeg)
        }
        .sortedBy { it.distanceM }

/** B1 : une approche reste active jusqu'au changement de manœuvre visée (pas de clignotement au seuil des 150 / 400 m). */
internal fun approachLatched(previous: NavigationUiState?, routeId: String, nextIndex: Int, approachingNow: Boolean): Boolean {
    if (approachingNow) return true
    val prev = previous ?: return false
    return prev.approach != null && prev.route?.id == routeId && prev.progress?.nextManeuverIndex == nextIndex
}

/** Formatage pur (testé via le ViewModel) ; [previous] = dernier état produit (hystérésis de l'approche, B1). */
internal fun buildUiState(s: NavigationState, muted: Boolean, following: Boolean, toleranceKmh: Int, styleUrl: String, zone: ZoneId, previous: NavigationUiState? = null): NavigationUiState {
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
            // B2 : distances aux manœuvres suivantes fournies par le suivi, sur la même abscisse que le bandeau ; sans distance, pas d'élément.
            val upcoming = s.upcoming.mapIndexedNotNull { i, u ->
                val d = p.distancesToManeuversM.getOrNull(i) ?: return@mapIndexedNotNull null
                UpcomingUi(u.type, Formatters.maneuverDistance(d), u.streetName, d, u.instruction, exitSignFor(u))
            }
            val approaching = approachLatched(previous, s.route.id, p.nextManeuverIndex, GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh))
            base.copy(
                phase = NavigationPhase.GUIDING,
                route = s.route,
                progress = p,
                banner = BannerUi(m.type, Formatters.maneuverDistance(p.distanceToNextManeuverM), m.instruction, m.streetName, m.exitNumber, m.roundaboutExit, m.exitToward),
                approach = if (approaching) ApproachUi(m.type, m.exitNumber, m.exitToward, m.beginShapeIndex) else null,
                roundabout = s.roundabout?.takeIf { m.type == ManeuverType.ROUNDABOUT_ENTER || m.type == ManeuverType.ROUNDABOUT_EXIT },
                then = thenFor(s.upcoming.firstOrNull(), p.distancesToManeuversM.firstOrNull()?.let { it - p.distanceToNextManeuverM }),
                sign = exitSignFor(m),
                showLanes = lanesVisible(s.lanes, p.distanceToNextManeuverM, approaching),
                lanes = s.lanes,
                upcoming = upcoming,
                hazards = hazardsAhead(s.hazards, p.distanceAlongM),
                activeZone = s.activeZone,
                raining = s.raining,
                limitLoweredByRain = s.limitLoweredByRain,
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

/** D1 : la manœuvre d'après est montrée sous le bandeau quand elle suit la prochaine de moins de 500 m. */
internal const val THEN_WITHIN_M = 500.0

/** D4 : voies montrées à partir de 400 m de la manœuvre (même seuil que l'approche d'une sortie). */
internal const val LANES_FROM_M = 400.0

/** D3 : manœuvres qui portent un panneau de direction. */
private val SIGN_TYPES = setOf(
    ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT,
    ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT,
)

/** D3 : panneau de [m] (numéro, directions, route) ; null hors sortie / bretelle / fourche / fusion ou sans aucune donnée de panneau. */
internal fun exitSignFor(m: Maneuver): ExitSignUi? {
    if (m.type !in SIGN_TYPES) return null
    if (m.exitNumber == null && m.exitToward == null && m.exitBranch == null) return null
    return ExitSignUi(m.exitNumber, m.exitToward, m.exitBranch)
}

/** D1 : « puis … » pour [next] situé [gapM] mètres après la prochaine manœuvre ; rue, sinon instruction. */
internal fun thenFor(next: Maneuver?, gapM: Double?): ThenUi? {
    if (next == null || gapM == null || gapM >= THEN_WITHIN_M) return null
    return ThenUi(next.type, next.streetName ?: next.instruction)
}

/** D4 : voies visibles dans les [LANES_FROM_M] derniers mètres ou à l'approche ; jamais sans données (LIMITE : FOSSGIS public n'en renvoie pas). */
internal fun lanesVisible(lanes: List<Lane>, distanceToNextM: Double, approaching: Boolean): Boolean =
    lanes.isNotEmpty() && (approaching || distanceToNextM < LANES_FROM_M)

/** D4 : état de chaque voie ; sans voie « active » (serveur qui ne renvoie que `valid`), les voies valides sont les voies conseillées. */
internal fun laneStates(lanes: List<Lane>): List<LaneState> {
    val anyActive = lanes.any { it.active }
    return lanes.map { l ->
        when {
            l.active -> LaneState.ACTIVE
            l.valid && !anyActive -> LaneState.ACTIVE
            l.valid -> LaneState.VALID
            else -> LaneState.INVALID
        }
    }
}

/** D5 : toutes les étapes de [route] ; celles avant [nextIndex] sont passées. Longueur Valhalla de l'étape, non cumulée (B2). */
internal fun tripSteps(route: Route, nextIndex: Int): List<TripStepUi> = route.maneuvers.mapIndexed { i, m ->
    TripStepUi(
        index = i, type = m.type, instruction = m.instruction, street = m.streetName,
        lengthText = m.lengthMeters.takeIf { it > 0.0 && m.type != ManeuverType.ARRIVE }?.let { Formatters.distance(it) },
        passed = i < nextIndex, isNext = i == nextIndex,
    )
}
