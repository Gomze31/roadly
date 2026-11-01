package fr.roadly.core.navigation

import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B4 sur données réelles (scénarios 4 et 5) : Paris (2e et 5e sorties) et autoroute de Toulouse (3e sortie). */
class RoundaboutResolverTest {
    private val paris = ParisRoute.route()

    @Test fun `champs-elysees roundabout - second exit straight through, ring between entry and exit`() {
        val info = RoundaboutResolver.resolve(paris, 10)
        assertEquals(2, info.exitNumber)
        assertNull("le nombre total de sorties n'est pas fourni par /route", info.exitCount)
        assertEquals(296.0, info.entryBearingDeg!!, 0.0)
        assertEquals(296.0, info.exitBearingDeg!!, 0.0)
        assertEquals(0.0, info.exitAngleDeg!!, 0.0)
        assertEquals(paris.geometry.subList(207, 225), info.ringGeometry)
        assertEquals("Avenue des Champs-Élysées", info.exitStreet)
    }

    @Test fun `etoile - fifth exit without an exit maneuver, nothing invented`() {
        val info = RoundaboutResolver.resolve(paris, 12)
        assertEquals(5, info.exitNumber)
        assertEquals(295.0, info.entryBearingDeg!!, 0.0)
        assertNull(info.exitBearingDeg); assertNull(info.exitAngleDeg); assertNull(info.exitStreet)
        assertEquals(paris.geometry.subList(298, 323), info.ringGeometry)
    }

    @Test fun `motorway fixture - a roundabout of three exits or more is resolved from the raw data`() {
        val r = MotorwayRoute.route()
        val raw = MotorwayRoute.rawManeuvers
        val k = raw.indices.first { i ->
            raw[i]["type"]!!.jsonPrimitive.int == 26 && ((raw[i]["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull ?: 0) >= 3 &&
                raw.getOrNull(i + 1)?.get("type")?.jsonPrimitive?.int == 27
        }
        val info = RoundaboutResolver.resolve(r, k)
        assertEquals((raw[k]["roundabout_exit_count"] as JsonPrimitive).intOrNull, info.exitNumber)
        assertEquals(r.maneuvers[k + 1].streetName, info.exitStreet)
        assertEquals(r.geometry.subList(r.maneuvers[k].beginShapeIndex, r.maneuvers[k + 1].beginShapeIndex + 1), info.ringGeometry)
        assertEquals(raw[k + 1]["bearing_after"]!!.jsonPrimitive.int.toDouble(), info.exitBearingDeg!!, 0.0)
    }

    @Test fun `all roundabouts are indexed by their entry`() {
        assertEquals(setOf(10, 12), RoundaboutResolver.resolveAll(paris).keys)
        assertEquals(setOf(1, 3), RoundaboutResolver.resolveAll(TestRoutes.twoRoundabouts()).keys)
    }

    @Test fun `the roundabout is shown before entry and kept inside the ring`() {
        val all = RoundaboutResolver.resolveAll(paris)
        assertEquals(all[10], RoundaboutResolver.current(all, 10))
        assertEquals("dans l'anneau : prochaine = sortie", all[10], RoundaboutResolver.current(all, 11))
        assertEquals("Étoile : prochaine = arrivée, courante = entrée", all[12], RoundaboutResolver.current(all, 13))
        assertNull(RoundaboutResolver.current(all, 9))
    }

    @Test fun `exit target is frozen under jitter at the entry and inside the ring`() {
        val t = RouteTracker(paris)
        val all = RoundaboutResolver.resolveAll(paris)
        val script = FixScript(paris, seed = 13)
        val enterAt = t.maneuverStartM[10]
        script.drive(enterAt - 150.0, enterAt - 30.0, speedKmh = 30.0).forEach { t.update(it) }
        assertEquals(List(10) { 10 }, script.jitter(enterAt, 10, 8.0).map { t.update(it).nextManeuverIndex })
        script.drive(enterAt - 8.0, enterAt + 25.0, speedKmh = 20.0).forEach { t.update(it) }
        val inRing = script.jitter(enterAt + 28.0, 10, 8.0).map { RoundaboutResolver.current(all, t.update(it).nextManeuverIndex) }
        assertTrue(inRing.all { it == all[10] })
    }

    @Test fun `the exit of the roundabout is announced inside the ring only`() {
        val t = RouteTracker(paris)
        val enterAt = t.maneuverStartM[10]
        val exitAt = t.maneuverStartM[11]
        val already = mutableSetOf<Pair<Int, Tier>>()
        val said = mutableListOf<Pair<Announcement, Double>>()
        FixScript(paris, seed = 17).drive(enterAt - 200.0, exitAt + 20.0, speedKmh = 20.0).forEach { f ->
            val p = t.update(f)
            AnnouncementPolicy.next(p, paris.maneuvers, 20.0, already)?.let { a -> already += a.maneuverIndex to a.tier; said += a to p.distanceAlongM }
        }
        val exitCalls = said.filter { it.first.maneuverIndex == 11 }
        assertTrue("la sortie est annoncée", exitCalls.isNotEmpty())
        assertTrue("jamais avant d'être entré dans l'anneau", exitCalls.all { it.second > enterAt })
        assertEquals(Tier.NOW, exitCalls.last().first.tier)
        assertEquals(paris.maneuvers[11].verbalPre, exitCalls.last().first.text)
    }
}
