package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NAV-4 : précision médiocre durable (30 à 100 m, canyon urbain). Après [GuidanceConstants.DEGRADED_MIN_FIXES] fixes consécutifs
 * cohérents, le suivi décide de nouveau « hors itinéraire » et « arrivée », avec des marges élargies.
 */
class RouteTrackerDegradedAccuracyTest {
    private val route = TestRoutes.straight()
    private val n = GuidanceConstants.DEGRADED_MIN_FIXES

    /** Fix à l'abscisse [along], décalé de [northM] m au nord du tracé (plein est), une seconde après le précédent. */
    private var clockMs = 0L
    private fun fix(t: RouteTracker, along: Double, northM: Double = 0.0, accuracyM: Float = 45f, speedMps: Float? = 8f, bearing: Float? = 90f): LocationFix {
        clockMs += 1_000L
        return LocationFix(TestRoutes.north(t.pointAt(along), northM), bearing, speedMps, accuracyM, clockMs)
    }

    /** [count] fixes médiocres sur le tracé, à 8 m/s, à partir de [from]. Rend l'abscisse suivante. */
    private fun onRoute(t: RouteTracker, from: Double, count: Int, accuracyM: Float = 45f): Double {
        var a = from
        repeat(count) { t.update(fix(t, a, accuracyM = accuracyM)); a += 8.0 }
        return a
    }

    @Test fun `moderate fixes decide nothing until the tenth consistent one, as before`() {
        val t = RouteTracker(route)
        // Le véhicule s'écarte du tracé dès le premier fix médiocre, 40 m par seconde à 30 m/s (sans saut) : 200 m d'écart au 5e fix.
        for (i in 1 until n) {
            t.update(fix(t, 100.0, northM = 40.0 * i, speedMps = 30f, bearing = 0f))
            assertFalse("fix médiocre n°$i, à ${40 * i} m du tracé", t.isOffRoute)
        }
        t.update(fix(t, 100.0, northM = 40.0 * n, speedMps = 30f, bearing = 0f))
        assertTrue("${n}e fix cohérent : la décision est prise", t.isOffRoute)
    }

    @Test fun `sustained moderate accuracy - off route is declared once the gap exceeds 100 m plus the accuracy`() {
        val t = RouteTracker(route)
        var a = onRoute(t, 100.0, n)
        var north = 0.0
        var declaredAt: Double? = null
        // Le véhicule quitte le tracé à angle droit, 8 m par seconde, cap nord.
        repeat(30) {
            north += 8.0
            t.update(fix(t, a, northM = north, bearing = 0f))
            if (declaredAt == null && t.isOffRoute) declaredAt = north
        }
        assertTrue("hors itinéraire déclaré", declaredAt != null)
        // Règle des 5 fixes (écart − 45 m > 40 m, cap écarté) : au plus tôt au 5e fix au-delà de 85 m ; règle franche : au-delà de 145 m.
        assertTrue("déclaré à $declaredAt m du tracé", declaredAt!! > 85.0 + 4 * 8.0 - 0.5 && declaredAt!! <= 152.0)
    }

    @Test fun `sustained moderate accuracy - 120 m beside the route on the same heading is not proven off route`() {
        val t = RouteTracker(route)
        var a = onRoute(t, 100.0, n)
        // Décalage progressif (pas de saut) jusqu'à 120 m, puis route parallèle : 120 − 45 = 75 m, sous le seuil franc ; cap du tracé.
        var north = 0.0
        repeat(15) { north += 8.0; t.update(fix(t, a, northM = north)); a += 8.0 }
        repeat(20) { t.update(fix(t, a, northM = 120.0)); a += 8.0 }
        assertFalse(t.isOffRoute)
    }

    @Test fun `a jump breaks the streak - one moderate fix thrown 400 m away decides nothing`() {
        val t = RouteTracker(route)
        val a = onRoute(t, 100.0, n + 5)
        t.update(fix(t, a, northM = 400.0))
        assertFalse("saut de 400 m en une seconde : série rompue", t.isOffRoute)
        // Le même écart, tenu, redevient une série cohérente : la décision tombe au 10e fix.
        repeat(n - 2) { t.update(fix(t, a, northM = 400.0, speedMps = 0f, bearing = null)); assertFalse(t.isOffRoute) }
        t.update(fix(t, a, northM = 400.0, speedMps = 0f, bearing = null))
        assertTrue(t.isOffRoute)
    }

