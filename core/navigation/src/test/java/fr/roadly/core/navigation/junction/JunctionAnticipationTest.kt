package fr.roadly.core.navigation.junction

import fr.roadly.core.model.Route
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionPhase
import fr.roadly.core.model.junction.JunctionTopology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anticipation de la vue 3D sur des itinéraires réels (Toulouse, Muret) : étapes, vitesse, GPS, recalcul, sorties enchaînées. */
class JunctionAnticipationTest {
    private val motorway = RealJunctions.route("motorway")
    private val motorwayTopology = RealJunctions.topology("motorway", motorway, 0)

    private class Drive(val route: Route, val topology: JunctionTopology?, val a: JunctionAnticipation = JunctionAnticipation()) {
        var now = 0L
        fun at(along: Double, kmh: Double, accuracy: Float = 5f, extrapolated: Boolean = false, gpsLost: Boolean = false, rerouting: Boolean = false, offRouteM: Double = 0.0): JunctionGuidance? {
            now += 1_000
            return a.update(
                JunctionInput(
                    route, RealJunctions.progressAt(route, along, accuracy, extrapolated, offRouteM), kmh, now, gpsLost, rerouting,
                    offRoute = offRouteM > 50, topology = { topology },
                ),
            )
        }

        /** Roule de [from] à [to] à [kmh] (une position par seconde) et renvoie les vues successives. */
        fun drive(from: Double, to: Double, kmh: Double): List<Pair<Double, JunctionGuidance?>> {
            val out = ArrayList<Pair<Double, JunctionGuidance?>>()
            var s = from
            while (s <= to) { out += s to at(s, kmh); s += kmh / 3.6 }
            return out
        }
    }

    @Test fun `motorway exit 19 at 110 km h - phases in order, reveal grows, then the close fork takes over`() {
        val d = Drive(motorway, motorwayTopology)
        val exit = RealJunctions.alongOf(motorway, 11)
        val views = d.drive(exit - 2_000, exit + 200, 110.0)
        val forExit = views.mapNotNull { it.second }.filter { it.maneuverIndex == 11 }
        val phases = forExit.map { it.phase }.distinct()
        assertEquals(listOf(JunctionPhase.PREPARE, JunctionPhase.REVEAL, JunctionPhase.REINFORCE, JunctionPhase.PRECISE, JunctionPhase.MANEUVER), phases)
        // Rien à plus de 1,6 km ; la préparation commence au-delà de 800 m à cette vitesse (30 s × 30,6 m/s).
        val firstAt = views.first { it.second != null }.first
        assertTrue(exit - firstAt > 800.0)
        assertTrue(exit - firstAt <= JunctionTiming().maxM + 31)
        val reveals = forExit.filter { it.phase == JunctionPhase.REVEAL }.map { it.reveal }
        assertEquals(reveals.sorted(), reveals)
        // La bifurcation de Labège (605 m après) est enchaînée sans repasser par la navigation normale.
        val after = views.filter { it.first > exit + 5 }.mapNotNull { it.second }
        assertTrue(after.isNotEmpty())
        assertTrue(after.first().chainedNext || after.first().maneuverIndex == 11)
        assertTrue(after.any { it.maneuverIndex == 12 && it.phase.visible })
    }

    @Test fun `distances adapt to speed, road type and complexity`() {
        val t = JunctionTiming()
        val highway50 = t.distanceFor(JunctionPhase.REVEAL, 50.0, JunctionKind.MOTORWAY_EXIT, highway = true, complexity = 0)
        val highway130 = t.distanceFor(JunctionPhase.REVEAL, 130.0, JunctionKind.MOTORWAY_EXIT, highway = true, complexity = 0)
        val roundabout50 = t.distanceFor(JunctionPhase.REVEAL, 50.0, JunctionKind.ROUNDABOUT, highway = false, complexity = 0)
        val complex130 = t.distanceFor(JunctionPhase.REVEAL, 130.0, JunctionKind.INTERCHANGE, highway = true, complexity = 2)
        assertEquals(500.0, highway50, 0.1)
        assertTrue(highway130 > 700.0)
        assertTrue(roundabout50 < highway50)
        assertTrue(complex130 > highway130)
        assertTrue(t.distanceFor(JunctionPhase.PREPARE, 250.0, JunctionKind.INTERCHANGE, true, 3) <= t.maxM)
    }

