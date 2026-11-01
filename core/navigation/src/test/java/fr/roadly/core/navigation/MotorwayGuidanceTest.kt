package fr.roadly.core.navigation

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.testing.FixScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scénarios 7 et 8 sur la fixture autoroutière réelle : sortie numérotée visée, échangeur derrière, index stable sous bruit. */
class MotorwayGuidanceTest {
    private val route = MotorwayRoute.route()
    private val exitIndex = route.maneuvers.indexOfFirst { (it.type == ManeuverType.EXIT_RIGHT || it.type == ManeuverType.EXIT_LEFT) && it.exitNumber != null }
    private val sides = setOf(ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT, ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT)

    private val lastExitIndex = route.maneuvers.indexOfLast { (it.type == ManeuverType.EXIT_RIGHT || it.type == ManeuverType.EXIT_LEFT) && it.exitNumber != null }

    /** Pilote jusqu'à [beforeM] mètres avant la sortie [index] et vérifie que la sortie est la cible, l'échangeur la suivante. */
    private fun assertExitIsTargetAt(index: Int, beforeM: Double) {
        assertTrue("la fixture doit contenir une sortie numérotée", index > 0)
        val t = RouteTracker(route)
        val exitAt = t.maneuverStartM[index]
        val p = FixScript(route).drive(exitAt - beforeM - 200.0, exitAt - beforeM, speedKmh = 110.0).map { t.update(it) }.last()
        assertEquals(index, p.nextManeuverIndex)
        assertEquals(beforeM * route.distanceMeters / t.totalGeometryM, p.distanceToNextManeuverM, 0.5)
        assertNotNull(route.maneuvers[index].exitToward)
        assertTrue("échangeur après la sortie : ${route.maneuvers[index + 1].type}", route.maneuvers[index + 1].type in sides)
        assertEquals(t.maneuverStartM[index + 1] - t.maneuverStartM[index], (p.distancesToManeuversM[0] - p.distanceToNextManeuverM) * t.totalGeometryM / route.distanceMeters, 0.5)
    }

    /** Sortie 19 : précédée de 11 km d'autoroute, donc visée à 400 m. */
    @Test fun `at 400 m the last numbered exit is the target and the interchange is the next maneuver`() {
        assertTrue("deux sorties numérotées dans la fixture", lastExitIndex > exitIndex)
        assertExitIsTargetAt(lastExitIndex, 400.0)
    }

    /** Sortie 31b : une bretelle de 230 m la précède (A 621), la cible n'y bascule qu'après son début, donc test à 150 m. */
    @Test fun `at 150 m the first numbered exit is the target and the interchange is the next maneuver`() {
        assertExitIsTargetAt(exitIndex, 150.0)
    }

    @Test fun `GPS noise at the exit at motorway speed never flips the target`() {
        val t = RouteTracker(route)
        val exitAt = t.maneuverStartM[exitIndex]
        val indices = FixScript(route, seed = 23).drive(exitAt - 300.0, exitAt + 5.0, speedKmh = 110.0, noiseM = 4.0, accuracyM = 8f).map { t.update(it).nextManeuverIndex }
        assertTrue(indices.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(exitIndex, indices.last())
    }
}