    @Test fun `a precise fix or an unusable fix restarts the streak`() {
        for (breaker in listOf(5f, 150f)) {
            val t = RouteTracker(route)
            var a = onRoute(t, 100.0, n - 1)
            t.update(fix(t, a, accuracyM = breaker)); a += 8.0
            // Écart franc, mais la série ne compte plus que quelques fixes médiocres.
            var north = 0.0
            repeat(n - 1) { north += 30.0; t.update(fix(t, a, northM = north, speedMps = 30f, bearing = 0f)) }
            assertFalse("après un fix de précision $breaker m", t.isOffRoute)
        }
    }

    @Test fun `back on the route - the off-route state is cleared on the measured gap`() {
        val t = RouteTracker(route)
        var a = onRoute(t, 100.0, n)
        var north = 0.0
        repeat(25) { north += 8.0; t.update(fix(t, a, northM = north, bearing = 0f)) }
        assertTrue(t.isOffRoute)
        // Retour progressif : entre 85 m et 40 m rien n'est prouvé (état gardé) ; sous 40 m mesurés, le véhicule est de retour.
        while (north > 48.0) { north -= 8.0; t.update(fix(t, a, northM = north, bearing = 180f)) }
        assertTrue("à $north m : toujours hors itinéraire", t.isOffRoute)
        repeat(2) { north -= 8.0; t.update(fix(t, a, northM = north, bearing = 180f)) }
        assertFalse(t.isOffRoute)
    }

    @Test fun `sustained moderate accuracy - arrival needs three slow fixes in the arrival zone`() {
        val t = RouteTracker(route)
        val end = t.totalGeometryM
        var a = end - 200.0
        // Approche à 8 m/s jusqu'au bout : le véhicule roule encore, l'arrivée n'est pas déclarée.
        while (a < end) { t.update(fix(t, a)); a += 8.0 }
        assertFalse("en roulant : pas d'arrivée", t.hasArrived)
        t.update(fix(t, end - 5.0, speedMps = 0f, bearing = null))
        t.update(fix(t, end - 8.0, northM = 6.0, speedMps = 0f, bearing = null))
        assertFalse("deux fixes à l'arrêt : pas encore", t.hasArrived)
        t.update(fix(t, end - 3.0, northM = -5.0, speedMps = 0f, bearing = null))
        assertTrue(t.hasArrived)
    }

    @Test fun `moderate accuracy without a long consistent streak never arrives, as before`() {
        val t = RouteTracker(route)
        val end = t.totalGeometryM
        FixScript(route).drive(0.0, end - 300.0, speedKmh = 50.0).forEach { t.update(it) }
        clockMs = 1_000_000L
        repeat(n - 1) { t.update(fix(t, end - 5.0, speedMps = 0f, bearing = null)) }
        assertFalse(t.hasArrived)
    }

    @Test fun `a slow fix outside the arrival zone restarts the arrival count`() {
        val t = RouteTracker(route)
        val end = t.totalGeometryM
        var a = end - 200.0
        while (a < end - 40.0) { t.update(fix(t, a)); a += 8.0 }
        fun stopped(along: Double): GeoPoint = t.pointAt(along)
        clockMs += 1_000L; t.update(LocationFix(stopped(end - 5.0), null, 0f, 45f, clockMs))
        clockMs += 1_000L; t.update(LocationFix(stopped(end - 5.0), null, 0f, 45f, clockMs))
        clockMs += 1_000L; t.update(LocationFix(stopped(end - 40.0), null, 0f, 45f, clockMs))
        clockMs += 1_000L; t.update(LocationFix(stopped(end - 5.0), null, 0f, 45f, clockMs))
        clockMs += 1_000L; t.update(LocationFix(stopped(end - 5.0), null, 0f, 45f, clockMs))
        assertFalse(t.hasArrived)
        clockMs += 1_000L; t.update(LocationFix(stopped(end - 5.0), null, 0f, 45f, clockMs))
        assertTrue(t.hasArrived)
    }
}
