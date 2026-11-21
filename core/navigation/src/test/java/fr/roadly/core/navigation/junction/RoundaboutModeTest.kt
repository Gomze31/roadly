package fr.roadly.core.navigation.junction

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.AnnouncementPolicy
import fr.roadly.core.navigation.RoundaboutLayout
import fr.roadly.core.navigation.RoundaboutLayouts
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RoundaboutProgressTracker
import fr.roadly.core.navigation.RoundaboutResolver
import fr.roadly.core.navigation.RoundaboutVoice
import fr.roadly.core.navigation.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode rond-point sur des ronds-points réels (Étoile, Nation, Muret, Toulouse) : schéma, décompte, voix à la sortie. */
class RoundaboutModeTest {
    private fun layout(name: String, enter: Int, route: Route = RealJunctions.route(name), withTopology: Boolean = true): RoundaboutLayout {
        val topology = if (withTopology) RealJunctions.topology(name, route, enter) else null
        return RoundaboutLayouts.build(RoundaboutResolver.resolve(route, enter), RoundaboutExitCounter.count(route, enter, topology))
    }

    @Test fun `etoile 6th exit - every exit to pass is placed in order, the target keeps the real sweep`() {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val l = layout("etoile", enter, route)
        assertTrue(l.counted)
        assertEquals(6, l.targetRank)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), l.branches.map { it.rank })
        assertEquals(5, l.intermediateCount)
        assertTrue(l.branches.last().isTarget)
        assertEquals(l.sweepDeg!!, l.branches.last().angleDeg, 1e-6)
        assertEquals(l.ringLengthM, l.branches.last().offsetM, 1e-6)
        // Branches distinctes à l'écran, dans le sens de circulation, jamais collées à l'entrée.
        l.branches.zipWithNext().forEach { (a, b) -> assertTrue("${a.angleDeg} → ${b.angleDeg}", b.angleDeg - a.angleDeg >= RoundaboutLayouts.MIN_BRANCH_GAP_DEG - 1e-6) }
        assertTrue(l.branches.first().angleDeg >= RoundaboutLayouts.MIN_FIRST_BRANCH_DEG)
        l.branches.zipWithNext().forEach { (a, b) -> assertTrue(b.offsetM >= a.offsetM) }
    }

    @Test fun `every real roundabout with a consistent count yields a counted layout ending on the target`() {
        var checked = 0
        for (name in listOf("motorway", "muret", "nation", "etoile")) {
            val route = RealJunctions.route(name)
            for (enter in RealJunctions.enterIndices(route)) {
                val counted = RoundaboutExitCounter.count(route, enter, RealJunctions.topology(name, route, enter)) ?: continue
                if (!counted.consistent) continue
                val l = layout(name, enter, route)
                if (l.sweepDeg == null) continue
                assertTrue("$name/$enter", l.counted)
                assertEquals("$name/$enter", route.maneuvers[enter].roundaboutExit, l.targetRank)
                assertEquals("$name/$enter", route.maneuvers[enter].roundaboutExit!! - 1, l.intermediateCount)
                checked++
            }
        }
        assertTrue("au moins 6 ronds-points réels vérifiés, $checked", checked >= 6)
    }

    @Test fun `without topology only the target exit is drawn and no countdown is claimed`() {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val l = layout("etoile", enter, route, withTopology = false)
        assertFalse(l.counted)
        assertEquals(1, l.branches.size)
        assertTrue(l.branches.single().isTarget)
        val live = RoundaboutProgressTracker().update(route.id, enter, l, inside = true, distanceToNextM = l.ringLengthM / 2)
        assertNull(live.exitsRemaining)
        assertFalse(live.exitIsNext)
        assertNull(RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, live))
    }

    @Test fun `driving the ring - the countdown goes down one exit at a time and the vehicle angle only grows`() {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val l = layout("etoile", enter, route)
        val t = RoundaboutProgressTracker()
        val approach = t.update(route.id, enter, l, inside = false, distanceToNextM = 120.0)
        assertEquals(RoundaboutPhase.APPROACH, approach.phase)
        assertEquals(5, approach.exitsRemaining)
        assertEquals(0f, approach.ringFraction)
        val remaining = ArrayList<Int>()
        val angles = ArrayList<Double>()
        var offset = 0.0
        while (offset <= l.ringLengthM) {
            val live = t.update(route.id, enter, l, inside = true, distanceToNextM = l.ringLengthM - offset)
            remaining += live.exitsRemaining!!
            angles += live.vehicleAngleDeg!!
            assertEquals(live.exitsRemaining == 0, live.exitIsNext)
            offset += 4.0
        }
        assertEquals(listOf(5, 4, 3, 2, 1, 0), remaining.distinct())
        assertEquals(angles.sorted(), angles)
        assertTrue(angles.last() <= l.sweepDeg!! + 1e-6)
    }

    @Test fun `a GPS jump backwards never relights a passed exit`() {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val l = layout("etoile", enter, route)
        val t = RoundaboutProgressTracker()
        val third = l.branches[2].offsetM
        val before = t.update(route.id, enter, l, inside = true, distanceToNextM = l.ringLengthM - third - 3.0)
        val jumped = t.update(route.id, enter, l, inside = true, distanceToNextM = l.ringLengthM - l.branches[0].offsetM + 5.0)
        assertEquals(3, before.exitsPassed)
        assertEquals(3, jumped.exitsPassed)
        assertEquals(before.ringFraction, jumped.ringFraction)
        assertEquals(before.vehicleAngleDeg, jumped.vehicleAngleDeg)
    }

    @Test fun `another roundabout starts a fresh count`() {
        val route = RealJunctions.route("muret")
        val (first, second) = RealJunctions.enterIndices(route)
        val t = RoundaboutProgressTracker()
        val l1 = layout("muret", first, route)
        t.update(route.id, first, l1, inside = true, distanceToNextM = 0.0)
        val l2 = layout("muret", second, route)
        val live = t.update(route.id, second, l2, inside = false, distanceToNextM = 80.0)
        assertEquals(0, live.exitsPassed)
        assertEquals(0f, live.ringFraction)
    }

    @Test fun `after the exit the diagram is complete and confirms`() {
        val route = RealJunctions.route("muret")
        val enter = RealJunctions.enterIndices(route).first()
        val l = layout("muret", enter, route)
        val live = RoundaboutProgressTracker().exited(l, 12.0)
        assertEquals(RoundaboutPhase.EXITED, live.phase)
        assertEquals(1f, live.ringFraction)
        assertEquals(0, live.exitsRemaining)
        assertEquals(12.0, live.distanceM, 1e-9)
    }

    @Test fun `exit voice waits for the previous exit, then speaks whatever the distance`() {
        val route = RealJunctions.route("etoile")
        val enter = RealJunctions.enterIndices(route).first()
        val l = layout("etoile", enter, route)
        val t = RoundaboutProgressTracker()
        val early = t.update(route.id, enter, l, inside = true, distanceToNextM = l.ringLengthM - 5.0)
        assertEquals(false, RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, early))
        // 30 m avant la sortie mais une sortie reste à passer : la règle de distance dirait « maintenant », le décompte l'interdit.
        val p = RealJunctions.progressAt(route, RealJunctions.alongOf(route, enter + 1) - 30.0)
        assertEquals(enter + 1, p.nextManeuverIndex)
        assertNull(AnnouncementPolicy.next(p, route.maneuvers, 30.0, emptySet(), roundaboutExitGate = false))
        val last = t.update(route.id, enter, l, inside = true, distanceToNextM = l.ringLengthM - l.branches[4].offsetM - 3.0)
        assertEquals(true, RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, last))
        val far = RealJunctions.progressAt(route, RealJunctions.alongOf(route, enter + 1) - 90.0)
        val a = AnnouncementPolicy.next(far, route.maneuvers, 30.0, emptySet(), roundaboutExitGate = true)!!
        assertEquals(Tier.NOW, a.tier)
        assertEquals(route.maneuvers[enter + 1].verbalPre, a.text.substringBefore(", puis"))
        // Dit une seule fois.
        assertNull(AnnouncementPolicy.next(far, route.maneuvers, 30.0, setOf(enter + 1 to Tier.NOW), roundaboutExitGate = true))
    }

    @Test fun `a first exit needs no gate - the usual rule applies`() {
        val route = RealJunctions.route("motorway")
        val enter = RealJunctions.enterIndices(route).first { route.maneuvers[it].roundaboutExit == 1 }
        val l = layout("motorway", enter, route)
        val live = RoundaboutProgressTracker().update(route.id, enter, l, inside = true, distanceToNextM = 5.0)
        assertNull(RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, live))
        assertNull(RoundaboutVoice.exitGate(ManeuverType.TURN_LEFT, live))
    }
}
