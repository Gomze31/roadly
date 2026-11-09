package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteLabelPlacementTest {
    private fun line(vararg pts: Pair<Double, Double>) = pts.map { GeoPoint(it.first, it.second) }

    @Test fun `the label goes where the alternative is farthest from the other routes`() {
        val main = line(43.60 to 1.40, 43.60 to 1.42, 43.60 to 1.44, 43.60 to 1.46)
        // Détour vers le nord au milieu, extrémités communes.
        val alt = line(43.60 to 1.40, 43.61 to 1.42, 43.63 to 1.43, 43.61 to 1.44, 43.60 to 1.46)
        assertEquals(GeoPoint(43.63, 1.43), RouteLabelPlacement.labelPoint(alt, listOf(main)))
    }

    @Test fun `without other routes the middle point is used, and an empty route has no label`() {
        val r = line(43.0 to 1.0, 43.1 to 1.1, 43.2 to 1.2)
        assertEquals(GeoPoint(43.1, 1.1), RouteLabelPlacement.labelPoint(r, emptyList()))
        assertNull(RouteLabelPlacement.labelPoint(emptyList(), listOf(r)))
    }
}
