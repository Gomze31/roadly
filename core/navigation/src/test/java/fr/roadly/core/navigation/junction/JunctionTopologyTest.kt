package fr.roadly.core.navigation.junction

import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.RoadClass
import fr.roadly.core.navigation.RouteGeometry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Topologie réelle (`trace_attributes`) : décodage, comptage des sorties de rond-point, classement des carrefours. */
class JunctionTopologyTest {
    private val motorway = RealJunctions.route("motorway")

    @Test fun `parse keeps lanes, classes, bridges and drops non driveable branches`() {
        val t = RealJunctions.topology("motorway", motorway, 0)
        assertEquals(133, t.edges.size)
        // Périphérique de Toulouse avant la sortie 31b : 4 voies, autoroute, pas une bretelle.
        val before31b = t.edges.first { it.endIndex == 79 }
        assertEquals(4, before31b.laneCount)
        assertEquals(RoadClass.MOTORWAY, before31b.roadClass)
        assertFalse(before31b.isRamp)
        // La sortie elle-même est une bretelle à 1 voie.
        val ramp = t.edges.first { it.beginIndex == 79 }
        assertTrue(ramp.isRamp)
        assertEquals(1, ramp.laneCount)
        assertTrue("ponts de l'A620", t.edges.any { it.bridge })
        // Aucune branche piétonne : toutes les branches gardées sont carrossables dans au moins un sens.
        assertTrue(t.edges.flatMap { it.branches }.all { it.inbound || it.outbound })
    }

    @Test fun `parse shifts indices by the window offset`() {
        val text = javaClass.classLoader!!.getResource("fixtures/junction_motorway_trace.json")!!.readText()
        val shifted = ValhallaJunctionTopology.parse(Json { ignoreUnknownKeys = true }, text, indexOffset = 100)!!
        assertEquals(100, shifted.first().beginIndex)
    }

    @Test fun `parse rejects garbage`() {
        assertNull(ValhallaJunctionTopology.parse(Json, "not json", 0))
    }

    @Test fun `window covers the approach and the exit`() {
        val g = RouteGeometry(motorway.geometry)
        val at = RealJunctions.alongOf(motorway, 11)
        val w = JunctionWindow.around(g, at)
        assertTrue(g.cumulative[w.fromIndex] <= at - JunctionWindow.BEFORE_M + 60)
        assertTrue(g.cumulative[w.toIndex] >= at + JunctionWindow.AFTER_M - 1 || w.toIndex == motorway.geometry.lastIndex)
    }

    @Test fun `Toulouse roundabouts - topological exit count equals Valhalla`() {
        val t = RealJunctions.topology("motorway", motorway, 0)
        val enters = RealJunctions.enterIndices(motorway)
        assertEquals(listOf(2, 4, 6, 14), enters)
        for (i in enters) {
            val r = RoundaboutExitCounter.count(motorway, i, t)!!
            assertTrue("rond-point $i", r.consistent)
            assertEquals(motorway.maneuvers[i].roundaboutExit, r.targetExit)
            assertEquals((1..r.targetExit!!).toList(), r.exits.map { it.rank })
            assertTrue(r.exits.last().isTarget)
        }
    }

    @Test fun `Muret - 3rd exit counted on the real ring`() {
        val route = RealJunctions.route("muret")
        val t = RealJunctions.topology("muret", route, 0)
        val third = RealJunctions.enterIndices(route).first { route.maneuvers[it].roundaboutExit == 3 }
        val r = RoundaboutExitCounter.count(route, third, t)!!
        assertTrue(r.consistent)
        assertEquals(listOf(1, 2, 3), r.exits.map { it.rank })
    }

    @Test fun `place de la Nation and Etoile - 6th exit on complex roundabouts`() {
        for (name in listOf("nation", "etoile")) {
            val route = RealJunctions.route(name)
            val t = RealJunctions.topology(name, route, 0)
            val i = RealJunctions.enterIndices(route).first()
            val r = RoundaboutExitCounter.count(route, i, t)!!
            assertTrue(name, r.consistent)
            assertEquals(name, 6, r.targetExit)
            assertEquals(name, 6, r.exits.size)
        }
    }

    @Test fun `without topology only the Valhalla target is numbered`() {
        val r = RoundaboutExitCounter.count(motorway, 14, topology = null)!!
        assertFalse(r.consistent)
        assertEquals(3, r.targetExit)
        assertEquals(listOf(3), r.exits.map { it.rank })
    }

    @Test fun `diverging count never invents intermediate numbers`() {
        val t = RealJunctions.topology("motorway", motorway, 0)
        // Une sortie retirée de l'anneau : le comptage ne donne plus le rang de Valhalla.
        val ringEdge = t.edges.first { it.roundabout && it.beginIndex >= 480 && it.branches.any { b -> b.outbound } }
        val tampered = t.copy(edges = t.edges.map { if (it === ringEdge) it.copy(branches = emptyList()) else it })
        val r = RoundaboutExitCounter.count(motorway, 14, tampered)!!
        assertFalse(r.consistent)
        assertEquals(3, r.targetExit)
        assertEquals(listOf(3), r.exits.map { it.rank })
    }

    @Test fun `classifier - exits, fork, roundabouts relevant, simple turn not`() {
        val t = RealJunctions.topology("motorway", motorway, 0)
        val exit31b = JunctionClassifier.classify(motorway, 9, t, gapToNextM = 2_792.0)!!
        assertTrue(exit31b.kind == JunctionKind.MOTORWAY_EXIT || exit31b.kind == JunctionKind.INTERCHANGE)
        val exit19 = JunctionClassifier.classify(motorway, 11, t, gapToNextM = 605.0)!!
        assertTrue(exit19.kind == JunctionKind.MOTORWAY_EXIT || exit19.kind == JunctionKind.INTERCHANGE)
        // Sortie suivie d'une bifurcation à 605 m : complexité relevée (sorties rapprochées).
        assertTrue(exit19.complexity >= 1)
        val fork = JunctionClassifier.classify(motorway, 10, t, gapToNextM = 11_199.0)!!
        assertTrue(fork.kind == JunctionKind.MOTORWAY_FORK || fork.kind == JunctionKind.INTERCHANGE)
        assertEquals(JunctionKind.ROUNDABOUT, JunctionClassifier.classify(motorway, 14, t, null)!!.kind)
        assertNull("tournant simple en ville", JunctionClassifier.classify(motorway, 1, t, 68.0))
        assertNull("départ", JunctionClassifier.classify(motorway, 0, t, 58.0))
    }

    @Test fun `classifier - multi level interchange on the A620`() {
        val t = RealJunctions.topology("motorway", motorway, 0)
        // Fenêtre autour du « Gardez la gauche pour rester sur A 620 » : ponts dans la fenêtre → échangeur, complexité ≥ 1.
        val window = JunctionWindow.around(RouteGeometry(motorway.geometry), RealJunctions.alongOf(motorway, 10))
        val local = t.copy(edges = t.edges.filter { it.beginIndex >= window.fromIndex && it.endIndex <= window.toIndex })
        val c = JunctionClassifier.classify(motorway, 10, local, 11_199.0)!!
        assertEquals(JunctionKind.INTERCHANGE, c.kind)
        assertTrue(c.complexity >= 1)
    }

    @Test fun `sign comes only from Valhalla`() {
        val s = JunctionClassifier.sign(motorway, 9)
        assertNotNull(s)
        assertEquals("31b", s!!.exitNumber)
        assertNull(JunctionClassifier.sign(motorway, 1))
    }
}
