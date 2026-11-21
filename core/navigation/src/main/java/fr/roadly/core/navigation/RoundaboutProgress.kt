package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.junction.JunctionRoundabout
import fr.roadly.core.model.normalizeSignedDeg

/**
 * Mode rond-point (docs/GUIDAGE-INTELLIGENT.md §3). Le schéma ne compte plus à la place du conducteur un simple numéro : il place
 * chaque sortie connue à son angle réel sur l'anneau, suit le véhicule et dit combien de sorties restent à laisser passer.
 *
 * Sources, rien d'inventé :
 * - le tracé de l'anneau parcouru ([RoundaboutInfo.ringGeometry], `/route`) donne les angles et les longueurs ;
 * - les sorties intermédiaires viennent du comptage topologique ([JunctionRoundabout], `trace_attributes`), seulement s'il donne
 *   le même rang que Valhalla. Sinon seule la sortie à prendre est dessinée et aucun décompte n'est annoncé.
 * LIMITE : les sorties situées après celle à prendre ne sont pas connues (hors du tracé) ; circulation à droite seulement.
 */
data class RoundaboutBranch(
    /** Rang de la sortie (1 = première rencontrée). */
    val rank: Int,
    /** Distance (m) le long de l'anneau depuis le point d'entrée. */
    val offsetM: Double,
    /** Angle du schéma (°) depuis l'entrée, dans le sens de circulation ; même convention que [RoundaboutGeometry.diagramSweepDeg]. */
    val angleDeg: Double,
    val isTarget: Boolean,
)

data class RoundaboutLayout(
    /** Longueur (m) de l'anneau parcouru, de l'entrée à la sortie. */
    val ringLengthM: Double,
    /** Arc du schéma jusqu'à la sortie à prendre ; null = aucune donnée d'angle (anneau et entrée seuls). */
    val sweepDeg: Double?,
    /** Sorties connues, par rang croissant ; la dernière est la sortie à prendre quand elle est connue. */
    val branches: List<RoundaboutBranch>,
    val targetRank: Int?,
    /** Les sorties intermédiaires sont connues et cohérentes avec Valhalla : le décompte est fiable. */
    val counted: Boolean,
    val exitStreet: String?,
    private val profileOffsetsM: List<Double>,
    private val profileAnglesDeg: List<Double>,
) {
    /** Nombre de sorties à laisser passer avant celle à prendre (0 pour une première sortie). */
    val intermediateCount: Int get() = branches.count { !it.isTarget }

    /** Angle du schéma (°) à [offsetM] mètres de l'entrée, interpolé sur le tracé réel ; null sans donnée d'angle. */
    fun angleAt(offsetM: Double): Double? {
        if (profileAnglesDeg.isEmpty()) return null
        val o = offsetM.coerceIn(0.0, ringLengthM)
        val i = profileOffsetsM.indexOfFirst { it >= o }
        if (i <= 0) return profileAnglesDeg.first().takeIf { i == 0 } ?: profileAnglesDeg.last()
        val o0 = profileOffsetsM[i - 1]
        val o1 = profileOffsetsM[i]
        val t = if (o1 > o0) (o - o0) / (o1 - o0) else 1.0
        return profileAnglesDeg[i - 1] + t * (profileAnglesDeg[i] - profileAnglesDeg[i - 1])
    }
}

object RoundaboutLayouts {
    /** Écart angulaire minimal (°) entre deux sorties dessinées : deux branches voisines restent distinctes. */
    const val MIN_BRANCH_GAP_DEG = 22.0
    /** Une sortie n'est jamais dessinée à moins de ceci (°) de la branche d'entrée. */
    const val MIN_FIRST_BRANCH_DEG = 18.0

