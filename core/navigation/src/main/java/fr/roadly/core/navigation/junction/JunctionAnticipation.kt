package fr.roadly.core.navigation.junction

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionPhase
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.navigation.RouteGeometry

/**
 * Distances d'anticipation (docs/JUNCTION-3D.md §4). Chaque étape a une distance de base (exemple du cahier des charges :
 * 800 / 500 / 250 / 100 m) et une durée minimale : à grande vitesse, c'est la durée qui l'emporte (à 130 km/h l'affichage commence
 * vers 720 m). La base est réduite en ville et dans les ronds-points, et allongée avec la complexité du carrefour.
 */
data class JunctionTiming(
    val prepareM: Double = 800.0,
    val revealM: Double = 500.0,
    val reinforceM: Double = 250.0,
    val preciseM: Double = 100.0,
    val maneuverM: Double = 20.0,
    val prepareS: Double = 30.0,
    val revealS: Double = 20.0,
    val reinforceS: Double = 11.0,
    val preciseS: Double = 5.0,
    val maneuverS: Double = 1.0,
    /** Retour à la navigation normale : au moins [recoverM] et [recoverS] après le point de manœuvre. */
    val recoverM: Double = 150.0,
    val recoverS: Double = 4.0,
    /** Facteur de base hors voie rapide (tournants, ronds-points). */
    val urbanScale: Double = 0.6,
    val roundaboutScale: Double = 0.7,
    /** +12 % de distance par point de complexité. */
    val complexityStep: Double = 0.12,
    /** Plafond (m) quelle que soit la vitesse. */
    val maxM: Double = 1_600.0,
    /** Retour en arrière d'étape seulement si la distance remonte de plus que ceci (saut GPS, demi-tour). */
    val backtrackM: Double = 80.0,
    /** Au-delà de cette précision (m), la position est jugée imprécise : pas d'étape PRECISE, vue « dégradée ». */
    val imprecisionM: Float = 25f,
    /** Au-delà de cet écart au tracé (m), la vue est retirée. */
    val offRouteM: Double = 45.0,
) {
    fun scaleFor(kind: JunctionKind, highway: Boolean): Double = when {
        kind == JunctionKind.ROUNDABOUT -> roundaboutScale
        kind == JunctionKind.COMPLEX_TURN && !highway -> urbanScale
        else -> 1.0
    }

    /** Distance (m) à laquelle commence l'étape [phase], à [speedKmh], pour ce contexte. */
    fun distanceFor(phase: JunctionPhase, speedKmh: Double?, kind: JunctionKind, highway: Boolean, complexity: Int): Double {
        val (base, seconds) = when (phase) {
            JunctionPhase.PREPARE -> prepareM to prepareS
            JunctionPhase.REVEAL -> revealM to revealS
            JunctionPhase.REINFORCE -> reinforceM to reinforceS
            JunctionPhase.PRECISE -> preciseM to preciseS
            JunctionPhase.MANEUVER -> maneuverM to maneuverS
            JunctionPhase.RECOVER -> return 0.0
        }
        val mps = (speedKmh ?: 0.0).coerceAtLeast(0.0) / 3.6
        val d = maxOf(base * scaleFor(kind, highway), mps * seconds) * (1.0 + complexityStep * complexity)
        return d.coerceAtMost(maxM)
    }
}

/** Entrée de l'anticipation à chaque position. */
data class JunctionInput(
    val route: Route,
    val progress: RouteProgress,
    val speedKmh: Double?,
    val nowMs: Long,
    val gpsLost: Boolean,
    val rerouting: Boolean,
    val offRoute: Boolean,
    /** Topologie déjà chargée pour l'indice de manœuvre donné, sinon null. */
    val topology: (Int) -> JunctionTopology?,
)

/**
 * Machine à états de la vue 3D de carrefour (Kotlin pur). Une seule manœuvre est suivie à la fois ; les étapes n'avancent que
 * dans l'ordre (hystérésis [JunctionTiming.backtrackM]) ; la vue est retirée en recalcul, hors itinéraire et en perte de GPS ;
 * après la manœuvre, l'étape RECOVER ramène progressivement à la navigation normale, sauf si une manœuvre rapprochée prend le relais.
 */
