package fr.roadly.core.navigation.junction

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionRoundabout
import fr.roadly.core.model.junction.JunctionSign
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.model.junction.RoadClass
import fr.roadly.core.model.junction.RoundaboutExit
import fr.roadly.core.model.normalizeSignedDeg
import kotlin.math.abs

/**
 * Comptage des sorties d'un rond-point sur la topologie réelle (`trace_attributes`) : chaque nœud de l'anneau parcouru avant le
 * nœud de sortie compte autant de sorties que de branches sortantes (driveability `forward` ou `both`). Les branches d'entrée
 * seule (`backward`) ne comptent pas. Vérifié sur 4 ronds-points réels de Toulouse (2e, 2e, 1re, 3e sortie) : même rang que Valhalla.
 */
object RoundaboutExitCounter {
    fun count(route: Route, enterIndex: Int, topology: JunctionTopology?): JunctionRoundabout? {
        val ms = route.maneuvers
        val enter = ms.getOrNull(enterIndex)?.takeIf { it.type == ManeuverType.ROUNDABOUT_ENTER } ?: return null
        val exit = ms.getOrNull(enterIndex + 1)?.takeIf { it.type == ManeuverType.ROUNDABOUT_EXIT }
        val target = enter.roundaboutExit?.takeIf { it > 0 }
        val entryIndex = enter.beginShapeIndex
        val exitIndex = exit?.beginShapeIndex ?: enter.endShapeIndex ?: entryIndex
        val ring = topology?.edges.orEmpty().filter { it.roundabout && it.beginIndex >= entryIndex && it.endIndex <= exitIndex }.sortedBy { it.beginIndex }
        // Anneau couvert sans trou de l'entrée au nœud de sortie : sinon on ne compte rien (aucun rang intermédiaire inventé).
        val covered = ring.isNotEmpty() && ring.first().beginIndex == entryIndex && ring.last().endIndex == exitIndex &&
            ring.zipWithNext().all { (a, b) -> a.endIndex == b.beginIndex }
        if (!covered) {
            val only = if (target != null) listOf(RoundaboutExit(target, exit?.bearingAfterDeg ?: 0, exitIndex, isTarget = true)) else emptyList()
            return JunctionRoundabout(target, only, consistent = false, entryIndex, exitIndex, exit?.streetName)
        }
        val exits = ArrayList<RoundaboutExit>()
        var rank = 0
        for (e in ring.dropLast(1)) {
            // Plusieurs sorties au même nœud : de la plus à droite à la plus à gauche (circulation à droite, anneau anti-horaire).
            val heading = e.beginHeadingDeg
            val outs = e.branches.filter { it.outbound }.sortedBy { b -> heading?.let { normalizeSignedDeg((b.headingDeg - it).toDouble()) } ?: 0.0 }.reversed()
            for (b in outs) exits += RoundaboutExit(++rank, b.headingDeg, e.endIndex, isTarget = false)
        }
        val targetRank = rank + 1
        val exitEdge = topology!!.edges.firstOrNull { it.beginIndex == exitIndex && !it.roundabout }
        val exitHeading = exitEdge?.beginHeadingDeg ?: exit?.bearingAfterDeg ?: 0
        exits += RoundaboutExit(targetRank, exitHeading, exitIndex, isTarget = true)
        val consistent = target == null || target == targetRank
        val shown = if (consistent) exits else listOf(RoundaboutExit(target!!, exitHeading, exitIndex, isTarget = true))
        return JunctionRoundabout(target ?: targetRank, shown, consistent, entryIndex, exitIndex, exit?.streetName)
    }
}

/** Résultat du classement d'une manœuvre : type de carrefour et complexité 0..3. */
data class JunctionClass(val kind: JunctionKind, val complexity: Int)

/**
 * Décide si une manœuvre mérite la vue 3D. Les tournants simples en ville ne la déclenchent pas (changements de vue trop fréquents) :
 * seulement sorties, bifurcations, échangeurs, insertions sur voie rapide, ronds-points et intersections à 3 voies ou plus.
 */
object JunctionClassifier {
    /** Deux manœuvres plus proches que ceci sont enchaînées dans la même vue (sorties successives). */
    const val CHAIN_GAP_HIGHWAY_M = 700.0
    const val CHAIN_GAP_URBAN_M = 250.0
    private const val MIN_LANES_COMPLEX_TURN = 3