    @Test fun `roundabout 3rd exit in Muret - exit number from topology, camera phase inside the ring`() {
        val route = RealJunctions.route("muret")
        val enter = RealJunctions.enterIndices(route).first { route.maneuvers[it].roundaboutExit == 3 }
        val d = Drive(route, RealJunctions.topology("muret", route, 0))
        val at = RealJunctions.alongOf(route, enter)
        val views = d.drive((at - 600).coerceAtLeast(0.0), RealJunctions.alongOf(route, enter + 1) + 20, 40.0).mapNotNull { it.second }
        val rb = views.first { it.maneuverIndex == enter && it.phase.visible }
        assertEquals(JunctionKind.ROUNDABOUT, rb.kind)
        assertEquals(3, rb.roundabout!!.targetExit)
        assertTrue(rb.roundabout!!.consistent)
        // Dans l'anneau (prochaine manœuvre = sortie), la vue reste celle du rond-point, en étape MANEUVER.
        val inside = views.filter { it.maneuverIndex == enter && it.phase == JunctionPhase.MANEUVER }
        assertTrue(inside.isNotEmpty())
    }

    @Test fun `close roundabouts in Toulouse are chained`() {
        val d = Drive(motorway, motorwayTopology)
        val exit2 = RealJunctions.alongOf(motorway, 5)
        val views = d.drive(exit2 - 200, RealJunctions.alongOf(motorway, 6) + 5, 35.0)
        val firstNext = views.mapNotNull { it.second }.first { it.maneuverIndex == 6 }
        assertTrue(firstNext.chainedNext)
        assertTrue(firstNext.phase.visible)
    }

    @Test fun `lone roundabout - recover fades then normal navigation`() {
        val d = Drive(motorway, motorwayTopology)
        val exit = RealJunctions.alongOf(motorway, 15)
        val views = d.drive(RealJunctions.alongOf(motorway, 14) - 300, exit + 400, 40.0)
        val recover = views.filter { it.second?.phase == JunctionPhase.RECOVER }
        assertTrue(recover.isNotEmpty())
        val fades = recover.map { it.second!!.reveal }
        assertEquals(fades.sortedDescending(), fades)
        assertNull(views.last().second)
    }

    @Test fun `imprecise GPS never reaches lane precision`() {
        val d = Drive(motorway, motorwayTopology)
        val exit = RealJunctions.alongOf(motorway, 11)
        var s = exit - 900
        val phases = ArrayList<JunctionPhase>()
        while (s < exit - 30) { d.at(s, 110.0, accuracy = 40f)?.let { phases += it.phase; assertTrue(it.degraded) }; s += 30 }
        assertTrue(JunctionPhase.REINFORCE in phases)
        assertFalse(JunctionPhase.PRECISE in phases)
    }

    @Test fun `extrapolated position in a tunnel is degraded but kept`() {
        val d = Drive(motorway, motorwayTopology)
        val exit = RealJunctions.alongOf(motorway, 11)
        val g = d.at(exit - 300, 100.0, extrapolated = true)
        assertNotNull(g)
        assertTrue(g!!.degraded)
    }

    @Test fun `GPS lost, rerouting and off route remove the view`() {
        val exit = RealJunctions.alongOf(motorway, 11)
        val d = Drive(motorway, motorwayTopology)
        assertNotNull(d.at(exit - 300, 100.0))
        assertNull(d.at(exit - 270, 100.0, gpsLost = true))
        assertNotNull(d.at(exit - 240, 100.0))
        assertNull(d.at(exit - 210, 100.0, rerouting = true))
        assertNull(d.at(exit - 180, 100.0, offRouteM = 70.0))
        // Après recalcul : nouveau tracé (autre identifiant), la vue repart de zéro.
        val fresh = motorway.copy(id = "rerouted")
        val d2 = Drive(fresh, motorwayTopology.copy(routeId = "rerouted"), d.a)
        val g = d2.at(exit - 150, 100.0)
        assertNotNull(g)
        assertEquals("rerouted", g!!.routeId)
    }

    @Test fun `GPS jitter does not flip phases back`() {
        val d = Drive(motorway, motorwayTopology)
        val exit = RealJunctions.alongOf(motorway, 11)
        val reinforce = d.at(exit - 200, 90.0)!!
        assertEquals(JunctionPhase.REINFORCE, reinforce.phase)
        // Le fix suivant semble reculer de 40 m : l'étape reste.
        assertEquals(JunctionPhase.REINFORCE, d.at(exit - 240, 90.0)!!.phase)
    }

    @Test fun `going back well beyond the threshold lowers the phase (direction change)`() {
        val d = Drive(motorway, motorwayTopology)
        val exit = RealJunctions.alongOf(motorway, 11)
        assertEquals(JunctionPhase.PRECISE, d.at(exit - 90, 60.0)!!.phase)
        val back = d.at(exit - 600, 60.0)
        assertTrue(back == null || back.phase < JunctionPhase.PRECISE)
    }

    @Test fun `simple urban turns never trigger the view`() {
        val d = Drive(motorway, motorwayTopology)
        assertNull(d.at(RealJunctions.alongOf(motorway, 1) - 50, 40.0))
    }
}
