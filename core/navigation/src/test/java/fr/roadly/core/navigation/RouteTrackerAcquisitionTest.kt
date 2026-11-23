package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Première position sur un tracé (départ ou recalcul). Défaut reproduit : un tracé qui repasse près de son début dans le sens du
 * véhicule (demi-tour, boucle) accrochait la toute première position sur le passage de retour — saut de plusieurs centaines de
 * mètres, manœuvre annoncée aussitôt, voire arrivée déclarée au départ.
 */
class RouteTrackerAcquisitionTest {
    private val route = TestRoutes.outAndBack()

    private fun fix(p: GeoPoint, bearing: Float?, speedMps: Float = 12f, accuracy: Float = 5f) = LocationFix(p, bearing, speedMps, accuracy, 0L)

    @Test fun `u-turn route - a vehicle still heading the other way is acquired at the start, not on the return leg`() {
        val t = RouteTracker(route)
        // Le véhicule roule vers l'ouest au point de départ ; le tracé part vers l'est puis revient vers l'ouest 10 m plus au nord.
        val p = t.update(fix(route.geometry.first(), bearing = 270f))
        assertTrue("accroché à ${p.distanceAlongM} m du début", p.distanceAlongM < 30.0)
        assertEquals(1, p.nextManeuverIndex)
        assertFalse("pas d'arrivée au départ", t.hasArrived)
    }

    @Test fun `loop ending at its origin - the first fix is the start, never the arrival`() {
        val t = RouteTracker(route)
        // Position entre l'aller et le retour, sans cap exploitable (à l'arrêt) : les deux passages sont à 5 m.
        val between = TestRoutes.north(route.geometry.first(), TestRoutes.OUT_AND_BACK_LANE_M / 2)
        val p = t.update(fix(between, bearing = null, speedMps = 0f))
        assertTrue("accroché à ${p.distanceAlongM} m du début", p.distanceAlongM < 30.0)
        assertFalse(t.hasArrived)
    }

    @Test fun `the whole u-turn route is then tracked normally and arrival comes only at the end`() {
        val t = RouteTracker(route)
        val alongs = FixScript(route).drive(0.0, t.totalGeometryM, speedKmh = 40.0).map { t.update(it).distanceAlongM }
        alongs.zipWithNext().forEach { (a, b) -> assertTrue("recul ou saut : $a → $b", b >= a - 1.0 && b - a < 40.0) }
        assertTrue(t.hasArrived)
    }

    @Test fun `resuming far along the route is still acquired where the vehicle is`() {
        val t = RouteTracker(route)
        // Reprise sur le retour, 400 m après le demi-tour, cap à l'ouest : loin du début du tracé (aller à 10 m au sud mais à 800 m du départ).
        val along = t.maneuverStartM[1] + 400.0
        val p = t.update(FixScript(route).drive(along, along, speedKmh = 40.0).single())
        assertEquals(along, p.distanceAlongM, 5.0)
        val real = RouteTracker(MotorwayRoute.route())
        val far = real.totalGeometryM * 0.6
        assertEquals(far, real.update(FixScript(MotorwayRoute.route()).drive(far, far, speedKmh = 90.0).single()).distanceAlongM, 5.0)
    }

    @Test fun `after acquisition a single fix on the return leg does not pull the position there`() {
        val t = RouteTracker(route)
        t.update(fix(route.geometry.first(), bearing = 90f))
        val start = t.update(FixScript(route).drive(40.0, 40.0, speedKmh = 40.0).single()).distanceAlongM
        // Un fix qui tombe sur la chaussée de retour (10 m au nord, cap inversé par le bruit) : pas de saut de 1 100 m.
        val stray = t.update(fix(TestRoutes.north(t.pointAt(60.0), TestRoutes.OUT_AND_BACK_LANE_M), bearing = 270f)).distanceAlongM
        assertTrue("de $start à $stray", stray - start < 100.0)
    }
}
