package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B6 : routes parallèles, GPS imprécis, pont au-dessus (scénarios 9 et 10). */
class RouteTrackerParallelRoadsTest {
    @Test fun `8 s on a parallel road 25 m away never trigger off route`() {
        val r = TestRoutes.parallelRoads()
        val t = RouteTracker(r)
        FixScript(r).drive(100.0, 100.0 + 7 * 50 / 3.6, speedKmh = 50.0, lateralM = TestRoutes.PARALLEL_ROAD_OFFSET_M).forEach {
            t.update(it); assertFalse(t.isOffRoute)
        }
    }

    @Test fun `a parallel road 45 m away in the same direction is not off route, a diverging heading is`() {
        val r = TestRoutes.parallelRoads()
        val same = RouteTracker(r)
        FixScript(r).drive(100.0, 300.0, speedKmh = 50.0, lateralM = 45.0).forEach { same.update(it) }
        assertFalse("même sens que le tracé : pas de recalcul", same.isOffRoute)
        val away = RouteTracker(r)
        FixScript(r).drive(100.0, 300.0, speedKmh = 50.0, lateralM = 45.0, headingOffsetDeg = -70.0).forEach { away.update(it) }
        assertTrue("cap écarté de 70° sur plus de 3 fixes : hors itinéraire", away.isOffRoute)
    }

    @Test fun `imprecise fixes decide neither off route nor a now announcement`() { // scénario 10
        val r = TestRoutes.straight()
        val turnAt = RouteTracker(r).maneuverStartM[1]
        fun lateral(accuracyM: Float) = FixScript(r).drive(turnAt - 120.0, turnAt - 120.0, speedKmh = 30.0, lateralM = 120.0, accuracyM = accuracyM).single()
        val precise = RouteTracker(r)
        precise.update(LocationFix(precise.pointAt(turnAt - 150.0), null, null, 5f, 0L))
        precise.update(lateral(5f))
        assertTrue("témoin : le même écart de 120 m avec un fix précis est hors itinéraire", precise.isOffRoute)
        val t = RouteTracker(r)
        t.update(LocationFix(t.pointAt(turnAt - 150.0), null, null, 5f, 0L))
        t.update(lateral(60f))
        assertFalse("fix à 120 m mais précision 60 m : filtré", t.isOffRoute)
        // Jitter de 80 à 100 m avant le virage, précision 60 m : indice inchangé, jamais hors itinéraire.
        FixScript(r, seed = 21).jitter(turnAt - 90.0, count = 10, amplitudeM = 10.0, accuracyM = 60f).forEach { f ->
            assertEquals(1, t.update(f).nextManeuverIndex)
            assertFalse(t.isOffRoute)
        }
        // À 30 m du virage, la précision du fix est transmise à la politique : pas de NOW sur un fix imprécis.
        val close = t.update(LocationFix(t.pointAt(turnAt - 30.0), null, null, 60f, 0L))
        assertEquals(60f, close.accuracyM)
        assertNull(AnnouncementPolicy.next(close, r.maneuvers, 30.0, setOf(1 to Tier.NEAR)))
    }

    @Test fun `a bridge over an earlier part of the route keeps the along and the index`() {
        val r = TestRoutes.overpass()
        val t = RouteTracker(r)
        t.update(LocationFix(TestRoutes.xy(100.0, 0.0), null, null, 5f, 0L))
        t.update(LocationFix(TestRoutes.xy(115.0, 0.0), null, null, 5f, 1_000L))
        // 12 m au nord du croisement : 0 m du passage supérieur (abscisse ≈ 563), 12 m du passage en cours (abscisse 125).
        val p = t.update(LocationFix(TestRoutes.xy(125.0, 12.0), null, null, 5f, 2_000L))
        assertEquals(125.0, p.distanceAlongM, 1.0)
        assertEquals(1, p.nextManeuverIndex)
        assertFalse(t.isOffRoute)
    }
}
