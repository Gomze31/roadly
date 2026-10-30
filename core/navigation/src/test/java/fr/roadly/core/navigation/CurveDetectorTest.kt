package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.HazardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveDetectorTest {
    /** Point à [east] m à l'est et [north] m au nord de l'origine (48,85 N). */
    private fun p(east: Double, north: Double) = GeoPoint(48.85 + north / 111_320.0, 2.35 + east / (111_320.0 * 0.658))

    @Test fun `a straight line has no curve`() {
        val pts = (0..10).map { p(it * 50.0, 0.0) }
        assertTrue(CurveDetector.detect(pts).isEmpty())
    }

    @Test fun `a 90 degree bend within 60 m is a curve with its abscissa`() {
        // 200 m vers l'est, puis virage progressif vers le nord sur 40 m, puis 200 m vers le nord.
        val pts = listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0), p(220.0, 5.0), p(235.0, 20.0), p(240.0, 40.0), p(240.0, 140.0), p(240.0, 240.0))
        val curves = CurveDetector.detect(pts)
        assertEquals(1, curves.size)
        val c = curves.single()
        assertEquals(HazardKind.CURVE, c.kind)
        assertTrue("angle=${c.angleDeg}", c.angleDeg!! >= 80.0)
        assertTrue("start=${c.startAlongM}", c.startAlongM in 150.0..230.0)
        assertTrue("end=${c.endAlongM}", c.endAlongM in 230.0..300.0)
    }

    @Test fun `a bend at a maneuver point is not reported twice`() {
        val pts = listOf(p(0.0, 0.0), p(100.0, 0.0), p(200.0, 0.0), p(220.0, 5.0), p(235.0, 20.0), p(240.0, 40.0), p(240.0, 140.0), p(240.0, 240.0))
        // Manœuvre au point d'indice 3 (à ~221 m) : le virage en est tout proche → exclu.
        assertTrue(CurveDetector.detect(pts, maneuverShapeIndexes = listOf(3)).isEmpty())
    }

    @Test fun `a slow wide bend over hundreds of metres is not a curve`() {
        // 90° répartis sur 900 m (10° tous les 100 m).
        var x = 0.0; var y = 0.0
        val pts = mutableListOf(p(0.0, 0.0))
        for (i in 1..9) {
            val a = Math.toRadians(10.0 * i)
            x += 100 * Math.cos(a); y += 100 * Math.sin(a)
            pts += p(x, y)
        }
        assertTrue(CurveDetector.detect(pts).isEmpty())
    }
}
