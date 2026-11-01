package fr.roadly.feature.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * D2 : géométrie du schéma de rond-point aux bornes de l'arc (40° et 340°) et entre les deux.
 * Les aperçus ne peuvent pas être regardés en intégration continue : on vérifie ici que la flèche de sortie ne touche
 * jamais la branche d'entrée, que le trait de la branche reste visible et que la flèche reste dans le schéma.
 */
class RoundaboutDiagramTest {
    private val side = 1000f
    private val size = Size(side, side)

    private fun check(spec: RoundaboutDiagramSpec, minClearance: Float, tileCorner: Float?) {
        val w = side * spec.strokeRatio
        val r = side * spec.ringRatio
        val c = RoundaboutDiagram.center(size)
        val (entryEnd, entryOnRing) = RoundaboutDiagram.entry(size, spec)
        for (sweep in 40..340) {
            val b = RoundaboutDiagram.exitBranch(sweep.toDouble(), size, spec)
            val h = b.head
            // 1. La flèche ne touche pas la bande de la branche d'entrée (axe ± demi-trait).
            val clearance = triangleToSegment(h.tip, h.left, h.right, entryOnRing, entryEnd) - w / 2
            assertTrue("arc $sweep° : écart flèche / entrée ${clearance / side} < ${minClearance / side}", clearance >= minClearance)
            // 2. La base de la flèche est hors du trait de l'anneau : la branche reste visible.
            val visible = (b.base - c).getDistance() - (r + w / 2)
            assertTrue("arc $sweep° : branche visible ${visible / side}", visible >= 0.4f * w)
            // 3. Hors du trait de l'anneau, les deux branches ne se touchent pas.
            val radial = (b.base - c) / (b.base - c).getDistance()
            val exitOut = c + radial * (r + w / 2)
            val entryOut = Offset(entryOnRing.x, entryOnRing.y + w / 2)
            assertTrue("arc $sweep° : branches confondues hors de l'anneau", segmentToSegment(exitOut, b.base, entryOut, entryEnd) >= w)
            // 4. La flèche reste dans le schéma (et dans les coins arrondis du fond compact).
            for (p in listOf(h.tip, h.left, h.right)) {
                assertTrue("arc $sweep° : flèche hors du schéma $p", p.x in 0f..side && p.y in 0f..side)
                if (tileCorner != null) assertTrue("arc $sweep° : flèche hors du fond arrondi $p", insideRoundedSquare(p, tileCorner))
            }
        }
    }

    @Test fun `the compact exit arrow never touches the entry branch`() =
        check(RoundaboutDiagramSpec.Compact, minClearance = 0.02f * side, tileCorner = side * 12f / 48f)

    @Test fun `the large exit arrow never touches the entry branch`() =
        check(RoundaboutDiagramSpec.Large, minClearance = 0.015f * side, tileCorner = null)

    private fun insideRoundedSquare(p: Offset, k: Float): Boolean {
        val cx = p.x.coerceIn(k, side - k)
        val cy = p.y.coerceIn(k, side - k)
        return hypot(p.x - cx, p.y - cy) <= k
    }

    private fun pointToSegment(p: Offset, a: Offset, b: Offset): Float {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
        return (p - (a + ab * t)).getDistance()
    }

    private fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    private fun segmentToSegment(p: Offset, q: Offset, a: Offset, b: Offset): Float {
        val intersect = (cross(a, b, p) > 0) != (cross(a, b, q) > 0) && (cross(p, q, a) > 0) != (cross(p, q, b) > 0)
        if (intersect) return 0f
        return minOf(pointToSegment(p, a, b), pointToSegment(q, a, b), pointToSegment(a, p, q), pointToSegment(b, p, q))
    }

    private fun triangleToSegment(t1: Offset, t2: Offset, t3: Offset, a: Offset, b: Offset): Float {
        val d1 = cross(t1, t2, a); val d2 = cross(t2, t3, a); val d3 = cross(t3, t1, a)
        val inside = (d1 >= 0 && d2 >= 0 && d3 >= 0) || (d1 <= 0 && d2 <= 0 && d3 <= 0)
        if (inside) return 0f
        return minOf(segmentToSegment(t1, t2, a, b), segmentToSegment(t2, t3, a, b), segmentToSegment(t3, t1, a, b))
    }
}
