package fr.roadly.core.map

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** D5 : marges MapLibre (gauche, haut, droite, bas) selon le coin choisi pour l'icône d'attribution. */
class AttributionMarginsTest {
    @Test fun `bottom start - side on the left, vertical at the bottom`() {
        assertArrayEquals(intArrayOf(10, 0, 0, 40), AttributionMargins.of(top = false, end = false, verticalPx = 40, sidePx = 10))
    }

    @Test fun `top end - side on the right, vertical at the top`() {
        assertArrayEquals(intArrayOf(0, 120, 30, 0), AttributionMargins.of(top = true, end = true, verticalPx = 120, sidePx = 30))
    }

    @Test fun `guidance landscape - top right, left of the collapse button, clear of the action column`() {
        // Barre d'état 40 dp, barre de navigation à droite 48 dp, marge 12 dp, bouton « replier » 44 dp, écart 8 dp.
        val p = AttributionPlacement.guidanceLandscape(statusTop = 40.dp, navEnd = 48.dp, edge = 12.dp, toggle = 44.dp, gap = 8.dp)
        // Centrée sur la hauteur du bouton : 40 + 12 + (44 − 24) / 2 = 62 dp ; à sa gauche : 48 + 12 + 44 + 8 = 112 dp.
        assertEquals(AttributionPlacement(top = true, end = true, vertical = 62.dp, side = 112.dp), p)
        // Densité 3,5 : 217 px en haut, 392 px à droite.
        assertArrayEquals(intArrayOf(0, 217, 392, 0), AttributionMargins.of(p.top, p.end, verticalPx = 217, sidePx = 392))
    }

    @Test fun `guidance portrait - top right, under the collapse button`() {
        val p = AttributionPlacement.guidancePortrait(statusTop = 40.dp, edge = 12.dp, toggle = 44.dp, gap = 8.dp, inset = 12.dp)
        assertEquals(AttributionPlacement(top = true, end = true, vertical = 104.dp, side = 24.dp), p)
    }
}
