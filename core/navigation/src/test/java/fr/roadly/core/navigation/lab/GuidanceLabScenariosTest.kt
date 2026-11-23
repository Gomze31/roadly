package fr.roadly.core.navigation.lab

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.GuidanceConstants
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.ManeuverSide
import fr.roadly.core.navigation.PositioningPolicy
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.SpokenPhrases
import fr.roadly.core.navigation.bearing
import fr.roadly.core.navigation.junction.RealJunctions
import fr.roadly.core.navigation.lab.GuidanceLab.Scenario
import fr.roadly.core.navigation.lab.GuidanceLab.Timeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.Locale

/**
 * Scénarios de conduite du laboratoire de guidage ([GuidanceLab]) : chacun est décrit une fois (positions, précision, vitesse),
 * rejoué dans la vraie session, puis jugé sur le guidage attendu, la voix attendue (ordre, une seule fois, jamais en contradiction
 * avec l'état affiché à la même seconde) et les transitions / la récupération attendues.
 *
 * Tracés : fixtures Valhalla réelles ([RealJunctions]) — `motorway` (Toulouse, A620, sorties 31b et 19, 4 ronds-points),
 * `muret` (2 ronds-points), `nation` et `etoile` (Paris, 6e sortie). Les indices de manœuvre cités sont ceux de ces fixtures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceLabScenariosTest {
    private val motorway = RealJunctions.route("motorway")
    private val muret = RealJunctions.route("muret")
    private val nation = RealJunctions.route("nation")
    private val etoile = RealJunctions.route("etoile")

    private fun startOf(route: Route, index: Int): Double = RouteTracker(route).maneuverStartM[index]

    /** Rejoue [scenario], écrit sa chronologie dans `build/guidance-lab/`, puis applique [check]. */
    private fun lab(scenario: Scenario, check: (Timeline) -> Unit) = runTest {
        val tl = GuidanceLab.run(this, scenario)
        tl.dump()
        check(tl)
    }

    private fun assertNone(what: String, problems: List<String>, tl: Timeline) =
        assertTrue("${tl.scenario.name} — $what : $problems\n$tl", problems.isEmpty())

    /** Invariants communs : voix cohérente avec l'écran, aucune répétition, aucun clignotement de mode, indice jamais en recul. */
    private fun assertClean(tl: Timeline, ignoring: Set<GuidanceMode> = emptySet()) {
        assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
        assertNone("annonce répétée", GuidanceLab.repeatedAnnouncements(tl), tl)
        assertNone("oscillation de mode", GuidanceLab.modeOscillations(tl, ignoring), tl)
        assertNone("recul de l'indice de manœuvre", GuidanceLab.indexRegressions(tl), tl)
    }

    private fun assertNoReroute(tl: Timeline) {
        assertEquals("${tl.scenario.name} — aucun recalcul attendu\n$tl", 0, tl.requests.size)
        assertTrue("${tl.scenario.name} — l'état n'a jamais montré de recalcul\n$tl", tl.ticks.none { it.rerouting || it.mode == GuidanceMode.REROUTING })
    }

    /** Secondes où l'annonce « maintenant » de la manœuvre [index] de [route] a été dite. */
    private fun nowSeconds(tl: Timeline, route: Route, index: Int): List<Int> =
        tl.ticks.filter { it.routeId == route.id && it.nextIndex == index && it.spoken.any { s -> GuidanceLab.isNow(s, route.maneuvers[index]) } }.map { it.t }

    /** Secondes où une annonce à distance (« Dans X, … ») de la manœuvre [index] a été dite. */
    private fun distanceSeconds(tl: Timeline, route: Route, index: Int): List<Int> =
        tl.ticks.filter { it.routeId == route.id && it.nextIndex == index && it.spoken.any { s -> GuidanceLab.isDistance(s, route.maneuvers[index]) } }.map { it.t }

    private fun assertNowOnce(tl: Timeline, route: Route, index: Int): Int {
        val seconds = nowSeconds(tl, route, index)
        assertEquals("${tl.scenario.name} — « ${GuidanceLab.nowStem(route.maneuvers[index])} » dit une seule fois (t=$seconds)\n$tl", 1, seconds.size)
        return seconds.single()
    }

    // --- 1. Route droite ---

    @Test fun `straight motorway cruise - nothing is said and the screen stays in CRUISE`() {
        val exit = startOf(motorway, 11)
        lab(GuidanceLab.scenario("croisière autoroute", motorway) { drive(from = 5_000.0, to = exit - 3_500.0, speedKmh = 110.0) }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val driving = tl.ticks.drop(1)
            assertTrue("au moins 3 minutes de croisière", driving.size > 180)
            assertEquals("rien n'est dit en croisière\n$tl", emptyList<String>(), driving.flatMap { it.spoken })
            assertEquals("mode constant\n$tl", listOf(GuidanceMode.CRUISE), driving.mapNotNull { it.mode }.distinct())
            assertEquals("manœuvre visée constante\n$tl", listOf(11), driving.mapNotNull { it.nextIndex }.distinct())
            assertTrue(driving.none { it.gpsLost || it.extrapolated })
        }
    }

    // --- 2. Virages urbains enchaînés ---

    @Test fun `urban turns in succession - each turn once, in order, the close ones chained with puis`() {
        // Nation : droite (Picpus), 224 m, gauche (Dorian), 126 m, droite (Bourdan), 121 m, gauche (Saint-Charles).
        lab(GuidanceLab.scenario("virages urbains enchaînés", nation) { drive(from = startOf(nation, 3) - 200.0, to = startOf(nation, 6) + 35.0, speedKmh = 30.0) }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val now = (3..6).map { assertNowOnce(tl, nation, it) }
            assertEquals("virages annoncés dans l'ordre\n$tl", now.sorted(), now)
            // Chaque virage est annoncé avant d'y être : la distance vraie restante est positive.
            (3..6).forEach { k ->
                val a = tl.at(now[k - 3]).trueAlongM!!
                assertTrue("virage $k dit avant le virage (à ${startOf(nation, k) - a} m)\n$tl", a < startOf(nation, k))
            }
            // Dorian → Bourdan (126 m) et Bourdan → Saint-Charles (121 m) : enchaînés.
            val dorian = tl.spoken.single { GuidanceLab.isNow(it, nation.maneuvers[4]) }
            assertTrue("« $dorian » enchaîne le virage suivant", dorian.contains(", puis tournez à droite dans Rue Pierre Bourdan"))
            val bourdan = tl.spoken.single { GuidanceLab.isNow(it, nation.maneuvers[5]) }
            assertTrue("« $bourdan » enchaîne le virage suivant", bourdan.contains(", puis tournez à gauche dans Square Saint-Charles"))
            // Picpus → Dorian (224 m) : pas d'enchaînement.
            assertTrue(tl.spoken.single { GuidanceLab.isNow(it, nation.maneuvers[3]) }.none { it == ',' })
            // L'écran dit la même chose : enchaînement signalé pendant l'approche de Dorian et de Bourdan.
            listOf(4, 5).forEach { k -> assertTrue("mode COMPLEX à l'approche du virage $k\n$tl", GuidanceMode.COMPLEX in tl.modesWhileNext(k)) }
        }
    }

    // --- 3. Ronds-points ---

    @Test fun `simple roundabout, first exit - entry then exit, once each, the diagram goes approach, inside, exited`() {
        // Toulouse, rond-point Frédéric Lubin-Lebrère : 1re sortie, 8 m d'anneau.
        val enter = 6
        lab(GuidanceLab.scenario("rond-point simple 1re sortie", motorway, topology = "motorway") {
            drive(from = startOf(motorway, enter) - 105.0, to = startOf(motorway, enter + 1) + 140.0, speedKmh = 25.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val entry = assertNowOnce(tl, motorway, enter)
            val exit = assertNowOnce(tl, motorway, enter + 1)
            assertTrue("entrée ($entry) avant sortie ($exit)\n$tl", entry < exit)
            assertTrue(tl.spoken.any { it.contains("1er sortie") })
            assertEquals("phases du schéma\n$tl", listOf(RoundaboutPhase.APPROACH, RoundaboutPhase.INSIDE, RoundaboutPhase.EXITED, null), tl.roundaboutPhases().dropWhile { it == null })
            assertEquals(listOf(GuidanceMode.ROUNDABOUT_APPROACH), tl.modesWhileNext(enter).filter { it != GuidanceMode.CRUISE })
        }
    }

    private fun etoileScenario(name: String, topology: String?) = GuidanceLab.scenario(name, etoile, topology) {
        drive(from = startOf(etoile, 1) - 250.0, to = startOf(etoile, 2) + 90.0, speedKmh = 28.0)
    }

    @Test fun `complex roundabout, etoile 6th exit with topology - countdown to zero, leave said once when the exit is next`() {
        lab(etoileScenario("Étoile 6e sortie avec topologie", "etoile")) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            assertNowOnce(tl, etoile, 1)
            assertTrue(tl.spoken.any { it.contains("6ème sortie") })
            val exitSecond = assertNowOnce(tl, etoile, 2)
            assertTrue("la sortie est dite quand la prochaine sortie est la bonne\n$tl", tl.at(exitSecond).live?.exitIsNext == true)
            assertEquals("phases du schéma\n$tl", listOf(RoundaboutPhase.APPROACH, RoundaboutPhase.INSIDE, RoundaboutPhase.EXITED, null), tl.roundaboutPhases().dropWhile { it == null })
            val remaining = tl.ticks.mapNotNull { it.live?.exitsRemaining }
            assertEquals("décompte jamais croissant\n$tl", remaining.sortedDescending(), remaining)
            assertEquals("5 sorties à laisser à l'entrée", 5, remaining.first())
            assertEquals(0, remaining.last())
            assertTrue("au moins 4 paliers vus à 1 Hz : ${remaining.distinct()}", remaining.distinct().size >= 4)
            tl.ticks.filter { it.live?.phase == RoundaboutPhase.INSIDE }.forEach { assertEquals("t=${it.t}\n$tl", GuidanceMode.ROUNDABOUT, it.mode) }
            // « Quittez » n'est jamais dit avant que la 5e sortie soit derrière.
            assertEquals(5, tl.at(exitSecond).live?.exitsPassed)
        }
    }

    @Test fun `complex roundabout, etoile 6th exit without topology - no countdown is claimed, the exit is still said once`() {
        lab(etoileScenario("Étoile 6e sortie sans topologie", null)) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            assertNowOnce(tl, etoile, 1)
            assertNowOnce(tl, etoile, 2)
            val lives = tl.ticks.mapNotNull { it.live }
            assertTrue("le schéma est montré\n$tl", lives.size > 5)
            assertTrue("aucun décompte sans topologie\n$tl", lives.all { it.exitsRemaining == null && !it.exitIsNext })
            assertEquals("phases du schéma\n$tl", listOf(RoundaboutPhase.APPROACH, RoundaboutPhase.INSIDE, RoundaboutPhase.EXITED, null), tl.roundaboutPhases().dropWhile { it == null })
        }
    }

    @Test fun `two roundabouts in a row - each entry and each exit once, the second is not mixed up with the first`() {
        // Muret : place du Languedoc (2e sortie) puis, 238 m plus loin, un second rond-point (3e sortie).
        lab(GuidanceLab.scenario("deux ronds-points de suite", muret, topology = "muret") {
            drive(from = startOf(muret, 1) + 12.0, to = startOf(muret, 6) - 12.0, speedKmh = 24.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val order = listOf(2, 3, 4, 5).map { assertNowOnce(tl, muret, it) }
            assertEquals("entrée 1, sortie 1, entrée 2, sortie 2\n$tl", order.sorted(), order)
            assertTrue(tl.spoken.any { it.contains("2e sortie") } && tl.spoken.any { it.contains("3ème sortie") })
            for (enter in listOf(2, 4)) {
                val ticks = tl.ticks.filter { it.nextIndex == enter || it.nextIndex == enter + 1 }
                val remaining = ticks.mapNotNull { it.live?.exitsRemaining }
                assertEquals("rond-point $enter : décompte jamais croissant\n$tl", remaining.sortedDescending(), remaining)
                assertTrue("rond-point $enter : l'anneau a été vu\n$tl", ticks.any { it.live?.phase == RoundaboutPhase.INSIDE })
            }
            // Entre les deux, le schéma du premier disparaît avant que celui du second apparaisse.
            val phases = tl.roundaboutPhases().dropWhile { it == null }
            assertEquals("phases\n$tl", 2, phases.count { it == RoundaboutPhase.APPROACH })
            assertEquals("phases\n$tl", 2, phases.count { it == RoundaboutPhase.INSIDE })
        }
    }

    // --- 4. Sortie d'autoroute ---

    /** Sortie 19 de l'A620 (manœuvre 11) abordée à [speedKmh] ; rend le préavis (s) de la dernière annonce. */
    private fun motorwayExit(speedKmh: Int, minLeadS: Double) {
        val exit = 11
        val at = startOf(motorway, exit)
        val keepRight = PositioningPolicy.phrase(motorway.maneuvers[exit])!!
        lab(GuidanceLab.scenario("sortie autoroute $speedKmh km-h", motorway) { drive(from = at - 2_600.0, to = at - 15.0, speedKmh = speedKmh.toDouble()) }) { tl ->
            val lead = GuidanceLab.lastAnnouncementLeadS(tl, exit)
            tl.dump(String.format(Locale.ROOT, "MESURE : dernière annonce de la sortie %.2f s avant la sortie à %d km/h", lead ?: Double.NaN, speedKmh))
            assertClean(tl)
            assertNoReroute(tl)
            val far = distanceSeconds(tl, motorway, exit)
            assertEquals("anticipation puis rappel proche, une fois chacun (t=$far)\n$tl", 2, far.size)
            val place = tl.secondsOf { it == keepRight }
            assertEquals("placement dit une seule fois\n$tl", 1, place.size)
            val now = assertNowOnce(tl, motorway, exit)
            assertTrue("anticipation (${far[0]}) < placement (${place[0]}) < rappel (${far[1]}) < manœuvre ($now)\n$tl", far[0] < place[0] && place[0] < far[1] && far[1] < now)
            assertEquals("la sortie est la dernière chose dite\n$tl", now, tl.ticks.last { it.spoken.isNotEmpty() }.t)
            assertEquals("croisière puis sortie, sans retour\n$tl", listOf(GuidanceMode.CRUISE, GuidanceMode.EXIT), tl.modesWhileNext(exit))
            // Le mode sortie est ouvert au moins 20 s avant la sortie.
            val exitModeFrom = tl.ticks.first { it.mode == GuidanceMode.EXIT }
            assertTrue("mode EXIT ouvert ${exitModeFrom.guiding!!.situation.secondsToManeuver} s avant\n$tl", exitModeFrom.guiding!!.situation.secondsToManeuver!! >= 20.0)
            assertTrue("dernière annonce au moins $minLeadS s avant la sortie (mesuré : $lead s)\n$tl", lead != null && lead >= minLeadS)
        }
    }

    // Préavis mesurés de la dernière annonce (« Prenez la sortie 19 vers … ») : 3,00 s à 90 (75 m), 3,09 s à 110 (94 m), 3,00 s à 130 (108 m).
    // Le seuil est de 4 s de trajet, échantillonné à 1 Hz : le plancher garanti est donc de 3 s (tolérance d'arrondi : 2,95 s).
    @Test fun `motorway exit at 90 km h - anticipation, placement, manoeuvre, each once, with lead time`() = motorwayExit(90, minLeadS = 2.95)
    @Test fun `motorway exit at 110 km h - anticipation, placement, manoeuvre, each once, with lead time`() = motorwayExit(110, minLeadS = 2.95)
    @Test fun `motorway exit at 130 km h - anticipation, placement, manoeuvre, each once, with lead time`() = motorwayExit(130, minLeadS = 2.95)

    // --- 5. Manœuvres rapprochées sur voie rapide ---

    @Test fun `slight right onto the slip road followed 230 m later by exit 31b - both said once, in order, before each point`() {
        // Le jeu de données réel ne contient pas de KEEP_* suivi de près d'une sortie : la paire la plus proche est
        // « Serrez à droite dans A 621 » (8) puis « Prenez la sortie 31b » (9), à 230 m.
        lab(GuidanceLab.scenario("bretelle puis sortie 31b", motorway) {
            // Approche réelle : on sort du rond-point Lubin-Lebrère (manœuvres 6 et 7) 177 m avant la bretelle.
            drive(from = startOf(motorway, 6) - 60.0, to = startOf(motorway, 7) + 12.0, speedKmh = 25.0)
            drive(to = startOf(motorway, 9) + 150.0, speedKmh = 60.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val first = assertNowOnce(tl, motorway, 8)
            assertTrue("la bretelle est dite avant d'y être\n$tl", tl.at(first).trueAlongM!! < startOf(motorway, 8))
            val second = assertNowOnce(tl, motorway, 9)
            assertTrue(first < second)
            assertTrue("la sortie est dite avant d'y être\n$tl", tl.at(second).trueAlongM!! < startOf(motorway, 9))
            assertTrue("mode EXIT à l'approche de la sortie\n$tl", GuidanceMode.EXIT in tl.modesWhileNext(9))
            val lead = GuidanceLab.lastAnnouncementLeadS(tl, 9)!!
            assertTrue("sortie 31b dite au moins 3 s avant (mesuré : $lead s)\n$tl", lead >= 3.0)
        }
    }

    @Test fun `exit 19 followed 600 m later by a fork - exit then keep left, the fork gets its own FORK mode`() {
        val exit = startOf(motorway, 11)
        val fork = startOf(motorway, 12)
        lab(GuidanceLab.scenario("sortie 19 puis bifurcation", motorway) {
            drive(from = exit - 420.0, to = exit, speedKmh = 110.0)
            drive(to = fork + 120.0, speedKmh = 70.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val a = assertNowOnce(tl, motorway, 11)
            val b = assertNowOnce(tl, motorway, 12)
            assertTrue(a < b)
            assertTrue("la bifurcation est dite avant d'y être\n$tl", tl.at(b).trueAlongM!! < fork)
            assertEquals("bifurcation : croisière tant qu'elle est à plus de 500 m, puis FORK\n$tl", listOf(GuidanceMode.CRUISE, GuidanceMode.FORK), tl.modesWhileNext(12))
            assertTrue(tl.ticks.filter { it.mode == GuidanceMode.FORK }.all { it.guiding!!.situation.side == ManeuverSide.LEFT })
            assertEquals("anticipation et rappel de la bifurcation\n$tl", 2, distanceSeconds(tl, motorway, 12).size)
        }
    }

    @Test fun `guidance starting 160 m before a manoeuvre does not say the same sentence twice in two seconds`() {
        lab(GuidanceLab.scenario("départ à 160 m d'une manœuvre", motorway) { drive(from = startOf(motorway, 8) - 160.0, to = startOf(motorway, 8) - 20.0, speedKmh = 70.0) }) { tl ->
            assertNone("annonce répétée", GuidanceLab.repeatedAnnouncements(tl), tl)
        }
    }

    // --- 6. Pertes GPS ---

    @Test fun `GPS lost 8 s before an urban turn - no now on a guess, now said once on the first real fix`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("perte GPS 8 s avant virage", nation) {
            drive(from = turn - 200.0, to = turn - 90.0, speedKmh = 30.0)
            gpsGap(8)
            drive(to = turn + 60.0, speedKmh = 30.0)
        }) { tl ->
            assertClean(tl, ignoring = setOf(GuidanceMode.GPS_UNCERTAIN))
            assertNoReroute(tl)
            val gap = tl.ticks.filter { it.fix == null && it.t > 0 }
            assertEquals(8, gap.size)
            assertTrue("aucun « maintenant » pendant le trou\n$tl", gap.none { t -> t.spoken.any { GuidanceLab.isNow(it, nation.maneuvers[3]) } })
            assertTrue("la position est extrapolée pendant le trou\n$tl", gap.count { it.extrapolated } >= 4)
            assertTrue("l'indice n'avance pas sur une position devinée\n$tl", gap.all { it.nextIndex == 3 })
            val now = assertNowOnce(tl, nation, 3)
            assertEquals("« maintenant » dit au premier fix réel après le trou\n$tl", gap.last().t + 1, now)
            assertTrue("…encore avant le virage\n$tl", tl.at(now).trueAlongM!! < turn)
            assertTrue(tl.ticks.filter { it.t >= now }.none { it.extrapolated || it.gpsLost })
        }
    }

    @Test fun `GPS lost 20 s before a motorway exit - badge after 10 s, nothing false said, guidance resumes without duplicate`() {
        val exit = 11
        val at = startOf(motorway, exit)
        lab(GuidanceLab.scenario("perte GPS 20 s avant sortie", motorway) {
            drive(from = at - 2_300.0, to = at - 900.0, speedKmh = 110.0)
            gpsGap(20)
            drive(to = at - 15.0, speedKmh = 110.0)
        }) { tl ->
            assertClean(tl, ignoring = setOf(GuidanceMode.GPS_UNCERTAIN))
            assertNoReroute(tl)
            val gap = tl.ticks.filter { it.fix == null && it.t > 0 }
            assertEquals(20, gap.size)
            assertTrue("aucun « maintenant » pendant le trou\n$tl", gap.none { t -> t.spoken.any { GuidanceLab.isNow(it, motorway.maneuvers[exit]) } })
            // Badge « GPS perdu » après 10 s sans fix, et mode GPS_UNCERTAIN.
            val lost = gap.filter { it.gpsLost }
            assertEquals("badge à partir de la 11e seconde\n$tl", (gap.first().t + 10..gap.last().t).toList(), lost.map { it.t })
            assertTrue(lost.all { it.mode == GuidanceMode.GPS_UNCERTAIN })
            // Écran éteint, le badge ne se voit pas : la perte est dite, une seule fois, à la seconde où le badge apparaît.
            assertEquals("« Signal GPS perdu. » dit une fois, avec le badge\n$tl", listOf(lost.first().t), tl.secondsOf { it == SpokenPhrases.GPS_LOST })
            // L'extrapolation est plafonnée à 150 m : la distance affichée ne descend pas plus bas pendant le trou.
            val before = tl.at(gap.first().t - 1).distanceM!!
            assertTrue("extrapolation plafonnée\n$tl", gap.all { before - it.distanceM!! <= GuidanceConstants.EXTRAPOLATION_MAX_M + 1.0 })
            // Reprise : dès le premier fix, plus de badge, la distance vraie, le mode sortie en 3 s au plus.
            val back = tl.at(gap.last().t + 1)
            assertTrue("reprise au premier fix\n$tl", !back.gpsLost && !back.extrapolated)
            assertEquals("distance vraie à la reprise\n$tl", at - back.trueAlongM!!, back.distanceM!!, 3.0)
            assertTrue("mode EXIT retrouvé en 3 s\n$tl", (0..3).any { tl.at(back.t + it).mode == GuidanceMode.EXIT })
            assertEquals("anticipation et rappel, une fois chacun\n$tl", 2, distanceSeconds(tl, motorway, exit).size)
            val now = assertNowOnce(tl, motorway, exit)
            assertTrue(now > back.t)
        }
    }

    // --- 7. GPS trompeur ---

    @Test fun `GPS jump of 80 m sideways for one fix - no reroute, no state oscillation, nothing said`() {
        val exit = 11
        val at = startOf(motorway, exit)
        lab(GuidanceLab.scenario("saut GPS 80 m", motorway) {
            drive(from = at - 900.0, to = at - 420.0, speedKmh = 110.0)
            gpsJump(lateralM = 80.0)
            drive(to = at - 160.0, speedKmh = 110.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val jump = tl.ticks.single { it.label == "saut GPS" }
            assertEquals("rien n'est dit sur le saut\n$tl", emptyList<String>(), jump.spoken)
            assertEquals(listOf(exit), tl.ticks.drop(1).mapNotNull { it.nextIndex }.distinct())
            assertEquals("le mode ne bouge pas\n$tl", tl.at(jump.t - 1).mode, jump.mode)
            assertEquals(tl.at(jump.t - 1).mode, tl.at(jump.t + 1).mode)
            // La distance affichée continue de décroître : ni retour en arrière ni bond.
            val distances = tl.ticks.drop(1).map { it.distanceM!! }
            distances.zipWithNext().forEachIndexed { i, (a, b) -> assertTrue("distance monotone à t=${i + 2} ($a → $b)\n$tl", b < a && a - b < 80.0) }
        }
    }

    @Test fun `parallel road 30 m aside for 10 s - no reroute, guidance unchanged`() {
        val exit = 11
        val at = startOf(motorway, exit)
        lab(GuidanceLab.scenario("route parallèle 30 m", motorway) {
            drive(from = at - 1_000.0, to = at - 760.0, speedKmh = 110.0)
            drive(to = at - 454.0, speedKmh = 110.0, lateralM = 30.0, label = "parallèle")
            drive(to = at - 160.0, speedKmh = 110.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            val aside = tl.ticks.filter { it.label.startsWith("parall") }
            assertEquals(10, aside.size)
            assertTrue("écart mesuré ≈ 30 m\n$tl", aside.all { it.guiding!!.progress.offRouteM in 25.0..35.0 })
            assertEquals(listOf(exit), tl.ticks.drop(1).mapNotNull { it.nextIndex }.distinct())
            assertEquals("croisière puis sortie, sans retour\n$tl", listOf(GuidanceMode.CRUISE, GuidanceMode.EXIT), tl.modesWhileNext(exit))
            val distances = tl.ticks.drop(1).map { it.distanceM!! }
            distances.zipWithNext().forEach { (a, b) -> assertTrue("distance monotone ($a → $b)\n$tl", b < a) }
        }
    }

    // --- 8. Bouchon ---

    @Test fun `slow crawl and stop-and-go before a turn - nothing repeated, no mode flicker, now said once`() {
        val turn = startOf(nation, 3)
        lab(GuidanceLab.scenario("bouchon avant virage", nation) {
            drive(from = turn - 130.0, to = turn - 70.0, speedKmh = 8.0)
            stop(seconds = 10, jitterM = 3.0)
            drive(to = turn - 42.0, speedKmh = 6.0)
            stop(seconds = 8, jitterM = 3.0)
            drive(to = turn - 25.0, speedKmh = 5.0)
            stop(seconds = 6, jitterM = 3.0)
            drive(to = turn + 45.0, speedKmh = 12.0)
        }) { tl ->
            assertClean(tl)
            assertNoReroute(tl)
            assertNowOnce(tl, nation, 3)
            assertTrue("au plus deux annonces à distance (anticipation, rappel)\n$tl", distanceSeconds(tl, nation, 3).size <= 2)
            assertEquals("un seul mode pendant toute l'approche\n$tl", listOf(GuidanceMode.PREPARE), tl.modesWhileNext(3))
            assertTrue("rien n'est dit à l'arrêt\n$tl", tl.ticks.filter { it.label == "arrêt" }.all { it.spoken.isEmpty() })
            assertTrue(tl.ticks.none { it.gpsLost || it.extrapolated })
        }
    }

    // --- 9. Sortie d'itinéraire ---

    @Test fun `wrong turn - reroute requested once per interval from the vehicle position, no manoeuvre announced meanwhile`() {
        lab(wrongTurnWithoutNetwork()) { tl ->
            assertTrue("au moins 3 demandes en 45 s\n$tl", tl.requests.size >= 3)
            tl.requests.zipWithNext().forEach { (a, b) ->
                assertTrue("au plus un recalcul par intervalle de 10 s (t=${a.t} puis t=${b.t})\n$tl", b.atMs - a.atMs >= GuidanceConstants.REROUTE_MIN_INTERVAL_MS)
            }
            // Détection : le premier recalcul part au plus 15 s (125 m) après le virage manqué.
            val left = tl.ticks.first { it.label == "quitte" }.t
            val delay = tl.requests.first().t - left
            tl.dump("MESURE : virage urbain manqué à 30 km/h, recalcul demandé $delay s après (écart au tracé alors : ${tl.at(tl.requests.first().t).guiding?.progress?.offRouteM?.toInt()} m)")
            assertTrue("détecté en $delay s\n$tl", delay <= 15)
            assertEquals("« Recalcul de l'itinéraire. » dit à la première demande\n$tl", tl.requests.first().t, tl.secondsOf { it == SpokenPhrases.REROUTING }.first())
            assertTrue("l'état montre le recalcul à chaque demande\n$tl", tl.requests.all { tl.at(it.t).rerouting && tl.at(it.t).mode == GuidanceMode.REROUTING })
            // Chaque demande part de la position du véhicule, vers la destination d'origine, avec son cap.
            tl.requests.forEach { r ->
                assertEquals(nation.geometry.last(), r.request.destination)
                assertTrue(r.request.origin.distanceTo(tl.at(r.t).truePoint) < 1.0)
                assertEquals(tl.at(r.t).fix!!.bearingDeg, r.request.originHeadingDeg)
            }
            // Sans réponse du routeur, l'ancien tracé reste affiché mais aucune de ses manœuvres n'est annoncée.
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl), tl)
            val after = tl.ticks.filter { it.t > tl.requests.first().t }.flatMap { it.spoken }
            assertEquals("aucune manœuvre annoncée hors itinéraire ; l'échec du recalcul est dit une seule fois\n$tl", listOf(SpokenPhrases.REROUTE_FAILED), after)
            // Entre deux essais, l'écran reste en recalcul : il ne remontre pas la manœuvre du tracé abandonné comme si elle était suivie.
            val pending = tl.ticks.filter { it.t >= tl.requests.first().t && it.fix != null }
            assertTrue("recalcul affiché sans interruption jusqu'à la fin\n$tl", pending.all { it.rerouting && it.mode == GuidanceMode.REROUTING })
            assertNone("recul de l'indice de manœuvre", GuidanceLab.indexRegressions(tl), tl)
        }
    }

    /** Nation : le conducteur continue tout droit au lieu de tourner à droite dans la rue de Picpus ; le réseau ne répond pas. */
    private fun wrongTurnWithoutNetwork(): Scenario {
        val turn = startOf(nation, 3)
        return GuidanceLab.scenario("mauvais virage sans réseau", nation) {
            drive(from = turn - 150.0, to = turn - 4.0, speedKmh = 30.0)
            routerFails()
            leave(seconds = 45, speedKmh = 30.0)
        }
    }

    @Test fun `wrong turn - recalcul is said once per off-route episode`() {
        lab(wrongTurnWithoutNetwork()) { tl ->
            assertEquals("« Recalcul de l'itinéraire. » dit une seule fois (t=${tl.secondsOf { it == SpokenPhrases.REROUTING }})\n$tl", 1, tl.count { it == SpokenPhrases.REROUTING })
        }
    }

    // --- 10. Sortie d'autoroute manquée ---

    /**
     * Sortie 19 manquée à [speedKmh] : le véhicule reste sur la chaussée, en ligne droite au cap du tracé mesuré sur les 60 m qui
     * précèdent le point de sortie (138°), pendant [seconds] s. Le routeur ne répond pas.
     */
    private fun missedExit(speedKmh: Double, seconds: Int = 40): Scenario {
        val at = startOf(motorway, 11)
        return GuidanceLab.scenario("sortie d'autoroute manquée ${speedKmh.toInt()} km-h", motorway) {
            drive(from = at - 1_200.0, to = at, speedKmh = speedKmh)
            routerFails()
            leave(seconds = seconds, speedKmh = speedKmh)
        }
    }

    /** Délai (s) entre le point de sortie et la première demande de recalcul, et secondes de référence de la règle générale (écart > 100 m). */
    private class MissedExitMeasure(val seconds: Int?, val meters: Double?, val offAtRequestM: Double?, val hardRuleSeconds: Int?)

    private fun measureMissedExit(tl: Timeline, speedKmh: Double): MissedExitMeasure {
        // Dernier fix sur le tracé = le point de sortie (à moins d'un pas près).
        val left = tl.ticks.first { it.label == "quitte" }.t - 1
        val request = tl.requests.firstOrNull()
        val seconds = request?.let { it.t - left }
        val hard = tl.ticks.firstOrNull { it.t > left && (it.guiding?.progress?.offRouteM ?: 0.0) > GuidanceConstants.OFF_ROUTE_HARD_M }?.let { it.t - left }
        val m = MissedExitMeasure(seconds, seconds?.let { it * speedKmh / 3.6 }, request?.let { tl.at(it.t).guiding?.progress?.offRouteM }, hard)
        tl.dump(
            String.format(
                Locale.ROOT, "MESURE : sortie manquée à %.0f km/h — recalcul demandé %s s / %s m après le point de sortie (écart au tracé alors : %s m) ; référence, règle générale des 100 m d'écart : %s s / %s m",
                speedKmh, m.seconds, m.meters?.toInt(), m.offAtRequestM?.toInt(), hard, hard?.let { (it * speedKmh / 3.6).toInt() },
            ),
        )
        return m
    }

    @Test fun `missed motorway exit at 110 km h - delay before the reroute is requested`() {
        lab(missedExit(110.0)) { tl ->
            val m = measureMissedExit(tl, 110.0)
            assertTrue("un recalcul finit par être demandé\n$tl", m.seconds != null)
            // Mesuré le 2026-11-21 avec `MissedManeuverDetector` branché : 14 s / 427 m (écart 25 m). La règle générale seule (écart > 100 m)
            // n'aurait réagi qu'à 21 s / 641 m. À 90 km/h : 17 s / 425 m (règle générale : 25 s / 625 m) ; à 130 km/h : 12 s / 433 m
            // (18 s / 650 m). Borne large : à resserrer quand le détecteur sera stabilisé.
            assertTrue("recalcul demandé ${m.seconds} s / ${m.meters} m après la sortie manquée\n$tl", m.seconds!! <= 30 && m.meters!! <= 920.0)
            assertTrue("jamais avant le point de sortie\n$tl", m.seconds > 0)
            assertEquals("« Recalcul de l'itinéraire. » dit à la première demande\n$tl", tl.requests.first().t, tl.secondsOf { it == SpokenPhrases.REROUTING }.first())
            // Tant que rien ne prouve l'erreur, le guidage ne s'affole pas : pas de recalcul affiché avant la demande.
            assertTrue(tl.ticks.filter { it.t < tl.requests.first().t }.none { it.rerouting })
        }
    }

    @Test fun `missed motorway exit at 90 and 130 km h - the delay is recorded and stays bounded`() {
        for (speed in listOf(90.0, 130.0)) {
            lab(missedExit(speed)) { tl ->
                val m = measureMissedExit(tl, speed)
                assertTrue("à $speed km/h : recalcul demandé ${m.seconds} s / ${m.meters} m après la sortie manquée\n$tl", m.seconds != null && m.seconds in 1..30)
            }
        }
    }

    @Test fun `missed motorway exit without network - nothing of the abandoned route is announced, recalcul said once`() {
        lab(missedExit(110.0)) { tl ->
            val first = tl.requests.first().t
            val said = tl.ticks.filter { it.t > first }.flatMap { tick -> tick.spoken.map { "t=${tick.t} « $it »" } }
            assertEquals("après la demande de recalcul, seul l'échec est dit, une fois\n$tl", 1, said.size)
            assertTrue("$said", said.single().endsWith("« ${SpokenPhrases.REROUTE_FAILED} »"))
        }
    }

    @Test fun `missed motorway exit then reroute - the new route is adopted and nothing of the old one is said after`() {
        val at = startOf(motorway, 11)
        // La chaussée prolongée en ligne droite recoupe la fin du tracé réel (abscisse 19 400 m, 1,6 km plus loin, à 0,2° du cap de
        // la chaussée) : le tracé « recalculé » est cette ligne droite, puis la fin réelle du tracé (géométrie et manœuvres réelles).
        // Seul le raccord en ligne droite n'est pas une route enregistrée.
        val rejoin = 19_400.0
        val t = RouteTracker(motorway)
        val straightOn = bearing(t.pointAt(at - 60.0), t.pointAt(at))
        val toRejoin = bearing(t.pointAt(at), t.pointAt(rejoin))
        assertEquals("le raccord est dans l'axe de la chaussée", straightOn, toRejoin, 1.0)
        val tailM = t.totalGeometryM - rejoin
        lab(GuidanceLab.scenario("sortie manquée puis recalcul", motorway) {
            drive(from = at - 1_200.0, to = at, speedKmh = 110.0)
            routerFails()
            leave(seconds = 26, speedKmh = 110.0, bearingDeg = toRejoin)
            routerAnswers { request -> GuidanceLab.tailRoute(motorway, rejoin, request.origin) }
            leave(seconds = 14, speedKmh = 110.0, untilServed = true)
            followServedRoute()
            drive(toEndMinus = tailM + 220.0, speedKmh = 110.0)
            drive(toEndMinus = tailM + 15.0, speedKmh = 60.0)
            drive(toEndMinus = 45.0, speedKmh = 30.0)
        }) { tl ->
            assertEquals("un seul tracé rendu\n$tl", 1, tl.served.size)
            val fresh = tl.served.single()
            assertTrue("2 échecs puis la bonne réponse\n$tl", tl.requests.size == 3)
            tl.requests.zipWithNext().forEach { (a, b) -> assertTrue(b.atMs - a.atMs >= GuidanceConstants.REROUTE_MIN_INTERVAL_MS) }
            val after = tl.ticks.filter { it.t > fresh.t }
            assertTrue("le nouveau tracé est guidé dès la seconde suivante, sans recalcul affiché\n$tl", after.all { it.routeId == fresh.route.id && !it.rerouting && it.mode != GuidanceMode.REROUTING })
            assertTrue("le véhicule est sur le nouveau tracé\n$tl", after.all { it.guiding!!.progress.offRouteM < 15.0 })
            // Plus rien de l'ancien tracé : ni Labège, ni D 916, ni rond-point, ni bretelle Griffon, ni recalcul.
            val said = after.flatMap { it.spoken }
            val oldOnly = listOf("Labège", "D 916", "rond-point", "Griffon", "sortie 19", "Recalcul")
            assertTrue("aucune annonce de l'ancien tracé après le recalcul : $said\n$tl", said.none { s -> oldOnly.any(s::contains) })
            assertNone("voix en contradiction avec l'état affiché", GuidanceLab.voiceContradictions(tl, from = fresh.t + 1), tl)
            // Le guidage reprend : le virage suivant du nouveau tracé est annoncé à distance puis dit une fois, avant d'y être.
            val turn = fresh.route.maneuvers[1]
            assertEquals(ManeuverType.TURN_RIGHT, turn.type)
            assertTrue("virage annoncé à distance\n$tl", said.any { GuidanceLab.isDistance(it, turn) })
            val now = after.filter { tick -> tick.nextIndex == 1 && tick.spoken.any { GuidanceLab.isNow(it, turn) } }
            assertEquals("virage dit une fois\n$tl", 1, now.size)
            assertTrue(now.single().trueAlongM!! < RouteTracker(fresh.route).maneuverStartM[1])
            assertNone("annonce répétée", GuidanceLab.repeatedAnnouncements(tl).filter { it.contains("Tournez") || it.contains("tournez") }, tl)
            assertNone("oscillation de mode sur le nouveau tracé", GuidanceLab.modeOscillations(Timeline(tl.scenario, after, tl.requests, tl.served)), tl)
        }
    }
}
