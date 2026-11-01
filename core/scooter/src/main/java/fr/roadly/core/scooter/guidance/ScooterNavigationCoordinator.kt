package fr.roadly.core.scooter.guidance

import fr.roadly.core.model.Route
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysis
import fr.roadly.core.scooter.analysis.ScooterWarningKind
import fr.roadly.core.scooter.analysis.WarningSeverity
import fr.roadly.core.scooter.geo.RouteLine
import fr.roadly.core.scooter.range.EnergyLeg
import fr.roadly.core.scooter.range.RangeRisk
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import fr.roadly.core.scooter.regulation.LegalAssessment
import fr.roadly.core.scooter.segment.ScooterRouteSegment
import fr.roadly.core.scooter.segment.SurfaceQuality
import fr.roadly.core.scooter.segment.WayCategory

data class UpcomingWarning(val kind: ScooterWarningKind, val severity: WarningSeverity, val distanceM: Double, val detail: String)

data class CategoryAhead(val category: WayCategory, val distanceM: Double, val lengthM: Double)

/** Autonomie pendant la course : tout est estimé à partir de la batterie saisie, jamais lu sur la trottinette. */
data class GuidanceRange(
    val batteryNowPct: Double,
    val arrivalPct: Double,
    val risk: RangeRisk,
    val remainingRangeKm: Double,
    val energyUsedWh: Double,
)

/** État spécifique trottinette de l'écran de guidage. */
data class ScooterNavigationUiState(
    val analysisAvailable: Boolean = false,
    val osmTagsAvailable: Boolean = false,
    val currentCategory: WayCategory? = null,
    val currentLegal: LegalAssessment? = null,
    val currentSurface: SurfaceQuality = SurfaceQuality.UNKNOWN,
    val currentGradePct: Double? = null,
    val currentSpeedLimitKmh: Int? = null,
    val nextChange: CategoryAhead? = null,
    val nextCycleInfrastructure: CategoryAhead? = null,
    val upcomingWarnings: List<UpcomingWarning> = emptyList(),
    val range: GuidanceRange? = null,
    /** Part restante en pistes / bandes / voies vertes (0–1), null sans analyse. */
    val remainingCycleShare: Double? = null,
    val traversedAscentM: Double? = null,
)

data class ScooterGuidanceUpdate(val ui: ScooterNavigationUiState, val speech: List<String>)

/**
 * Coordonne le guidage trottinette au-dessus du moteur commun : formulation des manœuvres selon la voie, annonces
 * de transitions (route partagée, voie verte), avertissements anticipés et suivi d'autonomie.
 * Pur et sans horloge : appelé à chaque progression par la session de guidage.
 */
