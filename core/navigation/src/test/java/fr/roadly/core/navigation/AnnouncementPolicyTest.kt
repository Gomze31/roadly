package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AnnouncementPolicyTest {
    private val maneuvers = ParisRoute.route().maneuvers
    private fun progress(next: Int, dist: Double, extrapolated: Boolean = false) =
        RouteProgress("paris", 0, 0.0, 0.0, next, dist, 1000.0, 100.0, GeoPoint(48.85, 2.35), null, extrapolated)
    private fun next(k: Int, d: Double, v: Double?, already: Set<Pair<Int, Tier>> = emptySet(), extrapolated: Boolean = false, sincePrevious: Long? = null) =
        AnnouncementPolicy.next(progress(k, d, extrapolated), maneuvers, v, already, sincePrevious)
    private fun lower(s: String) = s.replaceFirstChar { it.lowercase(Locale.FRANCE) }

    @Test fun `city far tier uses the alert text with a spoken distance`() {
        val a = next(1, 250.0, 30.0)!!
        assertEquals(Tier.FAR, a.tier)
        assertEquals(1, a.maneuverIndex)
        assertEquals("Dans 250 mètres, " + lower(maneuvers[1].verbalAlert!!), a.text)
    }

    @Test fun `city near tier at 70 m`() {
        val a = next(1, 70.0, 30.0)!!
        assertEquals(Tier.NEAR, a.tier)
        assertEquals("Dans 70 mètres, " + lower(maneuvers[1].verbalAlert!!), a.text)
    }

    @Test fun `now tier speaks the pre transition text verbatim`() {
        val a = next(1, 30.0, 30.0)!!
        assertEquals(Tier.NOW, a.tier)
        assertEquals(maneuvers[1].verbalPre, a.text)
    }

    @Test fun `nothing beyond the far threshold of the speed class`() {
        assertNull(next(1, 350.0, 30.0))   // ville : 300 m
        assertNull(next(1, 700.0, 70.0))   // route : 600 m
        assertNull(next(1, 2100.0, 110.0)) // autoroute : 2000 m
    }

    @Test fun `highway far tier at 1,5 km`() {
        val a = next(1, 1500.0, 110.0)!!
        assertEquals(Tier.FAR, a.tier)
        assertEquals("Dans 1,5 kilomètre, " + lower(maneuvers[1].verbalAlert!!), a.text)
    }

    @Test fun `road thresholds`() {
        assertEquals(Tier.FAR, next(1, 600.0, 70.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 120.0, 70.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 150.0, 50.0)!!.tier)
    }

    @Test fun `threshold edges`() {
        assertEquals(Tier.FAR, next(1, 300.0, 30.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 80.0, 30.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 40.0, 30.0)!!.tier)
        assertEquals(Tier.NOW, next(1, 39.9, 30.0)!!.tier)
        assertEquals(Tier.FAR, next(1, 2000.0, 90.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 300.0, 90.0)!!.tier)
    }

    @Test fun `unknown speed uses city thresholds`() {
        assertNull(next(1, 500.0, null))
        assertEquals(Tier.FAR, next(1, 250.0, null)!!.tier)
    }

    @Test fun `same tier is never repeated`() = assertNull(next(1, 240.0, 30.0, setOf(1 to Tier.FAR)))

    @Test fun `a tier never comes back after a more urgent one`() {
        assertNull(next(1, 250.0, 30.0, setOf(1 to Tier.NEAR)))
        assertEquals(Tier.NOW, next(1, 30.0, 30.0, setOf(1 to Tier.NEAR))!!.tier)
        assertNull(next(1, 10.0, 30.0, setOf(1 to Tier.NOW)))
    }

    @Test fun `tiers of another maneuver do not block`() {
        val a = next(2, 250.0, 30.0, setOf(1 to Tier.NOW))!!
        assertEquals(2, a.maneuverIndex)
        assertEquals(Tier.FAR, a.tier)
    }

    @Test fun `missing alert text falls back to the pre transition text`() {
        assertNull("la fixture n'a pas d'alerte pour la manœuvre 11", maneuvers[11].verbalAlert)
        assertEquals("Dans 200 mètres, " + lower(maneuvers[11].verbalPre!!), next(11, 200.0, 30.0)!!.text)
    }

    @Test fun `arrival now uses the fixture sentence`() {
        val last = maneuvers.lastIndex
        assertEquals(maneuvers[last].verbalPre, next(last, 10.0, 30.0)!!.text)
    }

    @Test fun `now tier is suppressed while the position is extrapolated`() {
        assertNull(next(1, 30.0, 30.0, extrapolated = true))
        assertEquals(Tier.NOW, next(1, 30.0, 30.0, extrapolated = false)!!.tier)
    }

    @Test fun `now tier is suppressed when the fix is imprecise`() { // scénario 10
        assertNull("précision 60 m à 30 m du virage : pas de NOW", AnnouncementPolicy.next(progress(1, 30.0).copy(accuracyM = 60f), maneuvers, 30.0, setOf(1 to Tier.NEAR)))
        assertEquals(Tier.NOW, AnnouncementPolicy.next(progress(1, 30.0).copy(accuracyM = 10f), maneuvers, 30.0, setOf(1 to Tier.NEAR))!!.tier)
    }

    @Test fun `far and near tiers are still announced while extrapolated`() {
        assertEquals(Tier.FAR, next(1, 250.0, 30.0, extrapolated = true)!!.tier)
        assertEquals(Tier.NEAR, next(1, 70.0, 30.0, extrapolated = true)!!.tier)
    }

    @Test fun `out of range maneuver index gives nothing`() = assertNull(next(99, 10.0, 30.0))

    @Test fun `now threshold is four seconds of travel, at least 40 m`() {
        assertEquals(40.0, AnnouncementPolicy.nowThresholdM(null), 1e-9)
        assertEquals(40.0, AnnouncementPolicy.nowThresholdM(30.0), 1e-9)
        assertEquals(4 * 130 / 3.6, AnnouncementPolicy.nowThresholdM(130.0), 1e-9)
        assertEquals(Tier.NOW, next(1, 140.0, 130.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 150.0, 130.0)!!.tier)
    }

    @Test fun `at 130 km h and 1 Hz the now tier is never skipped`() {
        val step = 130 / 3.6
        var d = 2_500.0
        val already = mutableSetOf<Pair<Int, Tier>>()
        var nowAt: Double? = null
        while (d >= 0.0) {
            AnnouncementPolicy.next(progress(1, d), maneuvers, 130.0, already)?.let { a -> already += 1 to a.tier; if (a.tier == Tier.NOW) nowAt = d }
            d -= step
        }
        assertTrue("NOW au moins 3 s avant la manœuvre (à $nowAt m)", nowAt != null && nowAt!! >= 3 * step)
        assertEquals(setOf(Tier.FAR, Tier.NEAR, Tier.NOW), already.map { it.second }.toSet())
    }

    @Test fun `a following maneuver less than 200 m after is chained with puis`() {
        // Paris : manœuvre 1 (Rue de Lobau) puis 2 (Quai de l'Hôtel de Ville) 77 m plus loin.
        val p = progress(1, 70.0).copy(distancesToManeuversM = listOf(70.0 + 77.0, 400.0, 600.0))
        val a = AnnouncementPolicy.next(p, maneuvers, 30.0, emptySet())!!
        assertEquals(Tier.NEAR, a.tier)
        assertEquals("Dans 70 mètres, " + lower(maneuvers[1].verbalAlert!!).removeSuffix(".") + ", puis " + lower(maneuvers[2].verbalPre!!), a.text)
    }

    @Test fun `no chaining beyond 200 m, for the far tier, or toward the exit of the roundabout just entered`() {
        assertEquals(maneuvers[1].verbalPre, AnnouncementPolicy.next(progress(1, 30.0).copy(distancesToManeuversM = listOf(280.0)), maneuvers, 30.0, emptySet())!!.text)
        assertEquals("Dans 250 mètres, " + lower(maneuvers[1].verbalAlert!!), AnnouncementPolicy.next(progress(1, 250.0).copy(distancesToManeuversM = listOf(327.0)), maneuvers, 30.0, emptySet())!!.text)
        assertEquals(maneuvers[10].verbalPre, AnnouncementPolicy.next(progress(10, 30.0).copy(distancesToManeuversM = listOf(87.0)), maneuvers, 30.0, emptySet())!!.text)
    }

    @Test fun `no far announcement within 10 s of the previous maneuver`() {
        assertNull(next(1, 250.0, 30.0, sincePrevious = 9_999L))
        assertEquals(Tier.FAR, next(1, 250.0, 30.0, sincePrevious = 10_000L)!!.tier)
        assertEquals(Tier.NEAR, next(1, 70.0, 30.0, sincePrevious = 2_000L)!!.tier)
    }
}
