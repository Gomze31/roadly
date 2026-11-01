package fr.roadly.core.scooter.analysis

import fr.roadly.core.model.ScooterRouteGoal

/** Une variante à classer : [analysis] null = analyse indisponible (elle n'obtient alors aucun label sécurité / confort). */
data class RankInput(val routeId: String, val goal: ScooterRouteGoal?, val distanceM: Double, val durationS: Double, val analysis: ScooterRouteAnalysis?)

/**
 * Désigne, parmi les variantes, la plus rapide, la plus courte, la plus sécurisée et la plus confortable.
 * Les indices sécurité / confort ne sont jamais affichés comme des notes : ce sont des longueurs pondérées issues des
 * données connues, utilisées seulement pour comparer des variantes entre elles.
 */
object ScooterRouteRanking {
    /** Pénalité de sécurité (m équivalents) : routes partagées pondérées par la limitation, portions interdites / non vérifiées. */
    fun riskIndex(a: ScooterRouteAnalysis): Double = a.segments.sumOf { s ->
        val base = when (s.category) {
            fr.roadly.core.scooter.segment.WayCategory.SHARED_ROAD -> when (val l = s.speedLimitKmh) {
                null -> 1.5
                in 0..30 -> 1.0
                in 31..50 -> 2.0
                else -> 4.0
            }
            fr.roadly.core.scooter.segment.WayCategory.CYCLE_LANE -> 0.6
            fr.roadly.core.scooter.segment.WayCategory.LIVING_STREET, fr.roadly.core.scooter.segment.WayCategory.PEDESTRIAN_ZONE -> 0.4
            fr.roadly.core.scooter.segment.WayCategory.CYCLE_TRACK, fr.roadly.core.scooter.segment.WayCategory.GREENWAY -> 0.1
            else -> 0.8
        }
        val legal = when (s.legal.status) {
            fr.roadly.core.scooter.regulation.LegalStatus.FORBIDDEN -> 20.0
            fr.roadly.core.scooter.regulation.LegalStatus.NOT_RIDABLE -> 5.0
            fr.roadly.core.scooter.regulation.LegalStatus.UNVERIFIED -> 0.5
            fr.roadly.core.scooter.regulation.LegalStatus.ALLOWED -> 0.0
        }
        (base + legal) * s.lengthM
    } + a.warnings.count { it.kind == ScooterWarningKind.COMPLEX_INTERSECTION } * 150.0

    /** Inconfort : pavés, non revêtu, pentes fortes (m équivalents). */
    fun discomfortIndex(a: ScooterRouteAnalysis): Double = a.summary.roughLengthM * 2.0 + a.summary.unpavedLengthM * 3.0 +
        a.segments.filter { (it.gradePct ?: 0.0) >= ScooterComfortAnalyzer.STEEP_GRADE_PCT }.sumOf { it.lengthM * 2.0 } +
        (a.summary.ascentM ?: 0.0) * 10.0

    fun labels(inputs: List<RankInput>): Map<String, List<String>> {
        if (inputs.isEmpty()) return emptyMap()
        val out = inputs.associate { it.routeId to mutableListOf<String>() }
        inputs.minBy { it.durationS }.let { out.getValue(it.routeId) += "Le plus rapide" }
        val analyzed = inputs.filter { it.analysis != null }
        if (analyzed.size >= 1) {
            analyzed.minBy { riskIndex(it.analysis!!) / it.distanceM.coerceAtLeast(1.0) }.let { out.getValue(it.routeId) += "Le plus sécurisé" }
            analyzed.minBy { discomfortIndex(it.analysis!!) / it.distanceM.coerceAtLeast(1.0) }.let { out.getValue(it.routeId) += "Le plus confortable" }
        }
        inputs.minBy { it.distanceM }.let { out.getValue(it.routeId) += "Le plus court" }
        return out
    }
}
