package fr.roadly.core.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

/**
 * Position de l'équipement dans sa zone : stable d'un passage à l'autre, mais pas déductible de la fin de zone (R413-15).
 * Mesures sur la liste officielle réelle (3 309 équipements).
 */
class RadarZonePlacementTest {
    private val radars by lazy {
        val text = javaClass.classLoader!!.getResourceAsStream("radars-fixes-2025-12.csv")!!
            .use { InputStreamReader(it, Charsets.ISO_8859_1).readText() }
        RadarMerger.merge(RadarSources.parseFixedList(text), emptyList(), collectedAtMs = 0L, sourceVersion = "test").radars
    }

    @Test fun `placement is deterministic per equipment`() {
        val r = radars.first()
        assertEquals(RadarRegulation.leadM(r), RadarRegulation.leadM(r.copy()), 0.0)
        assertEquals(RadarRegulation.tailM(r), RadarRegulation.tailM(r.copy()), 0.0)
        assertEquals(RadarRegulation.stableFraction("01234", 7), RadarRegulation.stableFraction("01234", 7), 0.0)
    }

    @Test fun `lengths stay within the regulatory ranges`() {
        for (r in radars) {
            val base = RadarRegulation.baseLeadM(r)
            val lead = RadarRegulation.leadM(r)
            assertTrue(r.id, lead in base * RadarRegulation.LEAD_MIN_FACTOR..base * RadarRegulation.LEAD_MAX_FACTOR)
            assertTrue(r.id, RadarRegulation.tailM(r) in RadarRegulation.TAIL_MIN_M..RadarRegulation.TAIL_MAX_M)
        }
    }

    @Test fun `zone end does not reveal the equipment position`() {
        val tails = radars.map { RadarRegulation.tailM(it) }
        // Toute la plage est utilisée : connaître la fin de zone laisse 300 m d'incertitude sur la position.
        assertTrue(tails.min() < RadarRegulation.TAIL_MIN_M + 15)
        assertTrue(tails.max() > RadarRegulation.TAIL_MAX_M - 15)
        // Répartition à peu près uniforme : chaque tiers de la plage reçoit entre 25 et 42 % des équipements.
        val span = RadarRegulation.TAIL_MAX_M - RadarRegulation.TAIL_MIN_M
        val thirds = tails.groupingBy { ((it - RadarRegulation.TAIL_MIN_M) / span * 3).toInt().coerceIn(0, 2) }.eachCount()
        println("Traîne : min=${tails.min().toInt()} max=${tails.max().toInt()} tiers=$thirds")
        for (i in 0..2) assertTrue("tiers $i : ${thirds[i]}", (thirds[i] ?: 0).toDouble() / tails.size in 0.25..0.42)
        // Début et fin indépendants : la longueur totale ne permet pas non plus de retrouver le point.
        val distinctTails = tails.map { it.toInt() / 10 }.toSet().size
        assertTrue("traînes distinctes : $distinctTails", distinctTails > 25)
    }

    @Test fun `neighbour identifiers spread out`() {
        val a = RadarRegulation.stableFraction("06125", 0x7A11)
        val b = RadarRegulation.stableFraction("06126", 0x7A11)
        assertTrue(kotlin.math.abs(a - b) > 0.01)
    }
}