    fun build(info: RoundaboutInfo, counted: JunctionRoundabout?): RoundaboutLayout {
        val pts = info.ringGeometry
        val offsets = DoubleArray(pts.size)
        for (i in 1 until pts.size) offsets[i] = offsets[i - 1] + pts[i - 1].distanceTo(pts[i])
        val length = offsets.lastOrNull() ?: 0.0
        val diagram = RoundaboutGeometry.diagramSweepDeg(info)
        val raw = rawSweeps(pts)
        val angles: List<Double> = if (diagram == null) emptyList() else pts.indices.map { i ->
            val a = when {
                raw != null -> raw[i] / raw.last() * diagram
                length > 0.0 -> offsets[i] / length * diagram
                else -> diagram
            }
            a.coerceIn(0.0, diagram)
        }
        val reliable = counted != null && counted.consistent && counted.exits.isNotEmpty() && counted.exits.last().isTarget && diagram != null
        val branches = if (reliable) {
            val placed = counted!!.exits.map { e ->
                val k = (e.nodeIndex - counted.entryIndex).coerceIn(0, pts.lastIndex.coerceAtLeast(0))
                RoundaboutBranch(e.rank, if (e.isTarget) length else offsets.getOrElse(k) { length }, if (e.isTarget) diagram!! else angles.getOrElse(k) { diagram!! }, e.isTarget)
            }
            spread(placed)
        } else if (diagram != null && info.exitBearingDeg != null) {
            listOf(RoundaboutBranch(info.exitNumber ?: 1, length, diagram, isTarget = true))
        } else emptyList()
        return RoundaboutLayout(
            ringLengthM = length, sweepDeg = diagram, branches = branches,
            targetRank = counted?.targetExit?.takeIf { reliable } ?: info.exitNumber,
            counted = reliable, exitStreet = info.exitStreet ?: counted?.exitStreet,
            profileOffsetsM = offsets.toList(), profileAnglesDeg = angles,
        )
    }

    /** Sorties écartées d'au moins [MIN_BRANCH_GAP_DEG] en reculant depuis la sortie à prendre, qui garde son angle réel. */
    private fun spread(branches: List<RoundaboutBranch>): List<RoundaboutBranch> {
        if (branches.size < 2) return branches
        val out = branches.toMutableList()
        for (i in out.lastIndex - 1 downTo 0) {
            val max = out[i + 1].angleDeg - MIN_BRANCH_GAP_DEG
            val floor = MIN_FIRST_BRANCH_DEG + i * 4.0
            out[i] = out[i].copy(angleDeg = minOf(out[i].angleDeg, max).coerceAtLeast(floor))
        }
        return out
    }

    /** Arc cumulé (°, > 0 = sens antihoraire) à chaque point de l'anneau ; null si le tracé ne tourne pas dans le sens attendu. */
    private fun rawSweeps(ring: List<GeoPoint>): DoubleArray? {
        if (ring.size < 3) return null
        val out = DoubleArray(ring.size)
        var previous: Double? = null
        var acc = 0.0
        for (i in 1 until ring.size) {
            if (ring[i - 1].distanceTo(ring[i]) >= RoundaboutGeometry.MIN_SEGMENT_M) {
                val h = bearing(ring[i - 1], ring[i])
                previous?.let { acc -= normalizeSignedDeg(h - it) }
                previous = h
            }
            out[i] = acc.coerceAtLeast(0.0)
        }
        // Monotone : un point bruité ne fait jamais reculer le véhicule sur le schéma.
        for (i in 1 until out.size) out[i] = maxOf(out[i], out[i - 1])
        return out.takeIf { it.last() >= RoundaboutGeometry.MIN_RING_TURN_DEG }
    }
}

enum class RoundaboutPhase {
    /** Avant l'entrée : le schéma annonce la sortie et les sorties à laisser passer. */
    APPROACH,
    /** Dans l'anneau : le véhicule avance sur le schéma, les sorties passées s'éteignent. */
    INSIDE,
    /** Sortie prise : confirmation brève avant le retour à la navigation normale. */
    EXITED,
}

/** Confiance dans la position sur l'anneau. */
enum class RoundaboutConfidence {
    HIGH,
    /** Position extrapolée, trop imprécise pour distinguer deux sorties voisines, ou saut non confirmé : le décompte est gelé. */
    LOW,
}

/** Qualité de la position qui alimente le suivi. Les valeurs par défaut décrivent une bonne position (tests, aperçus). */
data class RoundaboutFix(
    val accuracyM: Float = 5f,
    val extrapolated: Boolean = false,
    val speedKmh: Double? = null,
    /** Horloge de la position ; null = pas de contrôle de vraisemblance du déplacement. */
    val nowMs: Long? = null,
)

