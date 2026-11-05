package fr.roadly.core.navigation

import fr.roadly.core.navigation.DrivingCoach.Advice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingCoachTest {
    private val min = 60_000L

    @Test fun `only confirmed drops are announced`() {
        val c = DrivingCoach()
        var t = 0L
        fun tick(limit: Int?) = c.update(t.also { t += 1_000 }, limit, 50.0).filterIsInstance<Advice.LimitDropped>()
        assertTrue(tick(90).isEmpty())
        assertTrue(tick(90).isEmpty()) // première limitation connue : rien
        assertTrue(tick(70).isEmpty()) // pas encore confirmée
        assertEquals(listOf(Advice.LimitDropped(70)), tick(70))
        assertTrue(tick(70).isEmpty()) // une seule fois
        assertTrue(tick(90).isEmpty())
        assertTrue(tick(90).isEmpty()) // hausse : silence
        assertTrue(tick(50).isEmpty())
        assertTrue(tick(90).isEmpty()) // 50 fugace (une position) : ignoré
        assertTrue(tick(null).isEmpty())
        assertTrue(tick(null).isEmpty()) // inconnue : rien
        assertTrue(tick(80).isEmpty())
        assertEquals(listOf(Advice.LimitDropped(80)), tick(80))
    }

    @Test fun `break after two hours, then every thirty minutes, reset by a real stop`() {
        val c = DrivingCoach()
        fun breaks(now: Long, speed: Double) = c.update(now, null, speed).count { it == Advice.TakeABreak }
        assertEquals(0, breaks(0, 90.0))
        assertEquals(0, breaks(119 * min, 90.0))
        assertEquals(1, breaks(120 * min, 90.0))
        assertEquals(0, breaks(121 * min, 90.0))
        assertEquals(1, breaks(150 * min, 90.0))
        // Feu rouge de 2 min : le compteur continue.
        assertEquals(0, breaks(151 * min, 0.0))
        assertEquals(0, breaks(153 * min, 90.0))
        assertEquals(1, breaks(180 * min, 90.0))
        // Vraie pause de 20 min : nouveau départ.
        assertEquals(0, breaks(181 * min, 0.0))
        assertEquals(0, breaks(201 * min, 0.0))
        assertEquals(0, breaks(202 * min, 90.0))
        assertEquals(0, breaks(321 * min, 90.0))
        assertEquals(1, breaks(322 * min, 90.0))
    }
}
