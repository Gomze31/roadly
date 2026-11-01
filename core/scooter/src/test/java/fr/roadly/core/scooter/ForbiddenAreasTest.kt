package fr.roadly.core.scooter

import fr.roadly.core.scooter.Fixtures.edge
import fr.roadly.core.scooter.regulation.FranceEdpmRegulation
import fr.roadly.core.scooter.routing.ForbiddenAreas
import fr.roadly.core.scooter.segment.ScooterSegmentClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForbiddenAreasTest {
    @Test fun excludesForbiddenMiddlePortionOnly() {
        val geometry = Fixtures.line(900.0)
        val segs = ScooterSegmentClassifier.classify(
            listOf(edge(20.0, roadClass = "primary", speed = 70), edge(300.0, use = "cycleway"), edge(200.0, roadClass = "trunk", speed = 90), edge(20.0, use = "steps"), edge(360.0, use = "cycleway")),
            emptyMap(), FranceEdpmRegulation, 900.0,
        )
        val polys = ForbiddenAreas.polygons(geometry, segs)
        // La portion interdite au départ (< 30 m) est gardée ; la route à 90 km/h et l'escalier sont exclus.
        assertEquals(2, polys.size)
        polys.forEach { ring -> assertEquals(ring.first(), ring.last()); assertEquals(5, ring.size) }
        val box = polys.first()
        assertTrue(box.minOf { it.lon } > geometry.first().lon)
    }
}
