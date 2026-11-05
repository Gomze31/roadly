package fr.roadly.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

class SurfacePaddingTest {
    private val w = 1000
    private val h = 500

    /** Centre de la zone que MapLibre garde une fois les marges retirées, en pixels. */
    private fun center(p: CameraPadding): Pair<Double, Double> {
        val left = p.left * w; val right = w - p.right * w
        val top = p.top * h; val bottom = h - p.bottom * h
        return (left + right) / 2.0 to (top + bottom) / 2.0
    }

    @Test fun `full surface centered`() {
        val p = SurfacePadding.forVisibleArea(w, h, SurfaceArea(0, 0, w, h), 0.5f)
        assertEquals(500.0, center(p).first, 0.5)
        assertEquals(250.0, center(p).second, 0.5)
    }

    @Test fun `left card and lower anchor`() {
        // Carte de guidage à gauche (400 px), bandeau haut 60 px : la cible va au centre de la zone libre, à 70 % de sa hauteur.
        val visible = SurfaceArea(400, 60, w, h)
        val (x, y) = center(SurfacePadding.forVisibleArea(w, h, visible, 0.7f))
        assertEquals(700.0, x, 0.5)
        assertEquals(60 + 0.7 * 440, y, 0.5)
    }

    @Test fun `upper anchor`() {
        val visible = SurfaceArea(0, 100, w, 400)
        val (_, y) = center(SurfacePadding.forVisibleArea(w, h, visible, 0.25f))
        assertEquals(100 + 0.25 * 300, y, 0.5)
    }

    @Test fun `degenerate area gives no padding`() {
        assertEquals(CameraPadding.NONE, SurfacePadding.forVisibleArea(w, h, SurfaceArea(300, 0, 300, h), 0.7f))
        assertEquals(CameraPadding.NONE, SurfacePadding.forVisibleArea(0, h, SurfaceArea(0, 0, 0, h), 0.7f))
    }
}