    private val exitTypes = setOf(ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT)
    private val forkTypes = setOf(ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT)
    private val mergeTypes = setOf(ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT)
    private val turnTypes = setOf(
        ManeuverType.TURN_LEFT, ManeuverType.TURN_RIGHT, ManeuverType.SLIGHT_LEFT, ManeuverType.SLIGHT_RIGHT,
        ManeuverType.SHARP_LEFT, ManeuverType.SHARP_RIGHT,
    )

    /** Signe d'autoroute (« A62 », « A 620 », « E80 ») sur le panneau : la bretelle mène à une autre autoroute. */
    private val motorwayRef = Regex("^\\s*[AE]\\s?\\d+")

    fun classify(route: Route, index: Int, topology: JunctionTopology?, gapToNextM: Double?): JunctionClass? {
        val m = route.maneuvers.getOrNull(index) ?: return null
        val atIndex = topology?.let { t -> t.edges.lastOrNull { it.endIndex <= m.beginShapeIndex } }
        val before = atIndex ?: topology?.edgeAt(m.beginShapeIndex - 1)
        val after = topology?.edgeAt(m.beginShapeIndex)
        val window = topology?.edges.orEmpty()
        val multiLevel = window.any { it.bridge || it.tunnel }
        val highwayBefore = before?.let { it.roadClass.isHighway } ?: (m.type in exitTypes || m.type in forkTypes)
        val laneCount = maxOf(m.lanes.size, before?.laneCount ?: 0)
        val branches = before?.branches?.size ?: 0
        val gap = gapToNextM
        val chainGap = if (highwayBefore) CHAIN_GAP_HIGHWAY_M else CHAIN_GAP_URBAN_M
        val chained = gap != null && gap < chainGap
        val kind = when (m.type) {
            ManeuverType.ROUNDABOUT_ENTER -> JunctionKind.ROUNDABOUT
            in exitTypes -> {
                val toMotorway = m.exitBranch?.let { motorwayRef.containsMatchIn(it) } == true ||
                    window.any { it.beginIndex > m.beginShapeIndex && !it.isRamp && it.roadClass == RoadClass.MOTORWAY && highwayBefore }
                if (toMotorway || (multiLevel && highwayBefore)) JunctionKind.INTERCHANGE else if (highwayBefore) JunctionKind.MOTORWAY_EXIT else
                    if (laneCount >= MIN_LANES_COMPLEX_TURN || after?.isRamp == true) JunctionKind.COMPLEX_TURN else return null
            }
            in forkTypes -> when {
                highwayBefore || (after?.roadClass?.isHighway == true) -> if (multiLevel) JunctionKind.INTERCHANGE else JunctionKind.MOTORWAY_FORK
                laneCount >= MIN_LANES_COMPLEX_TURN -> JunctionKind.COMPLEX_TURN
                else -> return null
            }
            in mergeTypes -> if (after?.roadClass?.isHighway != false) JunctionKind.MERGE else return null
            in turnTypes -> if (laneCount >= MIN_LANES_COMPLEX_TURN || (branches >= 3 && laneCount >= 2)) JunctionKind.COMPLEX_TURN else return null
            else -> return null
        }
        var complexity = 0
        if (branches >= 2 || (kind == JunctionKind.ROUNDABOUT && (m.roundaboutExit ?: 0) >= 4)) complexity++
        if (multiLevel) complexity++
        if (chained || laneCount >= 4) complexity++
        return JunctionClass(kind, complexity.coerceAtMost(3))
    }

    /** Panneau réel de la manœuvre, ou null s'il n'y en a pas. */
    fun sign(route: Route, index: Int): JunctionSign? {
        val m = route.maneuvers.getOrNull(index) ?: return null
        val toward = m.exitToward?.split(" · ")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return JunctionSign(m.exitNumber, toward, m.exitBranch).takeUnless { it.isEmpty }
    }

    /** Écart angulaire entre deux caps (0..180). */
    internal fun headingGap(a: Int, b: Int): Double = abs(normalizeSignedDeg((a - b).toDouble()))
}
