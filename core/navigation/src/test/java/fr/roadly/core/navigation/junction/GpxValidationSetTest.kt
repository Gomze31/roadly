package fr.roadly.core.navigation.junction

import fr.roadly.core.location.GpxTrack
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.RouteSimulation
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FixScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jeu de traces GPX rejouables sur le téléphone (docs/VALIDATION-TELEPHONE.md §8), écrit dans `core/navigation/build/gpx-validation/`.
 *
 * Ce sont des traces **synthétiques** : la géométrie est celle d'itinéraires Valhalla réels enregistrés (Toulouse, Paris), mais
 * les positions, les vitesses et le bruit sont calculés. Aucune n'est un enregistrement GPS. Chaque fichier le dit dans ses
 * métadonnées et dans son nom (`synth-…`). Une vraie trace enregistrée en roulant reste à fournir.
 */
class GpxValidationSetTest {
    private val dir = File("build/gpx-validation").apply { mkdirs() }
    private val note = "TRACE SYNTHÉTIQUE, pas un enregistrement GPS. Géométrie : itinéraire Valhalla réel enregistré. Positions, vitesses et bruit calculés."

    private fun save(name: String, what: String, fixes: List<LocationFix>): List<LocationFix> {
        val gpx = GpxTrack.write(fixes, name, "$note $what")
        File(dir, "$name.gpx").writeText(gpx)
        val back = GpxTrack.parse(gpx)
        assertEquals("$name : relecture complète", fixes.size, back.size)
        assertTrue("$name : positions relues à moins de 5 cm", fixes.zip(back).all { (a, b) -> a.point.distanceTo(b.point) < 0.05 })
        assertTrue("$name : marquée synthétique", gpx.contains("TRACE SYNTHÉTIQUE"))
        return back
    }

    private fun retime(fixes: List<LocationFix>) = fixes.mapIndexed { i, f -> f.copy(timestampMs = i * 1_000L) }

    private fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        val rad = bearingDeg * PI / 180.0
        return GeoPoint(p.lat + meters * cos(rad) / 111_320.0, p.lon + meters * sin(rad) / (111_320.0 * cos(p.lat * PI / 180.0)))
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat); val dl = Math.toRadians(b.lon - a.lon)
        return (Math.toDegrees(atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl))) + 360.0) % 360.0
    }

    @Test fun `toulouse - whole route at Valhalla speeds`() {
        val route = RealJunctions.route("motorway")
        val back = save("synth-toulouse-trajet-complet", "Trajet complet Toulouse : ronds-points, périphérique, sorties 31b et 19, bifurcation.", RouteSimulation.fixes(route))
        assertTrue(back.last().point.distanceTo(route.geometry.last()) < 1.0)
    }

    @Test fun `toulouse - exit 19 missed, the vehicle stays on the motorway`() {
        val route = RealJunctions.route("motorway")
        val t = RouteTracker(route)
        val exit = route.maneuvers.indexOfLast { it.type == ManeuverType.EXIT_RIGHT && it.exitNumber != null }
        val exitAt = t.maneuverStartM[exit]
        val before = FixScript(route).drive(exitAt - 2_500.0, exitAt, speedKmh = 110.0)
        val straight = bearing(t.pointAt(exitAt - 60.0), t.pointAt(exitAt))
        val after = (1..40).map { LocationFix(offset(t.pointAt(exitAt), straight, it * 110.0 / 3.6), straight.toFloat(), (110.0 / 3.6).toFloat(), 5f, 0L) }
        save("synth-toulouse-sortie-19-manquee", "Sortie 19 manquée à 110 km/h : après le point de sortie, le véhicule continue tout droit dans l'axe de la chaussée (ligne droite calculée, pas la vraie autoroute).", retime(before + after))
    }

    @Test fun `etoile - sixth exit with a noisy GPS`() {
        val route = RealJunctions.route("etoile")
        val t = RouteTracker(route)
        val enter = RealJunctions.enterIndices(route).first()
        val fixes = FixScript(route, seed = 7).drive((t.maneuverStartM[enter] - 400.0).coerceAtLeast(0.0), t.maneuverStartM[enter + 1] + 200.0, speedKmh = 26.0, noiseM = 4.0, accuracyM = 9f)
        save("synth-etoile-6e-sortie-gps-bruite", "Place Charles-de-Gaulle, 6e sortie, bruit gaussien de 4 m, précision annoncée 9 m.", retime(fixes))
    }

    @Test fun `muret - two roundabouts in a row`() {
        val route = RealJunctions.route("muret")
        save("synth-muret-deux-ronds-points", "Muret : deux ronds-points successifs (2e puis 3e sortie).", RouteSimulation.fixes(route))
    }

    @Test fun `toulouse - GPS lost for 20 s before exit 31b`() {
        val route = RealJunctions.route("motorway")
        val t = RouteTracker(route)
        val exit = route.maneuvers.indexOfFirst { it.type == ManeuverType.EXIT_RIGHT && it.exitNumber != null }
        val exitAt = t.maneuverStartM[exit]
        val fixes = FixScript(route).drive(exitAt - 1_500.0, exitAt + 400.0, speedKmh = 90.0)
        // Trou de 20 s : les positions entre 1 000 m et 500 m avant la sortie sont retirées, les horodatages gardés.
        val kept = fixes.filterIndexed { i, _ -> i !in 20..39 }
        val back = save("synth-toulouse-perte-gps-20s", "Sortie 31b à 90 km/h avec 20 s sans position entre 1 000 m et 500 m avant la sortie.", kept)
        assertTrue("le trou est conservé", back.zipWithNext().any { (a, b) -> b.timestampMs - a.timestampMs >= 20_000L })
    }
}
