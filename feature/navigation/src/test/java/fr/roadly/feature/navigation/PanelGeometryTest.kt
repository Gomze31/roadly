package fr.roadly.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D5 : part de l'écran couverte par le bloc du bas ou par la pile du haut, arrondie au-dessus par pas de 2 %. */
class PanelGeometryTest {
    @Test fun `the fraction is measured from the bottom and rounded up to 2 percent`() {
        assertEquals(0.30f, PanelGeometry.fraction(700f, 1000f)!!, 1e-4f)
        assertEquals("0,281 → 0,30 : la voiture reste au-dessus du panneau", 0.30f, PanelGeometry.fraction(719f, 1000f)!!, 1e-4f)
        assertEquals(0.28f, PanelGeometry.fraction(720f, 1000f)!!, 1e-4f)
    }

    @Test fun `the fraction is capped and an unknown height gives nothing`() {
        assertEquals(PanelGeometry.MAX_FRACTION, PanelGeometry.fraction(100f, 1000f)!!, 1e-4f)
        assertEquals(0f, PanelGeometry.fraction(1000f, 1000f)!!, 1e-4f)
        assertNull(PanelGeometry.fraction(500f, 0f))
    }

    @Test fun `an empty panel gives a positive zero and a NaN height gives nothing`() {
        assertEquals("+0, pas −0", 0f.toRawBits(), PanelGeometry.fraction(1000f, 1000f)!!.toRawBits())
        assertEquals("panneau sous l'écran : +0", 0f.toRawBits(), PanelGeometry.fraction(1200f, 1000f)!!.toRawBits())
        assertNull(PanelGeometry.fraction(500f, Float.NaN))
        assertNull(PanelGeometry.fraction(Float.NaN, 1000f))
    }

    @Test fun `the top stack fraction is measured from the top with the same rounding`() {
        // HONOR, approche de la sortie 31b : pile jusqu'à 277 dp sur 802 dp (0,345) → 0,36.
        assertEquals(0.36f, PanelGeometry.topFraction(277f, 802f)!!, 1e-4f)
        assertEquals(0.30f, PanelGeometry.topFraction(300f, 1000f)!!, 1e-4f)
        assertEquals(0.32f, PanelGeometry.topFraction(301f, 1000f)!!, 1e-4f)
        assertEquals(PanelGeometry.MAX_FRACTION, PanelGeometry.topFraction(900f, 1000f)!!, 1e-4f)
        assertEquals("pile vide : +0", 0f.toRawBits(), PanelGeometry.topFraction(0f, 1000f)!!.toRawBits())
        assertNull(PanelGeometry.topFraction(Float.NaN, 1000f))
        assertNull(PanelGeometry.topFraction(300f, 0f))
    }
}
