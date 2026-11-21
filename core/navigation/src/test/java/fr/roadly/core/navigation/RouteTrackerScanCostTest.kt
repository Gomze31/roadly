package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Coût de la recherche sur tout le tracé, faite à chaque position tant que le véhicule est à plus de 40 m du tracé (route parallèle
 * suivie dans le bon sens : pas de recalcul, la recherche dure). Mesure écrite dans `build/tracker-scan-cost.txt`.
 * Mesuré le 2026-11-22 sur 50 000 points (500 km) : 1 431 µs et 4,5 Mo alloués par position avant correction (une liste de 50 000 candidats) ; voir le fichier pour l'après.
 */
class RouteTrackerScanCostTest {
    /** 500 km plein est, un point tous les 10 m : l'ordre de grandeur d'un long trajet autoroutier. */
    private fun longRoute(points: Int = 50_000): Route {
        val pts = (0 until points).map { TestRoutes.xy(it * 10.0, 0.0) }
        val total = (points - 1) * 10.0
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, total, total / 30.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, points - 1, null, verbalPre = "Vous êtes arrivé."),
        )
        return Route("long", RouteCosting.FASTEST, total, total / 30.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /** Position à [eastM] le long du tracé, décalée de [northM] (voie parallèle), cap à l'est, 25 m/s. */
    private fun fix(eastM: Double, northM: Double, t: Long) = LocationFix(TestRoutes.xy(eastM, northM), 90f, 25f, 5f, t)

    @Test fun `parallel road 60 m aside on a 500 km route - cost of one position, and the tracking stays exact`() {
        val tracker = RouteTracker(longRoute())
        // Sur le tracé d'abord, puis 60 m au nord : au-delà de 40 m, chaque position déclenche la recherche globale.
        repeat(5) { tracker.update(fix(1_000.0 + it * 25.0, 0.0, it * 1_000L)) }
        // API de la JVM absente du SDK Android à la compilation : appelée par réflexion (le test tourne sur la JVM de l'hôte).
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val allocated = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val me = Thread.currentThread().id
        fun allocatedBytes() = allocated.invoke(bean, me) as Long
        repeat(20) { tracker.update(fix(1_125.0 + it * 25.0, 60.0, 5_000L + it * 1_000L)) } // échauffement
        val n = 100
        val bytesBefore = allocatedBytes()
        val t0 = System.nanoTime()
        var last = tracker.lastProgress!!
        repeat(n) { last = tracker.update(fix(1_625.0 + it * 25.0, 60.0, 25_000L + it * 1_000L)) }
        val microsPerFix = (System.nanoTime() - t0) / 1_000.0 / n
        val kbPerFix = (allocatedBytes() - bytesBefore) / 1024.0 / n
        File("build").mkdirs()
        File("build/tracker-scan-cost.txt").writeText(
            String.format(java.util.Locale.ROOT, "50 000 points, voie parallèle à 60 m : %.0f µs et %.0f Ko alloués par position%n", microsPerFix, kbPerFix),
        )
        // Le suivi reste exact : abscisse du véhicule, écart de 60 m, pas de hors-itinéraire tant que le cap suit le tracé.
        assertEquals(1_625.0 + (n - 1) * 25.0, last.distanceAlongM, 1.0)
        assertEquals(60.0, last.offRouteM, 1.0)
        assertFalse(tracker.isOffRoute)
        // Garde-fou : la recherche ne doit plus allouer un objet par segment du tracé (4,5 Mo par position avant correction).
        assertTrue("alloué par position : $kbPerFix Ko", kbPerFix < 512.0)
    }
}