/** État du mode rond-point à une position. */
data class RoundaboutLive(
    val layout: RoundaboutLayout,
    val phase: RoundaboutPhase,
    /** APPROACH : distance avant l'entrée ; INSIDE : distance avant la sortie ; EXITED : distance parcourue depuis la sortie. */
    val distanceM: Double,
    /** Sorties déjà laissées derrière soi (jamais décroissant dans un même rond-point). */
    val exitsPassed: Int,
    /** Sorties encore à laisser passer avant la sienne ; null = décompte non fiable (rien n'est affiché ni dit). */
    val exitsRemaining: Int?,
    /** La prochaine sortie rencontrée est celle à prendre (décompte fiable uniquement). */
    val exitIsNext: Boolean,
    /** 0..1 : part de l'anneau déjà parcourue. */
    val ringFraction: Float,
    /** Angle du véhicule sur le schéma (°) ; null sans donnée d'angle. */
    val vehicleAngleDeg: Double?,
    /** LOW : le décompte affiché est le dernier établi avec certitude, il n'avance plus tant que la position reste douteuse. */
    val confidence: RoundaboutConfidence = RoundaboutConfidence.HIGH,
)

/**
 * Suivi du véhicule dans un rond-point (Kotlin pur, un par course). Règles de fiabilité, dans l'ordre :
 * 1. **Jamais en arrière.** La position sur l'anneau et le nombre de sorties passées ne reculent pas : un fix imprécis ne rallume
 *    pas une sortie et ne fait pas remonter le décompte.
 * 2. **Une sortie n'est passée que nettement dépassée.** Marge après le nœud de la sortie, qui grandit avec l'imprécision du fix
 *    et reste bornée par l'écart jusqu'à la sortie suivante (sinon la bonne sortie serait annoncée trop tard).
 * 3. **Position douteuse = décompte gelé.** Position extrapolée, ou précision moins bonne que l'écart entre deux sorties voisines :
 *    rien n'avance, la confiance passe à LOW. Le décompte rattrape d'un coup quand la position redevient sûre.
 * 4. **Un saut doit être confirmé.** Une avancée plus grande que ce que la vitesse permet (saut GPS, accrochage sur une branche
 *    voisine) est ignorée tant que la position suivante ne la confirme pas.
 */
class RoundaboutProgressTracker {
    private var routeId: String? = null
    private var enterIndex = -1
    private var maxOffsetM = 0.0
    private var maxPassed = 0
    private var lastInsideAtMs: Long? = null
    private var pendingJumpM: Double? = null
    private var confidence = RoundaboutConfidence.HIGH

    fun reset() {
        routeId = null; enterIndex = -1; maxOffsetM = 0.0; maxPassed = 0
        lastInsideAtMs = null; pendingJumpM = null; confidence = RoundaboutConfidence.HIGH
    }

    /**
     * [inside] = l'entrée est passée, [distanceToNextM] est alors la distance jusqu'à la sortie ; sinon la distance jusqu'à l'entrée.
     */
    fun update(
        routeId: String, enterIndex: Int, layout: RoundaboutLayout, inside: Boolean, distanceToNextM: Double, fix: RoundaboutFix = RoundaboutFix(),
    ): RoundaboutLive {
        if (this.routeId != routeId || this.enterIndex != enterIndex) {
            reset()
            this.routeId = routeId
            this.enterIndex = enterIndex
        }
        val sure = !fix.extrapolated && fix.accuracyM <= maxAccuracyM(layout)
        confidence = if (sure) RoundaboutConfidence.HIGH else RoundaboutConfidence.LOW
        if (inside) {
            val raw = (layout.ringLengthM - distanceToNextM).coerceIn(0.0, layout.ringLengthM)
            val accepted = sure && plausible(raw, fix)
            if (!accepted) confidence = RoundaboutConfidence.LOW
            if (accepted) {
                maxOffsetM = maxOf(maxOffsetM, raw)
                maxPassed = maxOf(maxPassed, passedAt(layout, maxOffsetM, fix.accuracyM))
            }
            fix.nowMs?.let { lastInsideAtMs = it }
        }
        return live(layout, if (inside) RoundaboutPhase.INSIDE else RoundaboutPhase.APPROACH, distanceToNextM.coerceAtLeast(0.0))
    }

    /** Après la sortie : même schéma, anneau entièrement parcouru ; [travelledM] = distance depuis la sortie. */
    fun exited(layout: RoundaboutLayout, travelledM: Double): RoundaboutLive {
        maxOffsetM = layout.ringLengthM
        maxPassed = layout.intermediateCount
        confidence = RoundaboutConfidence.HIGH
        pendingJumpM = null
        return live(layout, RoundaboutPhase.EXITED, travelledM.coerceAtLeast(0.0))
    }

