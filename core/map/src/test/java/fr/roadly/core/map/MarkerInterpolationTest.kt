package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerInterpolationTest {
    private val a = MarkerPose(48.0, 2.0, 350.0)
    private val b = MarkerPose(48.001, 2.001, 10.0)   // ~134 m plus loin

    @Test fun `halfway is the midpoint and the arrow turns the short way`() {
        val m = MarkerInterpolation.lerp(a, b, 0.5)
        assertEquals(48.0005, m.lat, 1e-12)
        assertEquals(2.0005, m.lon, 1e-12)
        assertEquals(0.0, m.bearingDeg, 1e-9)
    }

    @Test fun `the fraction is clamped to the two fixes`() {
        assertEquals(a, MarkerInterpolation.lerp(a, b, -1.0))
        val end = MarkerInterpolation.lerp(a, b, 2.0)
        assertEquals(b.lat, end.lat, 1e-12); assertEquals(b.lon, end.lon, 1e-12); assertEquals(b.bearingDeg, end.bearingDeg, 1e-9)
    }

    @Test fun `a stationary vehicle is not animated - same pose, GPS jitter under half a metre`() {
        // À l'arrêt, chaque position (1 Hz) relançait 1 s d'animation vers la même pose : la source du marqueur était réécrite à chaque image.
        assertFalse(MarkerInterpolation.shouldAnimate(a, a, 1_000))
        assertFalse(MarkerInterpolation.shouldAnimate(a, a.copy(lat = a.lat + 0.000003), 1_000))  // ~0,3 m
        // Un vrai déplacement (1 m) ou une rotation sur place (demi-tour à l'arrêt, boussole) restent animés.
        assertTrue(MarkerInterpolation.shouldAnimate(a, a.copy(lat = a.lat + 0.00001), 1_000))  // ~1,1 m
        assertTrue(MarkerInterpolation.shouldAnimate(a, a.copy(bearingDeg = (a.bearingDeg + 15.0) % 360.0), 1_000))
    }

    @Test fun `standstill indoors - GPS jitter within the reported accuracy leaves the marker where it is`() {
        // Arrêté, précision 10 m, positions dispersées de quelques mètres chaque seconde (constat sur l'appareil : rendu continu à 58 i/s).
        val rnd = java.util.Random(7)
        var animated = 0
        repeat(180) {
            val to = a.copy(lat = a.lat + rnd.nextGaussian() * 2.5 / 111_320.0, lon = a.lon + rnd.nextGaussian() * 2.5 / 73_000.0)
            if (!MarkerInterpolation.isStandstillJitter(a, to, speedMps = 0f, accuracyM = 10f) && MarkerInterpolation.shouldAnimate(a, to, 1_000)) animated++
        }
        assertEquals("positions animées sur 3 minutes à l'arrêt", 0, animated)
        // Sans la règle, presque chaque position relançait une seconde d'animation.
        val rnd2 = java.util.Random(7)
        val before = (0 until 180).count {
            MarkerInterpolation.shouldAnimate(a, a.copy(lat = a.lat + rnd2.nextGaussian() * 2.5 / 111_320.0, lon = a.lon + rnd2.nextGaussian() * 2.5 / 73_000.0), 1_000)
        }
        assertTrue("avant : $before / 180", before > 150)
    }

    @Test fun `standstill rule never holds back a moving vehicle or a real displacement`() {
        val tenMetres = a.copy(lat = a.lat + 10.5 / 111_320.0)
        // En mouvement (2 m/s), même un petit déplacement est suivi.
        assertFalse(MarkerInterpolation.isStandstillJitter(a, a.copy(lat = a.lat + 0.00002), speedMps = 2f, accuracyM = 10f))
        // Vitesse inconnue : pas de règle.
        assertFalse(MarkerInterpolation.isStandstillJitter(a, a.copy(lat = a.lat + 0.00002), speedMps = null, accuracyM = 10f))
        // Arrêté mais la position s'est écartée de plus que la précision : le marqueur suit (déplacement lent, ou première position fausse).
        assertFalse(MarkerInterpolation.isStandstillJitter(a, tenMetres, speedMps = 0f, accuracyM = 10f))
        // Précision excellente : le plancher de 2 m absorbe la dérive résiduelle.
        assertTrue(MarkerInterpolation.isStandstillJitter(a, a.copy(lat = a.lat + 1.5 / 111_320.0), speedMps = 0f, accuracyM = 1f))
    }

    @Test fun `a jump of more than 200 m snaps instead of gliding across the map`() {
        assertTrue(MarkerInterpolation.shouldAnimate(a, b, 1_000))
        assertFalse(MarkerInterpolation.shouldAnimate(a, MarkerPose(48.01, 2.0, 0.0), 1_000))  // ~1,1 km
        assertFalse(MarkerInterpolation.shouldAnimate(a, b, 0))
    }

    @Test fun `a fix without heading points north, headings are normalised`() {
        assertEquals(0.0, MarkerInterpolation.pose(LocationFix(GeoPoint(48.0, 2.0), null, null, 5f, 0L)).bearingDeg, 1e-9)
        assertEquals(270.0, MarkerInterpolation.pose(LocationFix(GeoPoint(48.0, 2.0), -90f, null, 5f, 0L)).bearingDeg, 1e-9)
    }
}
