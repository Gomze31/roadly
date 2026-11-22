package fr.roadly.core.navigation.lab

import fr.roadly.core.model.Route
import fr.roadly.core.navigation.GuidanceConstants
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.SpokenPhrases
import fr.roadly.core.navigation.junction.RealJunctions
import fr.roadly.core.navigation.lab.GuidanceLab.Scenario
import fr.roadly.core.navigation.lab.GuidanceLab.Timeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Laboratoire GPS, temps et précision (audit 1.0, NAV-2 à NAV-5) : heure du téléphone fausse ou qui saute, fix grossier du réseau
 * dans un tunnel, précision médiocre durable (canyon urbain), recalcul derrière une requête d'arrière-plan sur le serveur public.
 * Comme [GpsHostileConditionsLabTest] : positions **calculées** sur des tracés Valhalla réels, rejouées dans la vraie session de
 * guidage — des essais simulés, pas des essais sur route. Chronologies écrites dans `build/guidance-lab/`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GpsTimeAndAccuracyLabTest {
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

    private fun nowCount(tl: Timeline, route: Route, index: Int): Int = tl.count { GuidanceLab.isNow(it, route.maneuvers[index]) }

    // --- NAV-2 : heure du téléphone et temps GNSS ---

    @Test fun `phone clock 30 s ahead of GNSS time - fixes are used from the first second, no GPS lost`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("heure du téléphone en avance de 30 s", nation) {
            wallClockAheadMs = 30_000L
            drive(from = turn - 300.0, to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            val first = tl.at(1)
            assertEquals("position vraie dès le premier fix\n$tl", first.trueAlongM!!, first.alongM!!, 3.0)
            assertTrue("jamais de badge GPS perdu\n$tl", tl.ticks.none { it.gpsLost })
            assertEquals("« Signal GPS perdu » jamais dit\n$tl", 0, tl.count { it == SpokenPhrases.GPS_LOST })
            assertEquals("virage dit une fois\n$tl", 1, nowCount(tl, nation, 3))
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            // L'heure d'arrivée affichée reste une heure du téléphone : heure courante (en avance de 30 s) + durée restante.
            val g = tl.at(10).guiding!!
            assertEquals("heure d'arrivée sur l'heure du téléphone\n$tl", 10_000.0 + 30_000.0 + g.progress.remainingDurationS * 1000.0, g.etaEpochMs.toDouble(), 2.0)
        }
    }

    @Test fun `phone clock jumps 2 min forward during a 4 s GPS gap - no GPS lost, nothing said for it`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("heure du téléphone avancée de 2 min pendant un trou de 4 s", nation) {
            drive(from = turn - 300.0, to = turn - 200.0, speedKmh = 30.0)
            // Passage sous un pont : 4 s sans fix, pendant lesquelles le téléphone se remet à l'heure du réseau (+2 min).
            gpsGap(2)
            wallClockJump(120_000L)
            gpsGap(2)
            drive(to = turn - 5.0, speedKmh = 30.0)
        }) { tl ->
            assertTrue("jamais de badge GPS perdu\n$tl", tl.ticks.none { it.gpsLost })
            val gap = tl.ticks.filter { it.fix == null && it.t > 0 }
            assertEquals("position vraie au premier fix après le trou\n$tl", tl.at(gap.last().t + 1).trueAlongM!!, tl.at(gap.last().t + 1).alongM!!, 3.0)
            assertEquals("« Signal GPS perdu » jamais dit\n$tl", 0, tl.count { it == SpokenPhrases.GPS_LOST })
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertEquals("virage dit une fois\n$tl", 1, nowCount(tl, nation, 3))
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            assertNone("annonce répétée", GuidanceLab.repeatedAnnouncements(tl), tl)
        }
    }

    @Test fun `phone clock set 10 min back at a tunnel entrance - the GPS loss is still detected`() {
        val exit = startOf(motorway, 11)
        lab(GuidanceLab.scenario("heure du téléphone reculée de 10 min dans un tunnel", motorway) {
            drive(from = exit - 3_200.0, to = exit - 3_000.0, speedKmh = 110.0)
            gpsGap(2)
            wallClockJump(-600_000L)
            gpsGap(23)
            drive(to = exit - 15.0, speedKmh = 110.0)
        }) { tl ->
            val gap = tl.ticks.filter { it.fix == null && it.t > 0 }
            assertTrue("position extrapolée dès 4 s sans fix\n$tl", tl.at(gap.first().t + 4).extrapolated)
            assertTrue("badge GPS perdu après 10 s sans fix\n$tl", gap.filter { it.t > gap.first().t + 10 }.all { it.gpsLost })
            assertEquals("« Signal GPS perdu » dit une fois\n$tl", 1, tl.count { it == SpokenPhrases.GPS_LOST })
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertEquals("sortie dite une fois\n$tl", 1, nowCount(tl, motorway, 11))
        }
    }

    @Test fun `phone clock set 10 min back after a failed reroute - the reroute is still retried every 10 to 20 s`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("heure du téléphone reculée de 10 min après un recalcul en échec", nation) {
            drive(from = turn - 150.0, to = turn - 4.0, speedKmh = 30.0)
            routerFails()
            leave(seconds = 12, speedKmh = 30.0)
            wallClockJump(-600_000L)
            leave(seconds = 45, speedKmh = 30.0)
        }) { tl ->
            assertTrue("premier recalcul demandé avant le changement d'heure\n$tl", tl.requests.isNotEmpty() && tl.requests.first().t < tl.ticks.first { it.label == "quitte" }.t + 12)
            assertTrue("le recalcul est retenté après le changement d'heure (${tl.requests.size} demande(s))\n$tl", tl.requests.size >= 3)
            tl.requests.zipWithNext().forEach { (a, b) ->
                assertTrue("essais espacés de ${b.atMs - a.atMs} ms\n$tl", b.atMs - a.atMs in GuidanceConstants.REROUTE_MIN_INTERVAL_MS..25_000L)
            }
            assertEquals("« Recalcul de l'itinéraire. » dit une seule fois\n$tl", 1, tl.count { it == SpokenPhrases.REROUTING })
        }
    }

    // --- NAV-3 : fix grossier du réseau pendant une perte GPS ---

    @Test fun `coarse network fixes every 5 s in a 40 s tunnel - GPS stays lost, position does not move, guidance resumes after`() {
        val exit = startOf(motorway, 11)
        lab(GuidanceLab.scenario("tunnel de 40 s avec fix réseau grossiers", motorway) {
            drive(from = exit - 3_200.0, to = exit - 3_000.0, speedKmh = 110.0)
            // Dans le tunnel : aucun fix GNSS ; toutes les 5 s, une position « réseau » à 500 m de la chaussée, précise à 900 m, sans vitesse ni cap.
            tamper(40, "tunnel") { i, fix, _ ->
                if (i % 5 == 4) fix.copy(point = GuidanceLab.offset(fix.point, 45.0, 500.0), accuracyM = 900f, speedMps = null, bearingDeg = null) else null
            }
            drive(to = exit - 15.0, speedKmh = 110.0)
        }) { tl ->
            val tunnel = tl.ticks.filter { it.label == "tunnel" }
            val coarse = tunnel.filter { it.fix != null }
            assertEquals(8, coarse.size)
            assertTrue("badge GPS perdu après 10 s de tunnel, fix grossiers compris\n$tl", tunnel.filter { it.t > tunnel.first().t + 10 }.all { it.gpsLost })
            assertEquals("« Signal GPS perdu » dit une fois\n$tl", 1, tl.count { it == SpokenPhrases.GPS_LOST })
            // Un fix grossier ne déplace pas la position guidée (ni en arrière, ni de côté) et ne relance pas l'extrapolation.
            for (c in coarse) {
                val before = tl.at(c.t - 1)
                assertTrue("t=${c.t} : position guidée reculée par un fix grossier\n$tl", c.alongM!! >= before.alongM!!)
                assertTrue("t=${c.t} : écart au tracé mesuré sur un fix grossier\n$tl", c.guiding!!.progress.offRouteM < 40.0)
            }
            val frozen = tunnel.filter { it.t > tunnel.first().t + 10 }.map { it.alongM!! }
            assertEquals("position figée après 10 s (plafond d'extrapolation)\n$tl", frozen.first(), frozen.last(), 0.5)
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertNone("recul de l'indice de manœuvre", GuidanceLab.indexRegressions(tl), tl)
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            val second = tl.at(tunnel.last().t + 2)
            assertEquals("position vraie au second fix après le tunnel\n$tl", second.trueAlongM!!, second.alongM!!, 5.0)
            assertTrue("plus de badge GPS perdu\n$tl", !second.gpsLost)
            assertEquals("sortie dite une fois\n$tl", 1, nowCount(tl, motorway, 11))
        }
    }

    // --- NAV-4 : précision médiocre durable (canyon urbain) ---

    @Test fun `urban canyon, 45 m accuracy throughout - a wrong turn still gets a reroute, only once clearly off the route`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("canyon urbain, mauvais virage", nation) {
            drive(from = turn - 300.0, to = turn - 4.0, speedKmh = 30.0, accuracyM = 45f)
            routerFails()
            leave(seconds = 60, speedKmh = 30.0, accuracyM = 45f)
        }) { tl ->
            assertTrue("un recalcul finit par être demandé\n$tl", tl.requests.isNotEmpty())
            val first = tl.requests.first()
            val off = tl.at(first.t).guiding!!.progress.offRouteM
            // Règle dégradée : l'écart doit dépasser le seuil habituel de 100 m une fois la précision (45 m) retranchée.
            assertTrue("recalcul demandé à ${off.toInt()} m du tracé : trop tôt pour une précision de 45 m\n$tl", off > 145.0)
            assertTrue("recalcul demandé à ${off.toInt()} m du tracé : trop tard\n$tl", off < 260.0)
            assertEquals("« Recalcul de l'itinéraire. » dit une fois, à la demande\n$tl", listOf(first.t), tl.secondsOf { it == SpokenPhrases.REROUTING })
            assertTrue("aucun recalcul affiché avant la demande\n$tl", tl.ticks.filter { it.t < first.t }.none { it.rerouting })
        }
    }

    @Test fun `urban canyon, 45 m accuracy with 20 m of noise and two 130 m jumps on the route - no reroute`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("canyon urbain, bruit et sauts sur le tracé", nation) {
            drive(from = turn - 400.0, to = turn - 250.0, speedKmh = 30.0, noiseM = 20.0, accuracyM = 45f)
            gpsJump(lateralM = 130.0, seconds = 2, accuracyM = 45f)
            drive(to = turn - 120.0, speedKmh = 30.0, noiseM = 20.0, accuracyM = 45f)
            gpsJump(lateralM = -130.0, seconds = 2, accuracyM = 45f)
            drive(to = turn - 5.0, speedKmh = 30.0, noiseM = 20.0, accuracyM = 45f)
        }) { tl ->
            assertEquals("aucun recalcul\n$tl", 0, tl.requests.size)
            assertTrue("aucun recalcul affiché\n$tl", tl.ticks.none { it.rerouting })
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
        }
    }

    @Test fun `urban canyon, 45 m accuracy to the destination - arrival is declared, and not before the last metres`() {
        val total = RouteTracker(nation).totalGeometryM
        lab(GuidanceLab.scenario("canyon urbain, arrivée", nation) {
            drive(from = total - 250.0, toEndMinus = 3.0, speedKmh = 30.0, accuracyM = 45f)
            stop(seconds = 20, jitterM = 10.0, accuracyM = 45f)
        }) { tl ->
            val arrived = tl.ticks.firstOrNull { it.state is NavigationState.Arrived }
            assertTrue("l'arrivée est déclarée\n$tl", arrived != null)
            assertTrue("arrivée déclarée à ${(total - arrived!!.trueAlongM!!).toInt()} m de la destination\n$tl", total - arrived.trueAlongM!! <= 25.0)
            assertEquals("phrase d'arrivée dite une fois\n$tl", 1, nowCount(tl, nation, nation.maneuvers.lastIndex))
        }
    }

    // --- NAV-5 : recalcul derrière une requête d'arrière-plan sur le serveur public ---

    @Test fun `public server busy with a 15 s background request - the reroute is sent at the next slot and answered`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("recalcul pendant une requête d'arrière-plan", nation) {
            publicServer = true
            drive(from = turn - 150.0, to = turn - 4.0, speedKmh = 30.0)
            routerAnswers { request -> GuidanceLab.tailRoute(nation, turn + 150.0, request.origin) }
            leave(seconds = 4, speedKmh = 30.0)
            // Le profil du tracé (ou un carrefour) part vers le serveur et met 15 s à répondre : c'est le délai maximal d'un appel.
            backgroundRequest(seconds = 15)
            leave(seconds = 40, speedKmh = 30.0, untilServed = true)
        }) { tl ->
            val starts = tl.serverStarts
            assertTrue("une requête d'arrière-plan est en vol quand le recalcul est demandé\n$tl", starts.first().kind == "arrière-plan" && tl.requests.first().atMs in starts.first().atMs..starts.first().atMs + 15_000L)
            assertEquals("un seul recalcul demandé, et servi\n$tl", 1 to 1, tl.requests.size to tl.served.size)
            val waitedMs = tl.served.single().atMs - tl.requests.single().atMs
            assertTrue("recalcul servi ${waitedMs} ms après la demande\n$tl", waitedMs <= 1_000L + tl.scenario.routerLatencyMs)
            assertEquals("« Recalcul impossible » jamais dit\n$tl", 0, tl.count { it == SpokenPhrases.REROUTE_FAILED })
            // Débit du serveur public respecté : jamais deux départs à moins d'une seconde.
            starts.zipWithNext().forEach { (a, b) -> assertTrue("départs à ${b.atMs - a.atMs} ms d'écart\n$tl", b.atMs - a.atMs >= 1_000L) }
            // La requête d'arrière-plan interrompue repart après le recalcul.
            assertEquals("$tl", listOf("arrière-plan", "recalcul", "arrière-plan"), starts.map { it.kind })
        }
    }
}
