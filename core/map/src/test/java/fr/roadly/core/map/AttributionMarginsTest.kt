package fr.roadly.core.map

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** D5 : marges MapLibre (gauche, haut, droite, bas) selon le coin choisi pour l'icône d'attribution. */
class AttributionMarginsTest {
    @Test fun `bottom start - side on the left, vertical at the bottom`() {
        assertArrayEquals(intArrayOf(10, 0, 0, 40), AttributionMargins.of(top = false, end = false, verticalPx = 40, sidePx = 10))
    }

    @Test fun `top end - side on the right, vertical at the top`() {
        assertArrayEquals(intArrayOf(0, 120, 30, 0), AttributionMargins.of(top = true, end = true, verticalPx = 120, sidePx = 30))
    }
}
