package fr.roadly.core.navigation.junction

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.BranchDiagrams
import fr.roadly.core.navigation.DiagramBranch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Schéma de bifurcation sur la topologie réelle de Toulouse (sorties 31b et 19, bifurcations) : ordre et côté exacts, angles lisibles. */
class BranchDiagramTest {
    private val route = RealJunctions.route("motorway")
    private val topology = RealJunctions.topology("motorway", route, 0)
    private val branching = setOf(
        ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT,
    )

    @Test fun `every real exit and fork with known branches gets a diagram whose route branch is on the announced side`() {
        var built = 0
        for ((i, m) in route.maneuvers.withIndex()) {
            if (m.type !in branching) continue
            val d = BranchDiagrams.build(route, i, topology) ?: continue
            built++
            assertEquals("$i : une seule branche d'itinéraire", 1, d.branches.count { it.isRoute })
            assertTrue("$i : au moins une branche à éviter", d.branches.size >= 2)
            // Ordre de gauche à droite conservé par le dessin, branches voisines distinctes à l'écran.
            assertEquals("$i", d.branches.sortedBy { it.angleDeg }, d.branches)
            d.branches.zipWithNext().forEach { (a, b) -> assertTrue("$i : ${a.drawnAngleDeg} → ${b.drawnAngleDeg}", b.drawnAngleDeg - a.drawnAngleDeg >= BranchDiagrams.MIN_GAP_DEG - 1e-6) }
            // Le côté annoncé par la manœuvre est celui du schéma : sortie à droite = branche de l'itinéraire à droite de sa voisine.
            val neighbours = listOfNotNull(d.branches.getOrNull(d.routeRank - 1), d.branches.getOrNull(d.routeRank + 1))
            val straightest = neighbours.minByOrNull { abs(it.angleDeg) }!!
            when (m.type) {
                ManeuverType.EXIT_RIGHT, ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_RIGHT ->
                    assertTrue("$i ${m.type} : itinéraire ${d.routeBranch.angleDeg}°, autre ${straightest.angleDeg}°", d.routeBranch.drawnAngleDeg > straightest.drawnAngleDeg)
                else -> assertTrue("$i ${m.type} : itinéraire ${d.routeBranch.angleDeg}°, autre ${straightest.angleDeg}°", d.routeBranch.drawnAngleDeg < straightest.drawnAngleDeg)
            }
        }
        assertTrue("au moins 3 schémas réels construits : $built", built >= 3)
    }

    @Test fun `no topology, no diagram - and none where the node has no other way out`() {
        val exit = route.maneuvers.indexOfFirst { it.type == ManeuverType.EXIT_RIGHT }
        assertNull(BranchDiagrams.build(route, exit, null))
        assertNull(BranchDiagrams.build(route, 0, topology))
        // Arrivée : rien à distinguer.
        assertNull(BranchDiagrams.build(route, route.maneuvers.lastIndex, topology))
    }

    @Test fun `a ramp leaving at a few degrees is drawn clearly on its side, the carriageway stays straight`() {
        val spread = BranchDiagrams.spread(listOf(DiagramBranch(0.0, 0.0, isRoute = false, isRamp = false), DiagramBranch(5.0, 5.0, isRoute = true, isRamp = true)))
        assertEquals(0.0, spread[0].drawnAngleDeg, 1e-9)
        assertEquals(BranchDiagrams.MIN_GAP_DEG, spread[1].drawnAngleDeg, 1e-9)
        // Rester sur la chaussée pendant qu'une bretelle part à droite : l'itinéraire reste droit, la bretelle est écartée.
        val stay = BranchDiagrams.spread(listOf(DiagramBranch(0.0, 0.0, isRoute = true, isRamp = false), DiagramBranch(6.0, 6.0, isRoute = false, isRamp = true)))
        assertEquals(0.0, stay[0].drawnAngleDeg, 1e-9)
        assertEquals(BranchDiagrams.MIN_GAP_DEG, stay[1].drawnAngleDeg, 1e-9)
        // Trois branches : l'ordre réel est gardé.
        val three = BranchDiagrams.spread(listOf(
            DiagramBranch(-40.0, -40.0, false, false), DiagramBranch(-4.0, -4.0, true, false), DiagramBranch(3.0, 3.0, false, true),
        ))
        assertEquals(three.map { it.drawnAngleDeg }.sorted(), three.map { it.drawnAngleDeg })
        assertNotNull(three.single { it.isRoute })
    }
}