class JunctionAnticipation(var timing: JunctionTiming = JunctionTiming()) {
    private var geometry: RouteGeometry? = null
    private var geometryRouteId: String? = null
    private var tracked: Tracked? = null
    private var recover: Recover? = null

    private data class Tracked(val routeId: String, val index: Int, val phase: JunctionPhase, val phaseDistanceM: Double)
    private data class Recover(val last: JunctionGuidance, val passedAlongM: Double, val passedAtMs: Long)

    fun reset() {
        tracked = null
        recover = null
        lastShown = null
    }

    /** Indice de manœuvre que la vue concerne pour une prochaine manœuvre [next] (dans un rond-point : son entrée). */
    fun junctionIndexFor(route: Route, next: Int): Int {
        val ms = route.maneuvers
        return if (ms.getOrNull(next)?.type == ManeuverType.ROUNDABOUT_EXIT && ms.getOrNull(next - 1)?.type == ManeuverType.ROUNDABOUT_ENTER) next - 1 else next
    }

    /** Abscisse (m) du début de la manœuvre [index]. */
    fun alongOf(route: Route, index: Int): Double {
        val g = geometryFor(route)
        val shape = route.maneuvers[index].beginShapeIndex.coerceIn(0, g.cumulative.lastIndex.coerceAtLeast(0))
        return if (g.cumulative.isEmpty()) 0.0 else g.cumulative[shape]
    }

    fun geometryFor(route: Route): RouteGeometry {
        val g = geometry
        if (g != null && geometryRouteId == route.id) return g
        return RouteGeometry(route.geometry).also { geometry = it; geometryRouteId = route.id }
    }

    /** Dernier indice de manœuvre couvert par la vue de [index] (la sortie d'un rond-point). */
    private fun lastIndexOf(route: Route, index: Int): Int =
        if (route.maneuvers[index].type == ManeuverType.ROUNDABOUT_ENTER && route.maneuvers.getOrNull(index + 1)?.type == ManeuverType.ROUNDABOUT_EXIT) index + 1 else index

    fun update(input: JunctionInput): JunctionGuidance? {
        val route = input.route
        val p = input.progress
        if (input.rerouting || input.gpsLost || input.offRoute || p.offRouteM > timing.offRouteM || p.routeId != route.id) {
            reset()
            return null
        }
        if (tracked?.routeId != null && tracked?.routeId != route.id) reset()
        if (recover?.last?.routeId != null && recover?.last?.routeId != route.id) recover = null
        val ms = route.maneuvers
        if (ms.isEmpty()) return null
        val next = p.nextManeuverIndex.coerceIn(0, ms.lastIndex)
        val index = junctionIndexFor(route, next)
        val candidate = evaluate(input, index, next)
        val r = recover
        if (r != null) {
            val travelled = p.distanceAlongM - r.passedAlongM
            val mps = (input.speedKmh ?: 0.0) / 3.6
            val doneM = maxOf(timing.recoverM, mps * timing.recoverS)
            val doneByTime = input.nowMs - r.passedAtMs > (timing.recoverS * 1000 * 2).toLong()
            // Manœuvre rapprochée déjà visible : elle prend la place de la vue qui se retire (sorties successives).
            if (candidate != null && candidate.phase.visible) {
                recover = null
                return candidate.copy(chainedNext = true)
            }
            if (travelled >= doneM || (doneByTime && travelled >= timing.recoverM * 0.5) || travelled < -timing.backtrackM) {
                recover = null
            } else {
                val fade = (1.0 - travelled.coerceAtLeast(0.0) / doneM).toFloat().coerceIn(0f, 1f)
                return r.last.copy(
                    phase = JunctionPhase.RECOVER, distanceM = -travelled, vehicleAlongM = p.distanceAlongM, reveal = fade,
                    speedKmh = input.speedKmh, degraded = isDegraded(p),
                )
            }
        }
        return candidate
    }

    private fun isDegraded(p: RouteProgress) = p.extrapolated || p.accuracyM > timing.imprecisionM

    /** Dernière vue calculée pour la manœuvre suivie (point de départ de RECOVER). */
    private var lastShown: JunctionGuidance? = null

