package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.normalizeSignedDeg
import kotlin.math.abs

/**
 * D2 : géométrie du schéma de rond-point, tirée du tracé réel de l'anneau (Kotlin pur).
 * L'arc parcouru (somme des écarts de cap le long de l'anneau) place la sortie ; le cap de sortie B4 n'est qu'un repli,
 * car les raccords entrée / sortie ne sont pas tangents (3e sortie de la fixture autoroute : cap +140°, arc 282°).
 * LIMITE : circulation à droite seulement (anneau antihoraire) ; les autres sorties ne sont jamais dessinées.
 */
object RoundaboutGeometry {
    /** Segments plus courts ignorés (m) : points doublés du tracé, cap indéfini. */
    const val MIN_SEGMENT_M = 0.5
    /** Courbure minimale (°) pour qu'un tracé soit lu comme un anneau ; en dessous (tracé rectiligne de test, mini-giratoire), repli sur les caps. */
    const val MIN_RING_TURN_DEG = 5.0
    /** Arc dessiné minimal (°) : la branche de sortie ne se confond jamais avec la branche d'entrée. */
    const val MIN_DIAGRAM_SWEEP_DEG = 40.0
    /** Arc dessiné maximal (°) : un demi-tour reste lisible, sortie juste à gauche de l'entrée. */
    const val MAX_DIAGRAM_SWEEP_DEG = 340.0
    /** Au-delà (°), l'angle de sortie B4 est lu comme un demi-tour : le signe de ±180° n'est pas fiable. */
    const val UTURN_DEG = 175.0

    /** Arc parcouru sur l'anneau (°, > 0 = sens antihoraire) = −Σ des écarts de cap signés entre segments successifs ; null si le tracé ne tourne pas. */
    fun sweepFromRing(ring: List<GeoPoint>): Double? {
        val headings = ring.zipWithNext()
            .filter { (a, b) -> a.distanceTo(b) >= MIN_SEGMENT_M }
            .map { (a, b) -> bearing(a, b) }
        if (headings.size < 2) return null
        val turn = headings.zipWithNext().sumOf { (a, b) -> normalizeSignedDeg(b - a) }
        return if (abs(turn) < MIN_RING_TURN_DEG) null else -turn
    }

    /** Arc à dessiner (°, borné) : celui de l'anneau, sinon 180° − angle de sortie B4 ; null = aucune donnée (anneau et entrée seuls). */
    fun diagramSweepDeg(info: RoundaboutInfo): Double? {
        val sweep = sweepFromRing(info.ringGeometry)
            ?: info.exitAngleDeg?.let { a -> 180.0 - (if (a >= UTURN_DEG) -180.0 else a) }
            ?: return null
        return sweep.coerceIn(MIN_DIAGRAM_SWEEP_DEG, MAX_DIAGRAM_SWEEP_DEG)
    }
}