class ScooterNavigationCoordinator(
    private val route: Route,
    private val analysis: ScooterRouteAnalysis?,
    private val estimator: ScooterRangeEstimator?,
    startBatteryPct: Double?,
    private val temperatureC: Double?,
    private val cruiseSpeedKmh: Double,
    /** Reprise après un recalcul : énergie et dénivelé déjà parcourus sur l'ancien tracé. */
    carriedEnergyWh: Double = 0.0,
    carriedModelEnergyWh: Double = 0.0,
    carriedAscentM: Double = 0.0,
) {
    companion object {
        const val WARNING_WINDOW_M = 350.0
        const val SHARED_ROAD_WINDOW_M = 150.0
        const val CROSSING_WINDOW_M = 60.0
        const val CONTINUE_MIN_LENGTH_M = 400.0
        const val UPCOMING_RANGE_M = 1_000.0
        const val MIN_SPEED_FOR_MODEL_KMH = 5.0
    }

    private val line = RouteLine(route.geometry)
    private val segments: List<ScooterRouteSegment> = analysis?.segments.orEmpty()
    private val announced = mutableSetOf<String>()
    private var batteryBasePct: Double? = startBatteryPct
    private var energySinceBaseWh = 0.0
    private var energyTotalWh = carriedEnergyWh
    private var modelEnergyTotalWh = carriedModelEnergyWh
    private var lastAlongM: Double? = null
    private var worstRiskAnnounced: RangeRisk? = null
    private var ascentDoneM = carriedAscentM
    private var descentDoneM = 0.0

    /** Nouvelle valeur de batterie saisie en cours de route : devient la référence. */
    fun setBattery(pct: Double) {
        batteryBasePct = pct.coerceIn(0.0, 100.0)
        energySinceBaseWh = 0.0
        worstRiskAnnounced = null
    }

    val energyUsedWh: Double get() = energyTotalWh
    /** Énergie du modèle non calibré : enregistrée avec le trajet pour la calibration. */
    val modelEnergyUsedWh: Double get() = modelEnergyTotalWh
    val ascentDone: Double get() = ascentDoneM
    val descentDone: Double get() = descentDoneM
    val hasGradeData: Boolean get() = segments.any { it.gradePct != null }

    /** Batterie estimée maintenant (%), null sans saisie ou sans capacité connue. */
    fun currentBatteryPct(): Double? {
        val base = batteryBasePct ?: return null
        val used = estimator?.pctFor(energySinceBaseWh, temperatureC) ?: return null
        return (base - used).coerceAtLeast(0.0)
    }

    fun segmentAt(alongM: Double): ScooterRouteSegment? = analysis?.segmentAt(alongM)

    /** Texte trottinette pour la manœuvre [index] au palier [stage] ; null = garder le texte Valhalla. */
    fun maneuverText(index: Int, stage: AnnouncementStage, distanceM: Double): String? {
        if (analysis == null) return null
        val m = route.maneuvers.getOrNull(index) ?: return null
        val along = line.alongAt(m.beginShapeIndex)
        val into = segmentAt(along + 5.0)
        if (into?.category == WayCategory.CROSSING) return if (stage == AnnouncementStage.NOW) null else ScooterPhrases.CROSSING
        val from = if (along > 5.0) segmentAt(along - 5.0)?.category else null
        val action = ScooterPhrases.maneuverAction(m, from, into?.category) ?: return null
        return ScooterPhrases.forStage(stage, distanceM, action)
    }

    fun update(alongM: Double, speedKmh: Double?, extrapolated: Boolean = false): ScooterGuidanceUpdate {
        val speech = ArrayList<String>()
        val speed = speedKmh?.takeIf { it >= MIN_SPEED_FOR_MODEL_KMH } ?: cruiseSpeedKmh
        accumulate(alongM, speed)
        val current = segmentAt(alongM)

        if (analysis != null && !extrapolated) {
            // Route partagée devant soi, après une portion d'infrastructure cyclable (ou au départ).
            nextSegment(alongM) { it.category == WayCategory.SHARED_ROAD && it.lengthM >= 30.0 }?.let { s ->
                val d = s.startAlongM - alongM
                val prev = segments.lastOrNull { it.endAlongM <= s.startAlongM + 0.5 && it !== s }
                if (d in 0.0..SHARED_ROAD_WINDOW_M && prev?.category != WayCategory.SHARED_ROAD && announced.add("shared@${s.startAlongM.toInt()}")) {
                    speech += ScooterPhrases.sharedRoadAhead(s.speedLimitKmh)
                }
            }
            nextSegment(alongM) { it.category == WayCategory.CROSSING }?.let { s ->
                if (s.startAlongM - alongM in 0.0..CROSSING_WINDOW_M && announced.add("cross@${s.startAlongM.toInt()}")) speech += ScooterPhrases.CROSSING
            }
            if (current != null && current.lengthM >= CONTINUE_MIN_LENGTH_M && alongM - current.startAlongM < 60.0 && current.endAlongM - alongM >= CONTINUE_MIN_LENGTH_M - 60.0) {
                val text = ScooterPhrases.continueAlong(current.category, current.endAlongM - alongM)
                if (text != null && announced.add("cont@${current.startAlongM.toInt()}")) speech += text
            }
            analysis.warnings.firstOrNull { w ->
                val d = w.startAlongM - alongM
                d > 0 && d <= WARNING_WINDOW_M && "w:${w.kind}@${w.startAlongM.toInt()}" !in announced && ScooterPhrases.warning(w.kind, d) != null
            }?.let { w ->
                announced += "w:${w.kind}@${w.startAlongM.toInt()}"
                speech += ScooterPhrases.warning(w.kind, w.startAlongM - alongM)!!
            }
        }

        val range = rangeState(alongM, speed)
        if (range != null && !extrapolated) {
            val worse = when (range.risk) {
                RangeRisk.INSUFFICIENT -> worstRiskAnnounced != RangeRisk.INSUFFICIENT
                RangeRisk.TIGHT -> worstRiskAnnounced == null || worstRiskAnnounced == RangeRisk.OK
                else -> false
            }
            if (worse) {
                speech += if (range.risk == RangeRisk.INSUFFICIENT) ScooterPhrases.INSUFFICIENT_RANGE else ScooterPhrases.TIGHT_RANGE
            }
            if (worstRiskAnnounced == null || range.risk.ordinal > worstRiskAnnounced!!.ordinal) worstRiskAnnounced = range.risk
        }

        val remaining = segments.filter { it.endAlongM > alongM }
        val remainingLen = remaining.sumOf { it.endAlongM - maxOf(it.startAlongM, alongM) }
        val remainingCycle = remaining.filter { it.category.isCycleInfrastructure }.sumOf { it.endAlongM - maxOf(it.startAlongM, alongM) }
        val ui = ScooterNavigationUiState(
            analysisAvailable = analysis != null,
            osmTagsAvailable = analysis?.osmTagsAvailable == true,
            currentCategory = current?.category,
            currentLegal = current?.legal,
            currentSurface = current?.surface ?: SurfaceQuality.UNKNOWN,
            currentGradePct = current?.gradePct,
            currentSpeedLimitKmh = current?.speedLimitKmh,
            nextChange = current?.let { c -> nextSegment(alongM) { it.category != c.category }?.let { CategoryAhead(it.category, it.startAlongM - alongM, it.lengthM) } },
            nextCycleInfrastructure = nextCycleInfrastructure(alongM),
            upcomingWarnings = analysis?.warnings.orEmpty()
                .filter { it.endAlongM >= alongM && it.startAlongM - alongM <= UPCOMING_RANGE_M && it.kind != ScooterWarningKind.UNVERIFIED_ACCESS }
                .map { UpcomingWarning(it.kind, it.severity, (it.startAlongM - alongM).coerceAtLeast(0.0), it.detail) }
                .take(3),
            range = range,
            remainingCycleShare = if (analysis != null && remainingLen > 0) remainingCycle / remainingLen else null,
            traversedAscentM = if (segments.any { it.gradePct != null }) ascentDoneM else null,
        )
        return ScooterGuidanceUpdate(ui, speech)
    }

    /** Prochaine portion de piste / bande / voie verte (celle en cours si l'on y est déjà). */
    fun nextCycleInfrastructure(alongM: Double): CategoryAhead? =
        segments.firstOrNull { it.endAlongM > alongM && it.category.isCycleInfrastructure }
            ?.let { CategoryAhead(it.category, (it.startAlongM - alongM).coerceAtLeast(0.0), it.lengthM) }

    private fun nextSegment(alongM: Double, predicate: (ScooterRouteSegment) -> Boolean): ScooterRouteSegment? =
        segments.firstOrNull { it.startAlongM > alongM && predicate(it) }

    /** Énergie consommée depuis la dernière position, à la vitesse réellement pratiquée, sur la portion parcourue. */
    private fun accumulate(alongM: Double, speedKmh: Double) {
        val est = estimator ?: run { lastAlongM = alongM; return }
        val from = lastAlongM
        lastAlongM = alongM
        if (from == null || alongM <= from) return
        val legs = legsBetween(from, alongM)
        val wh = est.energyWh(legs, speedKmh, temperatureC)
        energySinceBaseWh += wh
        energyTotalWh += wh
        modelEnergyTotalWh += est.modelEnergyWh(legs, speedKmh, temperatureC)
        ascentDoneM += legs.filter { it.gradePct > 0 }.sumOf { it.gradePct / 100.0 * it.lengthM }
        descentDoneM += legs.filter { it.gradePct < 0 }.sumOf { -it.gradePct / 100.0 * it.lengthM }
    }

    internal fun legsBetween(fromM: Double, toM: Double): List<EnergyLeg> {
        if (segments.isEmpty()) return listOf(EnergyLeg(toM - fromM))
        return segments.filter { it.endAlongM > fromM && it.startAlongM < toM }.map { s ->
            val len = minOf(s.endAlongM, toM) - maxOf(s.startAlongM, fromM)
            EnergyLeg(len, s.gradePct ?: 0.0, ScooterRangeEstimator.rollingFactor(s.surface))
        }.ifEmpty { listOf(EnergyLeg(toM - fromM)) }
    }

    private fun rangeState(alongM: Double, speedKmh: Double): GuidanceRange? {
        val est = estimator ?: return null
        val base = batteryBasePct ?: return null
        val usedPct = est.pctFor(energySinceBaseWh, temperatureC) ?: return null
        val nowPct = base - usedPct
        val remainingLegs = legsBetween(alongM, line.lengthM.coerceAtLeast(alongM))
        val estimate = est.estimate(remainingLegs, nowPct.coerceAtLeast(0.0), temperatureC, speedKmh)
        val arrival = estimate.arrivalBatteryPct ?: return null
        val risk = if (nowPct <= 0) RangeRisk.INSUFFICIENT else estimate.risk
        return GuidanceRange(nowPct.coerceAtLeast(0.0), arrival, risk, estimate.remainingRangeKm ?: 0.0, energyTotalWh)
    }
}