    /** Règle 4 : l'avancée depuis la dernière position retenue est-elle possible à cette vitesse ? Sinon elle attend sa confirmation. */
    private fun plausible(rawM: Double, fix: RoundaboutFix): Boolean {
        val now = fix.nowMs
        val last = lastInsideAtMs
        if (now == null || last == null) { pendingJumpM = null; return true }
        val dtS = ((now - last) / 1000.0).coerceIn(1.0, MAX_DT_S)
        val mps = (fix.speedKmh ?: DEFAULT_SPEED_KMH).coerceAtLeast(MIN_SPEED_KMH) / 3.6
        val reach = mps * dtS * JUMP_SPEED_FACTOR + fix.accuracyM + JUMP_SLACK_M
        if (rawM - maxOffsetM <= reach) { pendingJumpM = null; return true }
        val pending = pendingJumpM
        // Deuxième position de suite au même endroit ou au-delà : ce n'était pas un saut, on s'était arrêté de recevoir des fixes.
        if (pending != null && rawM >= pending - fix.accuracyM) { pendingJumpM = null; return true }
        pendingJumpM = rawM
        return false
    }

    private fun live(layout: RoundaboutLayout, phase: RoundaboutPhase, distanceM: Double): RoundaboutLive {
        val remaining = if (layout.counted) (layout.intermediateCount - maxPassed).coerceAtLeast(0) else null
        return RoundaboutLive(
            layout = layout, phase = phase, distanceM = distanceM, exitsPassed = maxPassed, exitsRemaining = remaining,
            exitIsNext = remaining == 0,
            ringFraction = if (layout.ringLengthM > 0.0) (maxOffsetM / layout.ringLengthM).toFloat().coerceIn(0f, 1f) else 0f,
            vehicleAngleDeg = layout.angleAt(maxOffsetM),
            confidence = confidence,
        )
    }

    companion object {
        const val MIN_PASS_MARGIN_M = 2.0
        const val MAX_PASS_MARGIN_M = 8.0
        /** Part de l'imprécision du fix ajoutée à la marge de passage. */
        const val PASS_ACCURACY_FACTOR = 0.3
        /** La marge ne dépasse jamais cette part de l'écart jusqu'à la sortie suivante. */
        const val PASS_GAP_SHARE = 0.4
        /** Bornes de la précision exigée (m) ; entre les deux, l'écart minimal entre deux sorties voisines. */
        const val MIN_REQUIRED_ACCURACY_M = 12.0
        const val MAX_REQUIRED_ACCURACY_M = 25.0
        const val JUMP_SPEED_FACTOR = 2.5
        const val JUMP_SLACK_M = 8.0
        const val DEFAULT_SPEED_KMH = 30.0
        const val MIN_SPEED_KMH = 10.0
        const val MAX_DT_S = 5.0

        /** Règle 3 : précision au-delà de laquelle deux sorties voisines ne se distinguent plus. */
        fun maxAccuracyM(layout: RoundaboutLayout): Double {
            val offsets = listOf(0.0) + layout.branches.map { it.offsetM }
            val spacing = offsets.zipWithNext { a, b -> b - a }.filter { it > 0.0 }.minOrNull() ?: MAX_REQUIRED_ACCURACY_M
            return spacing.coerceIn(MIN_REQUIRED_ACCURACY_M, MAX_REQUIRED_ACCURACY_M)
        }

        /** Règle 2 : marge (m) après le nœud de la sortie de rang [index] (dans [RoundaboutLayout.branches]). */
        fun passMarginM(layout: RoundaboutLayout, index: Int, accuracyM: Float): Double {
            val gap = (layout.branches.getOrNull(index + 1)?.offsetM ?: layout.ringLengthM) - layout.branches[index].offsetM
            val cap = (gap * PASS_GAP_SHARE).coerceIn(MIN_PASS_MARGIN_M, MAX_PASS_MARGIN_M)
            return (accuracyM * PASS_ACCURACY_FACTOR).coerceIn(MIN_PASS_MARGIN_M, cap)
        }

        /** Sorties à laisser passer franchies à [offsetM] ; le compte s'arrête à la première non franchie (pas de trou). */
        fun passedAt(layout: RoundaboutLayout, offsetM: Double, accuracyM: Float): Int {
            var n = 0
            for ((i, b) in layout.branches.withIndex()) {
                if (b.isTarget || offsetM < b.offsetM + passMarginM(layout, i, accuracyM)) break
                n++
            }
            return n
        }
    }
}