    private fun evaluate(input: JunctionInput, index: Int, next: Int): JunctionGuidance? {
        val route = input.route
        val p = input.progress
        var t = tracked
        // Manœuvre suivie dépassée : passage en RECOVER, une seule fois, depuis la dernière vue montrée.
        if (t != null && t.index != index) {
            val shown = lastShown
            if (shown != null && shown.maneuverIndex == t.index && shown.phase.visible && index > t.index) {
                recover = Recover(shown, shown.maneuverAlongM.coerceAtMost(p.distanceAlongM), input.nowMs)
            }
            tracked = null
            lastShown = null
            t = null
        }
        val topology = input.topology(index)
        val lastIdx = lastIndexOf(route, index)
        val gapToNext = route.maneuvers.getOrNull(lastIdx + 1)?.let { alongOf(route, lastIdx + 1) - alongOf(route, lastIdx) }
        val cls = JunctionClassifier.classify(route, index, topology, gapToNext)
        if (cls == null) { tracked = null; lastShown = null; return null }
        val highway = route.maneuvers[index].type != ManeuverType.ROUNDABOUT_ENTER && cls.kind != JunctionKind.COMPLEX_TURN
        // Dans un rond-point (entrée passée), la distance est celle de la sortie et l'étape reste MANEUVER jusqu'à la sortie.
        val inside = index != next
        val d = p.distanceToNextManeuverM
        fun at(ph: JunctionPhase) = timing.distanceFor(ph, input.speedKmh, cls.kind, highway, cls.complexity)
        var phase: JunctionPhase? = when {
            inside -> JunctionPhase.MANEUVER
            d <= at(JunctionPhase.MANEUVER) -> JunctionPhase.MANEUVER
            d <= at(JunctionPhase.PRECISE) -> JunctionPhase.PRECISE
            d <= at(JunctionPhase.REINFORCE) -> JunctionPhase.REINFORCE
            d <= at(JunctionPhase.REVEAL) -> JunctionPhase.REVEAL
            d <= at(JunctionPhase.PREPARE) -> JunctionPhase.PREPARE
            else -> null
        }
        val degraded = isDegraded(p)
        // Position imprécise : pas de précision voie par voie (PRECISE n'est pas atteinte), sauf si on y est déjà.
        if (degraded && phase == JunctionPhase.PRECISE && (t == null || t.phase <= JunctionPhase.REINFORCE)) phase = JunctionPhase.REINFORCE
        // Hystérésis : on ne recule d'étape que si la distance remonte nettement au-dessus du seuil de l'étape courante.
        if (t != null && (phase == null || phase < t.phase) && d <= t.phaseDistanceM + timing.backtrackM) phase = t.phase
        if (phase == null) { tracked = null; lastShown = null; return null }
        if (t == null || t.phase != phase) tracked = Tracked(route.id, index, phase, if (inside) 0.0 else at(phase))
        val revealStart = at(JunctionPhase.REVEAL)
        val revealEnd = at(JunctionPhase.REINFORCE)
        val reveal = when (phase) {
            JunctionPhase.PREPARE -> 0f
            JunctionPhase.REVEAL -> ((revealStart - d) / (revealStart - revealEnd).coerceAtLeast(1.0)).toFloat().coerceIn(0.05f, 1f)
            else -> 1f
        }
        val guidance = JunctionGuidance(
            routeId = route.id,
            maneuverIndex = index,
            kind = cls.kind,
            phase = phase,
            distanceM = d,
            maneuverAlongM = p.distanceAlongM + d,
            vehicleAlongM = p.distanceAlongM,
            maneuverIndexInShape = route.maneuvers[next].beginShapeIndex,
            reveal = reveal,
            complexity = cls.complexity,
            sign = JunctionClassifier.sign(route, index),
            lanes = route.maneuvers[index].lanes,
            roundabout = if (cls.kind == JunctionKind.ROUNDABOUT) RoundaboutExitCounter.count(route, index, topology) else null,
            topology = topology,
            degraded = degraded,
            chainedNext = false,
            speedKmh = input.speedKmh,
        )
        lastShown = guidance
        return guidance
    }
}
