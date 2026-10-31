package fr.roadly.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D5 : part de l'écran couverte par le bloc du bas, arrondie au-dessus par pas de 2 %. */
class BottomPanelGeometryTest {
    @Test fun `the fraction is measured from the bottom and rounded up to 2 percent`() {
        assertEquals(0.30f, BottomPanelGeometry.fraction(700f, 1000f)!!, 1e-4f)
        assertEquals("0,281 → 0,30 : la voiture reste au-dessus du panneau", 0.30f, BottomPanelGeometry.fraction(719f, 1000f)!!, 1e-4f)
        assertEquals(0.28f, BottomPanelGeometry.fraction(720f, 1000f)!!, 1e-4f)
    }

    @Test fun `the fraction is capped and an unknown height gives nothing`() {
        assertEquals(BottomPanelGeometry.MAX_FRACTION, BottomPanelGeometry.fraction(100f, 1000f)!!, 1e-4f)
        assertEquals(0f, BottomPanelGeometry.fraction(1000f, 1000f)!!, 1e-4f)
        assertNull(BottomPanelGeometry.fraction(500f, 0f))
    }
}
