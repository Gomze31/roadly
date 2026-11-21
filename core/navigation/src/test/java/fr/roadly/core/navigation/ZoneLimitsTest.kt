package fr.roadly.core.navigation

import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.RouteHazard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Limitation d'une zone de danger alignée sur celle de la route (constat en voiture : zone à 80 sur une route à 90). */
class ZoneLimitsTest {
    private fun zone(start: Double, end: Double, limit: Int?) = RouteHazard(HazardKind.VIGILANCE_ZONE, start, end, speedLimitKmh = limit, key = "z")
    private fun profile(vararg spans: SpeedLimitSpan) = RouteAttributes("r", 10_000.0, spans.toList(), emptyList())

    @Test
    fun `la zone prend la limitation de la route à l'endroit contrôlé`() {
        val out = ZoneLimits.reconcile(listOf(zone(1_000.0, 3_400.0, 80)), profile(SpeedLimitSpan(0.0, 10_000.0, 90)))
        assertEquals(90, out.single().speedLimitKmh)
    }

    @Test
    fun `la limitation lue est celle de la fin de zone, pas celle du début`() {
        // 110 jusqu'à 2 km, puis 90 : le point contrôlé (fin − 450 m = 2 950 m) est sur le tronçon à 90.
        val p = profile(SpeedLimitSpan(0.0, 2_000.0, 110), SpeedLimitSpan(2_000.0, 10_000.0, 90))
        assertEquals(90, ZoneLimits.reconcile(listOf(zone(500.0, 3_400.0, 110)), p).single().speedLimitKmh)
    }

    @Test
    fun `route de limitation inconnue, la valeur de la source est gardée`() {
        val z = zone(1_000.0, 3_400.0, 80)
        assertSame(z, ZoneLimits.reconcile(listOf(z), profile(SpeedLimitSpan(0.0, 500.0, 50))).single())
        assertSame(z, ZoneLimits.reconcile(listOf(z), null).single())
    }

    @Test
    fun `zone sans limitation publiée, celle de la route est affichée`() {
        assertEquals(90, ZoneLimits.reconcile(listOf(zone(1_000.0, 3_400.0, null)), profile(SpeedLimitSpan(0.0, 10_000.0, 90))).single().speedLimitKmh)
    }

    @Test
    fun `les virages ne sont pas touchés`() {
        val curve = RouteHazard(HazardKind.entries.first { it != HazardKind.VIGILANCE_ZONE }, 100.0, 200.0)
        assertSame(curve, ZoneLimits.reconcile(listOf(curve), profile(SpeedLimitSpan(0.0, 10_000.0, 90))).single())
    }

    @Test
    fun `zone courte, le point lu reste dans la zone`() {
        // Zone de 300 m en ville : fin − 450 m tomberait avant la zone, on lit à son début.
        val p = profile(SpeedLimitSpan(0.0, 1_000.0, 70), SpeedLimitSpan(1_000.0, 10_000.0, 50))
        assertEquals(50, ZoneLimits.reconcile(listOf(zone(1_100.0, 1_400.0, 30)), p).single().speedLimitKmh)
    }

    @Test
    fun `sans itinéraire, la limitation de la route suivie l'emporte dans la zone`() {
        assertEquals(90, ZoneLimits.inside(80, 90))
        assertEquals(80, ZoneLimits.inside(80, null))
        assertEquals(null, ZoneLimits.inside(null, null))
    }
}
