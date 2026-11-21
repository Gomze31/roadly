package fr.roadly.core.navigation

import fr.roadly.core.model.Route
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.model.normalizeSignedDeg
import kotlin.math.abs

/**
 * Schéma de bifurcation (docs/GUIDAGE-INTELLIGENT.md §9.2) : à une sortie, une fourche ou une intersection chargée, montrer la
 * branche à prendre **et celles à ne pas prendre**, dans l'ordre réel de gauche à droite.
 *
 * Source : les branches sortantes du nœud de la manœuvre dans la topologie Valhalla (`trace_attributes`, `intersecting_edges`).
 * Rien n'est dessiné sans elle, ni quand le nœud n'a aucune autre branche carrossable (il n'y a alors rien à distinguer).
 *
 * Ce qui est exact : le nombre de branches, leur côté et leur ordre. Ce qui est schématique : l'ouverture des angles. Une bretelle
 * d'autoroute s'écarte de quelques degrés seulement ; dessinée telle quelle elle serait illisible, donc deux branches voisines sont
 * écartées d'au moins [MIN_GAP_DEG] à l'écran — comme sur un panneau de présignalisation.
 */
data class DiagramBranch(
    /** Angle réel par rapport au cap d'arrivée (°, 0 = tout droit, > 0 = à droite). */
    val angleDeg: Double,
    /** Angle dessiné (°), même signe et même ordre que [angleDeg]. */
    val drawnAngleDeg: Double,
    val isRoute: Boolean,
    /** Bretelle (plus fine à l'écran qu'une chaussée principale). */
    val isRamp: Boolean,
)

data class BranchDiagram(val maneuverIndex: Int, val branches: List<DiagramBranch>) {
    val routeBranch: DiagramBranch get() = branches.first { it.isRoute }
    /** Rang de la branche à prendre parmi les branches, de gauche à droite (0 = la plus à gauche). */
    val routeRank: Int get() = branches.indexOfFirst { it.isRoute }
}

object BranchDiagrams {
    const val MIN_GAP_DEG = 26.0
    /** Au-delà, une branche part vers l'arrière : elle n'aide pas à choisir et n'est pas dessinée. */
    const val MAX_ANGLE_DEG = 150.0
    /** Deux caps à moins de ceci sont la même branche (celle de l'itinéraire, déjà comptée). */
    private const val SAME_BRANCH_DEG = 4.0
    private const val MAX_BRANCHES = 5

    fun build(route: Route, maneuverIndex: Int, topology: JunctionTopology?): BranchDiagram? {
        val m = route.maneuvers.getOrNull(maneuverIndex) ?: return null
        topology ?: return null
        val arrival = m.bearingBeforeDeg ?: return null
        val before = topology.edges.lastOrNull { it.endIndex <= m.beginShapeIndex && it.endIndex >= m.beginShapeIndex - 1 }
            ?: topology.edges.lastOrNull { it.endIndex <= m.beginShapeIndex } ?: return null
        val after = topology.edgeAt(m.beginShapeIndex)
        val routeHeading = after?.beginHeadingDeg ?: m.bearingAfterDeg ?: return null
        val routeAngle = normalizeSignedDeg((routeHeading - arrival).toDouble())
        val others = before.branches.asSequence()
            .filter { it.outbound }
            .map { normalizeSignedDeg((it.headingDeg - arrival).toDouble()) to it.isRamp }
            .filter { (a, _) -> abs(a) <= MAX_ANGLE_DEG && abs(normalizeSignedDeg(a - routeAngle)) > SAME_BRANCH_DEG }
            .toList()
        if (others.isEmpty()) return null
        val all = (others.map { (a, ramp) -> DiagramBranch(a, a, isRoute = false, isRamp = ramp) } +
            DiagramBranch(routeAngle, routeAngle, isRoute = true, isRamp = after?.isRamp == true))
            .sortedBy { it.angleDeg }
        val kept = nearestTo(all, MAX_BRANCHES)
        return BranchDiagram(maneuverIndex, spread(kept))
    }

    /** Garde la branche de l'itinéraire et ses voisines les plus proches en angle. */
    private fun nearestTo(sorted: List<DiagramBranch>, max: Int): List<DiagramBranch> {
        if (sorted.size <= max) return sorted
        val route = sorted.first { it.isRoute }
        return sorted.sortedBy { abs(it.angleDeg - route.angleDeg) }.take(max).sortedBy { it.angleDeg }
    }

    /**
     * Angles dessinés : la branche de l'itinéraire garde son angle réel (borné à ±[MAX_ANGLE_DEG]) ; les autres sont repoussées, de
     * proche en proche, pour laisser [MIN_GAP_DEG] entre deux voisines. L'ordre de gauche à droite ne change jamais.
     */
    fun spread(sorted: List<DiagramBranch>): List<DiagramBranch> {
        val i = sorted.indexOfFirst { it.isRoute }
        val drawn = DoubleArray(sorted.size) { sorted[it].angleDeg }
        // Une sortie à peine écartée de la chaussée est dessinée franchement de son côté.
        val neighbours = listOfNotNull(sorted.getOrNull(i - 1), sorted.getOrNull(i + 1))
        val straightest = neighbours.minByOrNull { abs(it.angleDeg) }
        if (straightest != null && abs(drawn[i]) < MIN_GAP_DEG && abs(straightest.angleDeg) <= abs(drawn[i])) {
            drawn[i] = if (sorted[i].angleDeg >= straightest.angleDeg) MIN_GAP_DEG else -MIN_GAP_DEG
        }
        for (k in i + 1 until sorted.size) drawn[k] = maxOf(drawn[k], drawn[k - 1] + MIN_GAP_DEG)
        for (k in i - 1 downTo 0) drawn[k] = minOf(drawn[k], drawn[k + 1] - MIN_GAP_DEG)
        return sorted.mapIndexed { k, b -> b.copy(drawnAngleDeg = drawn[k].coerceIn(-170.0, 170.0)) }
    }
}
