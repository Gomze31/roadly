package fr.roadly.core.navigation.lab

import fr.roadly.core.model.Route
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.junction.RealJunctions
import fr.roadly.core.navigation.lab.GuidanceLab.Scenario
import fr.roadly.core.navigation.lab.GuidanceLab.Timeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Laboratoire GPS, conditions hostiles : fixes faux, en retard, imprécis ou absents, rejoués dans la vraie session de guidage sur
 * des tracés Valhalla réels ([RealJunctions]). Les positions sont **calculées** (aucune trace enregistrée) : ce sont des essais
 * simulés, pas des essais sur route. Chronologies écrites dans `build/guidance-lab/`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpsHostileConditionsLabTest {
    private val nation = RealJunctions.route("nation")
    private val motorway = RealJunctions.route("motorway")

    private fun startOf(route: Route, index: Int): Double = RouteTracker(route).maneuverStartM[index]

    private fun lab(scenario: Scenario, check: (Timeline) -> Unit) = runTest {
        val tl = GuidanceLab.run(this, scenario)
        tl.dump()
        check(tl)
    }

    private fun assertNone(what: String, problems: List<String>, tl: Timeline) =
        assertTrue("${tl.scenario.name} — $what : $problems\n$tl", problems.isEmpty())

    private fun assertSane(tl: Timeline, ignoring: Set<GuidanceMode> = emptySet()) {
        assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
        assertNone("annonce répétée", GuidanceLab.repeatedAnnouncements(tl), tl)
        assertNone("oscillation de mode", GuidanceLab.modeOscillations(tl, ignoring), tl)
        assertEquals("${tl.scenario.name} — aucun recalcul attendu\n$tl", 0, tl.requests.size)
    }

    /** Secondes et distance vraie au virage où le « maintenant » de la manœuvre [index] a été dit. */
    private fun nowAt(tl: Timeline, route: Route, index: Int): List<Pair<Int, Double>> {
        val at = startOf(route, index)
        return tl.ticks.filter { it.spoken.any { s -> GuidanceLab.isNow(s, route.maneuvers[index]) } }.map { it.t to (at - (it.trueAlongM ?: at)) }
    }

    // Nation, manœuvre 3 : « Tournez à droite dans Rue de Picpus », abordée à 30 km/h (seuil du « maintenant » : 40 m).

    @Test fun `speed spike of 250 km h for two fixes 200 m before a turn - now is not said early`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("pic de vitesse avant un virage", nation) {
            drive(from = turn - 320.0, to = turn - 215.0, speedKmh = 30.0)
            tamper(2, "vitesse 250 km/h") { _, fix, _ -> fix.copy(speedMps = 69.4f) }
            drive(to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            assertSane(tl)
            val now = nowAt(tl, nation, 3)
            assertEquals("« maintenant » dit une fois\n$tl", 1, now.size)
            // À 30 km/h, le virage est à 8,3 m/s : dit à plus de 80 m, il arriverait 10 s trop tôt.
            assertTrue("« maintenant » dit à ${now.single().second.toInt()} m du virage\n$tl", now.single().second <= 80.0)
        }
    }

    @Test fun `heading reported backwards for two fixes on the route - no reroute, no index change`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("cap inversé", nation) {
            drive(from = turn - 400.0, to = turn - 300.0, speedKmh = 30.0)
            tamper(2, "cap à 180°") { _, fix, _ -> fix.copy(bearingDeg = ((fix.bearingDeg ?: 0f) + 180f) % 360f) }
            drive(to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            assertSane(tl)
            assertNone("recul de l'indice de manœuvre", GuidanceLab.indexRegressions(tl), tl)
            assertEquals(1, nowAt(tl, nation, 3).size)
        }
    }

    @Test fun `stale fix delivered late, 40 m behind - nothing repeated, position resumes at once`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("fix en retard", nation) {
            drive(from = turn - 300.0, to = turn - 120.0, speedKmh = 30.0)
            tamper(1, "fix périmé (−40 m)") { _, _, at -> at(-40.0) }
            drive(to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            assertSane(tl)
            assertNone("recul de l'indice de manœuvre", GuidanceLab.indexRegressions(tl), tl)
            val late = tl.ticks.first { it.label.startsWith("fix périmé") }
            val next = tl.at(late.t + 1)
            assertEquals("position vraie dès le fix suivant\n$tl", next.trueAlongM!!, next.alongM!!, 3.0)
            assertEquals(1, nowAt(tl, nation, 3).size)
        }
    }

    @Test fun `same fix delivered twice - nothing said twice`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("fix en double", nation) {
            drive(from = turn - 300.0, to = turn - 60.0, speedKmh = 30.0)
            tamper(3, "fix répété") { _, _, at -> at(0.0) }
            drive(to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            assertSane(tl)
            assertEquals(1, nowAt(tl, nation, 3).size)
        }
    }

    @Test fun `position thrown 300 m ahead along the route for one fix - the skipped turn is still announced when really reached`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("saut de 300 m en avant", nation) {
            drive(from = turn - 300.0, to = turn - 200.0, speedKmh = 30.0)
            tamper(1, "saut +300 m") { _, _, at -> at(300.0) }
            drive(to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            val now = nowAt(tl, nation, 3)
            assertEquals("« maintenant » du virage dit une fois\n$tl", 1, now.size)
            assertTrue("dit à l'approche réelle (${now.single().second.toInt()} m)\n$tl", now.single().second in 0.0..80.0)
        }
    }

    @Test fun `no accuracy reported by the phone, 50 m assumed, for a whole approach - no reroute, no false certainty`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("précision absente", nation) {
            drive(from = turn - 300.0, to = turn - 5.0, speedKmh = 30.0, accuracyM = 50f)
        }) { tl ->
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            // Limite connue, consignée : sans précision exploitable, le « maintenant » n'est jamais dit (les annonces à distance le sont).
            tl.dump("CONSTAT : précision 50 m sur toute l'approche — « maintenant » dit ${nowAt(tl, nation, 3).size} fois, annonces : ${tl.spoken}")
        }
    }

    @Test fun `poor accuracy and 15 m of noise for 25 s on a motorway - no reroute, uncertainty shown, guidance resumes`() {
        val exit = startOf(motorway, 11)
        lab(GuidanceLab.scenario("GPS médiocre sur voie rapide", motorway) {
            drive(from = exit - 3_000.0, to = exit - 2_400.0, speedKmh = 110.0)
            drive(to = exit - 1_640.0, speedKmh = 110.0, noiseM = 15.0, accuracyM = 35f, label = "GPS médiocre")
            drive(to = exit - 15.0, speedKmh = 110.0)
        }) { tl ->
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            assertNone("recul de l'indice de manœuvre", GuidanceLab.indexRegressions(tl), tl)
            assertEquals("sortie dite une fois, après le retour d'un GPS exploitable\n$tl", 1, nowAt(tl, motorway, 11).size)
        }
    }

    @Test fun `GPS lost for 60 s on a motorway - no reroute at recovery, position true on the second fix, exit announced once`() {
        val exit = startOf(motorway, 11)
        lab(GuidanceLab.scenario("tunnel de 60 s", motorway) {
            drive(from = exit - 3_200.0, to = exit - 3_000.0, speedKmh = 110.0)
            gpsGap(60)
            drive(to = exit - 15.0, speedKmh = 110.0)
        }) { tl ->
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            val gap = tl.ticks.filter { it.fix == null && it.t > 0 }
            val second = tl.at(gap.last().t + 2)
            assertEquals("position vraie au second fix après le tunnel\n$tl", second.trueAlongM!!, second.alongM!!, 5.0)
            assertTrue("plus de badge GPS perdu\n$tl", !second.gpsLost)
            assertEquals(1, nowAt(tl, motorway, 11).size)
        }
    }
}
