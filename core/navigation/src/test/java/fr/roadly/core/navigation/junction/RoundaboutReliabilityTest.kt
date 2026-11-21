package fr.roadly.core.navigation.junction

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RoundaboutConfidence
import fr.roadly.core.navigation.RoundaboutFix
import fr.roadly.core.navigation.RoundaboutLayout
import fr.roadly.core.navigation.RoundaboutLayouts
import fr.roadly.core.navigation.RoundaboutLive
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RoundaboutProgressTracker
import fr.roadly.core.navigation.RoundaboutResolver
import fr.roadly.core.navigation.RoundaboutVoice
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fiabilité du mode rond-point face à un GPS imparfait : saut, imprécision, extrapolation, bruit, route décalée, petit et grand
 * rond-point. La règle commune : dans le doute le décompte se fige, il ne remonte jamais, et rien n'est affirmé sans preuve.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoundaboutReliabilityTest {
    private val etoile = RealJunctions.route("etoile")
    private val etoileEnter = RealJunctions.enterIndices(etoile).first()
    private val l: RoundaboutLayout = layout("etoile", etoile, etoileEnter)

    private fun layout(name: String, route: Route, enter: Int) =
        RoundaboutLayouts.build(RoundaboutResolver.resolve(route, enter), RoundaboutExitCounter.count(route, enter, RealJunctions.topology(name, route, enter)))

    private fun RoundaboutProgressTracker.at(offsetM: Double, fix: RoundaboutFix = RoundaboutFix()) =
        update("etoile", etoileEnter, l, inside = true, distanceToNextM = l.ringLengthM - offsetM, fix = fix)

    @Test fun `an exit counts only once clearly behind, and the margin grows with imprecision but never past the next exit`() {
        val first = l.branches[0].offsetM
        assertEquals(0, RoundaboutProgressTracker().at(first + 1.0).exitsPassed)
        assertEquals(1, RoundaboutProgressTracker().at(first + 2.5).exitsPassed)
        // À 20 m de précision la marge est plus large (6 m au plus selon l'écart) : 2,5 m ne suffisent plus.
        assertEquals(0, RoundaboutProgressTracker().at(first + 2.5, RoundaboutFix(accuracyM = 20f)).exitsPassed)
        for (i in 0 until l.branches.size - 1) {
            val gap = l.branches[i + 1].offsetM - l.branches[i].offsetM
            val margin = RoundaboutProgressTracker.passMarginM(l, i, accuracyM = 25f)
            assertTrue("marge $margin < écart $gap", margin < gap && margin <= RoundaboutProgressTracker.MAX_PASS_MARGIN_M)
            assertTrue(RoundaboutProgressTracker.passMarginM(l, i, accuracyM = 1f) >= RoundaboutProgressTracker.MIN_PASS_MARGIN_M)
        }
    }

    @Test fun `a forward GPS jump is held until the next fix confirms it`() {
        val t = RoundaboutProgressTracker()
        var now = 0L
        fun fix() = RoundaboutFix(accuracyM = 6f, speedKmh = 30.0, nowMs = now.also { now += 1_000 })
        val start = t.at(5.0, fix())
        assertEquals(0, start.exitsPassed)
        // 1 s plus tard la position saute de 5 m à la 4e sortie : impossible à 30 km/h.
        val far = l.branches[3].offsetM + 4.0
        val jumped = t.at(far, fix())
        assertEquals(0, jumped.exitsPassed)
        assertEquals(RoundaboutConfidence.LOW, jumped.confidence)
        assertEquals(start.vehicleAngleDeg, jumped.vehicleAngleDeg)
        // Le fix suivant revient près de la vraie position : le saut est oublié.
        val back = t.at(14.0, fix())
        assertEquals(RoundaboutConfidence.HIGH, back.confidence)
        assertEquals(0, back.exitsPassed)
        // Un saut confirmé par la position suivante (trou de réception, pas une erreur) est accepté.
        val again = t.at(far, fix())
        assertEquals(0, again.exitsPassed)
        val confirmed = t.at(far + 6.0, fix())
        assertEquals(4, confirmed.exitsPassed)
        assertEquals(RoundaboutConfidence.HIGH, confirmed.confidence)
    }

    @Test fun `a slow GPS - one fix every 4 s - is not mistaken for a jump`() {
        val t = RoundaboutProgressTracker()
        var now = 0L
        var offset = 0.0
        var last: RoundaboutLive? = null
        while (offset <= l.ringLengthM) {
            last = t.at(offset, RoundaboutFix(accuracyM = 6f, speedKmh = 28.0, nowMs = now))
            assertEquals("à $offset m", RoundaboutConfidence.HIGH, last.confidence)
            now += 4_000; offset += 28.0 / 3.6 * 4
        }
        assertTrue(last!!.exitsPassed >= 4)
    }

    @Test fun `imprecise or extrapolated positions freeze the count, which catches up when the position is sure again`() {
        val t = RoundaboutProgressTracker()
        val sure = t.at(l.branches[0].offsetM + 4.0)
        assertEquals(1, sure.exitsPassed)
        val blurry = t.at(l.branches[2].offsetM + 4.0, RoundaboutFix(accuracyM = 45f))
        assertEquals(1, blurry.exitsPassed)
        assertEquals(RoundaboutConfidence.LOW, blurry.confidence)
        assertEquals(sure.ringFraction, blurry.ringFraction)
        val guessed = t.at(l.branches[3].offsetM + 4.0, RoundaboutFix(extrapolated = true))
        assertEquals(1, guessed.exitsPassed)
        assertEquals(RoundaboutConfidence.LOW, guessed.confidence)
        val again = t.at(l.branches[3].offsetM + 5.0)
        assertEquals(4, again.exitsPassed)
        assertEquals(RoundaboutConfidence.HIGH, again.confidence)
    }

    @Test fun `the required accuracy follows the spacing of the exits`() {
        val required = RoundaboutProgressTracker.maxAccuracyM(l)
        assertTrue(required in RoundaboutProgressTracker.MIN_REQUIRED_ACCURACY_M..RoundaboutProgressTracker.MAX_REQUIRED_ACCURACY_M)
        // Petit rond-point réel (Muret) : sorties serrées, précision exigée plus stricte ou égale à celle de l'Étoile.
        val muret = RealJunctions.route("muret")
        val small = layout("muret", muret, RealJunctions.enterIndices(muret).first())
        assertTrue(RoundaboutProgressTracker.maxAccuracyM(small) <= required)
    }

    @Test fun `voice gate - unsure and not yet acquired means no claim, acquired stays acquired`() {
        val t = RoundaboutProgressTracker()
        val unsure = t.at(l.branches[1].offsetM + 4.0, RoundaboutFix(accuracyM = 60f))
        assertNull(RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, unsure))
        val acquired = t.at(l.branches[4].offsetM + 5.0)
        assertEquals(true, RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, acquired))
        val thenBlurry = t.at(l.branches[4].offsetM + 9.0, RoundaboutFix(accuracyM = 60f))
        assertEquals(true, RoundaboutVoice.exitGate(ManeuverType.ROUNDABOUT_EXIT, thenBlurry))
    }

    // --- Session complète ---

    private val location = FakeLocationRepository()
    private val speech = FakeSpeechService()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }

    private fun TestScope.session(name: String): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val topology = object : JunctionTopologyProvider {
            override suspend fun topology(route: Route, maneuverIndex: Int, window: JunctionWindow): Outcome<JunctionTopology> =
                Outcome.Success(RealJunctions.topology(name, route, maneuverIndex))
        }
        return NavigationSession(
            location, routing, NoopSpeedLimitProvider(), speech, FakeUserPreferencesRepository(), FakeNavigationServiceController(), { testScheduler.currentTime },
            NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope, junctionTopology = topology,
        )
    }

    private suspend fun TestScope.play(s: NavigationSession, fixes: List<LocationFix>): List<NavigationState.Guiding> {
        val out = ArrayList<NavigationState.Guiding>()
        fixes.forEach { f ->
            advanceTimeBy(1_000)
            location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent()
            (s.state.value as? NavigationState.Guiding)?.let(out::add)
        }
        return out
    }

    /** Invariants d'un passage : décompte jamais croissant, phases dans l'ordre, sortie dite une fois et jamais avant la dernière sortie à laisser. */
    private fun assertCoherent(route: Route, enter: Int, states: List<NavigationState.Guiding>, label: String) {
        // Seulement ce rond-point : approche et anneau (prochaine manœuvre = son entrée ou sa sortie), puis sa confirmation de sortie.
        val lives = states.filter { it.progress.nextManeuverIndex in enter..enter + 1 || it.roundaboutLive?.phase == RoundaboutPhase.EXITED }.mapNotNull { it.roundaboutLive }
        assertTrue("$label : le mode rond-point a été vu", lives.size > 3)
        val remaining = lives.mapNotNull { it.exitsRemaining }
        assertEquals("$label : décompte jamais croissant", remaining.sortedDescending(), remaining)
        val phases = lives.map { it.phase }.distinct()
        assertEquals("$label : phases dans l'ordre", phases.sortedBy { it.ordinal }, phases)
        val exitPhrase = route.maneuvers[enter + 1].verbalPre!!.trimEnd('.').substringBefore(". Ensuite")
        assertEquals("$label : sortie dite une fois (${speech.spoken})", 1, speech.spoken.count { it.startsWith(exitPhrase) })
    }

    @Test fun `noisy GPS through the etoile - 4 m of noise, 9 m accuracy - stays coherent`() = runTest {
        val t = RouteTracker(etoile)
        val s = session("etoile"); s.start(RouteOption(etoile, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        val states = play(s, FixScript(etoile, seed = 7).drive(t.maneuverStartM[etoileEnter] - 200.0, t.maneuverStartM[etoileEnter + 1] + 90.0, speedKmh = 26.0, noiseM = 4.0, accuracyM = 9f))
        assertCoherent(etoile, etoileEnter, states, "Étoile bruitée")
        assertEquals(0, states.mapNotNull { it.roundaboutLive?.exitsRemaining }.last())
    }

    @Test fun `exit taken is shown only with evidence - measured, on the route, clearly past the exit node`() = runTest {
        val t = RouteTracker(etoile)
        val exitAt = t.maneuverStartM[etoileEnter + 1]
        val s = session("etoile"); s.start(RouteOption(etoile, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        val states = play(s, FixScript(etoile).drive(t.maneuverStartM[etoileEnter] - 120.0, exitAt + 70.0, speedKmh = 20.0))
        val exited = states.filter { it.roundaboutLive?.phase == RoundaboutPhase.EXITED }
        assertTrue(exited.isNotEmpty())
        exited.forEach { g ->
            assertEquals(etoileEnter + 2, g.progress.nextManeuverIndex)
            assertTrue("sortie affichée à ${g.progress.distanceAlongM - exitAt} m du nœud", g.progress.distanceAlongM - exitAt >= 8.0 * etoile.distanceMeters / t.totalGeometryM - 0.5)
        }
        // Entre le basculement de l'indice et la preuve, le schéma reste sur « prochaine sortie » : jamais de trou d'affichage.
        val between = states.filter { it.progress.nextManeuverIndex == etoileEnter + 2 && it.roundaboutLive?.phase == RoundaboutPhase.INSIDE }
        between.forEach { assertTrue(it.roundaboutLive!!.exitIsNext) }
    }

    @Test fun `GPS lost inside the ring - the count freezes, nothing is announced on a guess, it resumes after`() = runTest {
        val t = RouteTracker(etoile)
        val enterAt = t.maneuverStartM[etoileEnter]
        val exitAt = t.maneuverStartM[etoileEnter + 1]
        val s = session("etoile"); s.start(RouteOption(etoile, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
        val before = play(s, FixScript(etoile).drive(enterAt - 150.0, enterAt + 40.0, speedKmh = 26.0))
        val frozenAt = before.last().roundaboutLive!!.exitsPassed
        // 8 s sans position : le suivi extrapole le long du tracé.
        advanceTimeBy(8_000); runCurrent()
        val during = (s.state.value as NavigationState.Guiding).roundaboutLive
        if (during != null) {
            assertEquals("décompte gelé pendant l'extrapolation", frozenAt, during.exitsPassed)
            assertEquals(RoundaboutConfidence.LOW, during.confidence)
        }
        val after = play(s, FixScript(etoile).drive(enterAt + 100.0, exitAt + 80.0, speedKmh = 26.0))
        assertCoherent(etoile, etoileEnter, before + after, "Étoile avec perte GPS")
    }

    @Test fun `every real roundabout driven with noise keeps voice, count and phases coherent`() = runTest {
        var driven = 0
        for (name in listOf("muret", "nation", "motorway")) {
            val route = RealJunctions.route(name)
            val t = RouteTracker(route)
            for (enter in RealJunctions.enterIndices(route)) {
                if (route.maneuvers.getOrNull(enter + 1)?.type != ManeuverType.ROUNDABOUT_EXIT) continue
                val from = (t.maneuverStartM[enter] - 120.0).coerceAtLeast(t.maneuverStartM[enter - 1] + 5.0)
                val to = minOf(t.maneuverStartM[enter + 1] + 70.0, t.maneuverStartM.getOrElse(enter + 2) { t.totalGeometryM } - 5.0)
                speech.spoken.clear()
                val s = session(name); s.start(RouteOption(route, "r", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
                val states = play(s, FixScript(route, seed = 11 + enter).drive(from, to, speedKmh = 24.0, noiseM = 3.0, accuracyM = 8f))
                assertCoherent(route, enter, states, "$name/$enter")
                s.stop(); runCurrent()
                driven++
            }
        }
        assertTrue("au moins 5 ronds-points réels parcourus : $driven", driven >= 5)
    }
}
