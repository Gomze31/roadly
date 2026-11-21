package fr.roadly.core.navigation.lab

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.navigation.GuidanceConstants
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.PositioningPolicy
import fr.roadly.core.navigation.RoundaboutLive
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.SituationEngine
import fr.roadly.core.navigation.SpokenPhrases
import fr.roadly.core.navigation.bearing
import fr.roadly.core.navigation.junction.JunctionTopologyProvider
import fr.roadly.core.navigation.junction.JunctionWindow
import fr.roadly.core.navigation.junction.NoJunctionTopologyProvider
import fr.roadly.core.navigation.junction.RealJunctions
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Laboratoire de guidage : un scénario de conduite est décrit une seule fois (positions GPS, précision, vitesse, trous, sauts,
 * sortie d'itinéraire) puis rejoué, seconde par seconde, dans la VRAIE session de guidage ([NavigationSession]) — seuls le GPS,
 * la voix, l'horloge et le routeur sont des doublures. Le résultat est une chronologie ([Timeline]) : pour chaque seconde, le fix
 * émis, l'essentiel de l'état publié (manœuvre visée, distance, mode, rond-point, recalcul, GPS perdu) et ce qui a été dit.
 *
 * Données : uniquement les tracés réels enregistrés ([RealJunctions], `ValhallaFixture`) et la géométrie qui en dérive
 * (décalage latéral, ligne droite le long d'un cap du tracé). Rien d'autre n'est inventé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object GuidanceLab {

    // --- Description d'un scénario ---

    /** Un tronçon de conduite ; chaque seconde simulée produit une ligne de la chronologie. */
    sealed interface Segment {
        /**
         * Roule le long du tracé du véhicule de [fromAlongM] (null = à la suite de la position courante) à [toAlongM] (ou jusqu'à
         * [toEndMinusM] m avant la fin du tracé), à [speedKmh].
         * [noiseM] = écart-type du bruit GPS, [accuracyM] = précision annoncée, [lateralM] = décalage à gauche (route parallèle),
         * [headingOffsetDeg] = écart du cap GPS au cap du tracé.
         */
        data class Drive(
            val fromAlongM: Double?, val toAlongM: Double?, val speedKmh: Double, val noiseM: Double = 0.0, val accuracyM: Float = 5f,
            val lateralM: Double = 0.0, val headingOffsetDeg: Double = 0.0, val label: String = "roule",
            /** Autre façon de donner l'arrivée : à cette distance avant la fin du tracé du véhicule (tracé rendu par le routeur). */
            val toEndMinusM: Double? = null,
        ) : Segment

        /** Aucun fix pendant [seconds] s ; le véhicule continue à sa vitesse (tunnel, perte de réception). */
        data class GpsGap(val seconds: Int) : Segment

        /** Le véhicule continue normalement, mais le GPS le place à [lateralM] m à gauche du tracé pendant [seconds] fix. */
        data class GpsJump(val lateralM: Double, val seconds: Int = 1, val accuracyM: Float = 5f) : Segment

        /** À l'arrêt pendant [seconds] s : fixes sans vitesse ni cap, dispersés de ±[jitterM] m. */
        data class Stop(val seconds: Int, val jitterM: Double = 0.0, val accuracyM: Float = 5f) : Segment

        /**
         * Quitte le tracé en ligne droite pendant [seconds] s à [speedKmh]. Cap : [bearingDeg] s'il est donné ; sinon le cap du tracé
         * mesuré sur les [chordM] derniers mètres avant la position courante (« tout droit »), tourné de [turnDeg] (> 0 = à droite).
         * Déjà hors tracé, le cap courant est conservé. [untilServed] : s'arrête dès que le routeur a rendu un nouveau tracé.
         */
        data class Leave(
            val seconds: Int, val speedKmh: Double, val bearingDeg: Double? = null, val turnDeg: Double = 0.0, val chordM: Double = 60.0,
            val accuracyM: Float = 5f, val untilServed: Boolean = false,
        ) : Segment

        /** À partir d'ici, le routeur répond [answer] à chaque recalcul (null = échec réseau). */
        class RouterAnswers(val answer: (RouteRequest) -> Route?) : Segment

        /** Le véhicule suit désormais le dernier tracé rendu par le routeur, depuis sa position projetée sur ce tracé. */
        data object FollowServedRoute : Segment
    }

    class Scenario(
        val name: String,
        /** Tracé de la course au départ (réel). */
        val route: Route,
        /** Topologie réelle des carrefours (`trace_attributes` enregistré) ; null = aucune. */
        val topology: JunctionTopologyProvider?,
        val segments: List<Segment>,
        val seed: Int,
        /** Délai de réponse du routeur (ms) : pendant ce temps l'état « recalcul » est observable. */
        val routerLatencyMs: Long,
    )

    class ScenarioBuilder {
        val segments = ArrayList<Segment>()
        var seed = 42
        var routerLatencyMs = 1_500L

        fun drive(
            from: Double? = null, to: Double? = null, speedKmh: Double, noiseM: Double = 0.0, accuracyM: Float = 5f, lateralM: Double = 0.0,
            headingOffsetDeg: Double = 0.0, label: String = "roule", toEndMinus: Double? = null,
        ) {
            require((to == null) != (toEndMinus == null)) { "drive() : donner `to` ou `toEndMinus`" }
            segments += Segment.Drive(from, to, speedKmh, noiseM, accuracyM, lateralM, headingOffsetDeg, label, toEndMinus)
        }

        fun gpsGap(seconds: Int) { segments += Segment.GpsGap(seconds) }
        fun gpsJump(lateralM: Double, seconds: Int = 1, accuracyM: Float = 5f) { segments += Segment.GpsJump(lateralM, seconds, accuracyM) }
        fun stop(seconds: Int, jitterM: Double = 0.0, accuracyM: Float = 5f) { segments += Segment.Stop(seconds, jitterM, accuracyM) }
        fun leave(seconds: Int, speedKmh: Double, bearingDeg: Double? = null, turnDeg: Double = 0.0, chordM: Double = 60.0, untilServed: Boolean = false) {
            segments += Segment.Leave(seconds, speedKmh, bearingDeg, turnDeg, chordM, untilServed = untilServed)
        }
        fun routerFails() { segments += Segment.RouterAnswers { null } }
        fun routerAnswers(answer: (RouteRequest) -> Route?) { segments += Segment.RouterAnswers(answer) }
        fun followServedRoute() { segments += Segment.FollowServedRoute }
    }

    /** [topology] : nom d'une trace réelle de [RealJunctions] (« etoile », « muret », « nation », « motorway ») ou null. */
    fun scenario(name: String, route: Route, topology: String? = null, build: ScenarioBuilder.() -> Unit): Scenario {
        val b = ScenarioBuilder().apply(build)
        return Scenario(name, route, topology?.let(::realTopology), b.segments.toList(), b.seed, b.routerLatencyMs)
    }

    fun realTopology(name: String) = object : JunctionTopologyProvider {
        override suspend fun topology(route: Route, maneuverIndex: Int, window: JunctionWindow): Outcome<JunctionTopology> =
            Outcome.Success(RealJunctions.topology(name, route, maneuverIndex))
    }

    // --- Chronologie enregistrée ---

    /** Une seconde de la simulation. */
    class Tick(
        /** Secondes écoulées depuis le départ de la course (0 = départ, avant tout fix). */
        val t: Int,
        val label: String,
        /** Fix émis à cette seconde ; null = aucun (trou GPS, départ). */
        val fix: LocationFix?,
        /** Abscisse VRAIE du véhicule sur son tracé (m) ; null hors tracé. */
        val trueAlongM: Double?,
        val truePoint: GeoPoint,
        val trueSpeedKmh: Double,
        /** Tracé que suit réellement le véhicule (peut différer du tracé guidé pendant un recalcul). */
        val vehicleRouteId: String,
        val state: NavigationState,
        /** Phrases dites pendant cette seconde, dans l'ordre. */
        val spoken: List<String>,
        /** Recalculs demandés au routeur depuis le départ (cumul). */
        val rerouteRequests: Int,
    ) {
        val guiding: NavigationState.Guiding? get() = state as? NavigationState.Guiding
        val routeId: String? get() = guiding?.route?.id
        val nextIndex: Int? get() = guiding?.progress?.nextManeuverIndex
        val distanceM: Double? get() = guiding?.progress?.distanceToNextManeuverM
        val alongM: Double? get() = guiding?.progress?.distanceAlongM
        val mode: GuidanceMode? get() = guiding?.situation?.mode
        val live: RoundaboutLive? get() = guiding?.roundaboutLive
        val rerouting: Boolean get() = guiding?.rerouting == true
        val gpsLost: Boolean get() = guiding?.gpsLost == true
        val extrapolated: Boolean get() = guiding?.progress?.extrapolated == true
    }

    class RerouteRequest(val t: Int, val atMs: Long, val request: RouteRequest)
    class ServedRoute(val t: Int, val atMs: Long, val route: Route)

    class Timeline(val scenario: Scenario, val ticks: List<Tick>, val requests: List<RerouteRequest>, val served: List<ServedRoute>) {
        /** Tout ce qui a été dit, dans l'ordre. */
        val spoken: List<String> get() = ticks.flatMap { it.spoken }

        fun count(predicate: (String) -> Boolean): Int = spoken.count(predicate)

        /** Secondes auxquelles une phrase satisfaisant [predicate] a été dite. */
        fun secondsOf(predicate: (String) -> Boolean): List<Int> = ticks.filter { it.spoken.any(predicate) }.map { it.t }

        fun at(t: Int): Tick = ticks.first { it.t == t }

        /** Modes successifs (doublons consécutifs réunis) pendant que la manœuvre [index] du tracé [routeId] est la prochaine. */
        fun modesWhileNext(index: Int, routeId: String = scenario.route.id): List<GuidanceMode> =
            ticks.filter { it.routeId == routeId && it.nextIndex == index }.mapNotNull { it.mode }.collapsed()

        /** Phases du mode rond-point dans l'ordre, doublons consécutifs réunis (null = schéma absent). */
        fun roundaboutPhases(): List<RoundaboutPhase?> = ticks.map { it.live?.phase }.collapsed()

        override fun toString(): String = describe(this)

        /** Écrit la chronologie dans `build/guidance-lab/<nom>.txt` (trace lisible de chaque scénario, même réussi). */
        fun dump(extra: String = "") {
            try {
                val dir = File("build/guidance-lab").apply { mkdirs() }
                val slug = scenario.name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
                File(dir, "$slug.txt").writeText(describe(this) + (if (extra.isEmpty()) "" else "\n$extra\n"), Charsets.UTF_8)
            } catch (e: Exception) {
                // Trace de confort : son absence ne change aucun résultat.
            }
        }
    }

    private fun <T> List<T>.collapsed(): List<T> = fold(ArrayList()) { acc, x -> if (acc.isEmpty() || acc.last() != x) acc += x; acc }

    // --- Exécution ---

    /** Rejoue [scenario] dans une session réelle neuve, sur l'horloge virtuelle de [scope] (à appeler dans `runTest`). */
    suspend fun run(scope: TestScope, scenario: Scenario): Timeline = Runner(scope, scenario).play()

    private class LabRouter(private val nowMs: () -> Long, private val startMs: () -> Long, private val latencyMs: Long) : RoutingProvider {
        override val id = "laboratoire"
        var answer: (RouteRequest) -> Route? = { null }
        val requests = ArrayList<RerouteRequest>()
        val served = ArrayList<ServedRoute>()
        private fun second() = ((nowMs() - startMs()) / 1000).toInt()

        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += RerouteRequest(second(), nowMs(), request)
            val reply = answer
            delay(latencyMs)
            val route = reply(request) ?: return Outcome.Failure(AppError.Network)
            served += ServedRoute(second(), nowMs(), route)
            return Outcome.Success(listOf(route))
        }
    }

    private class Runner(private val scope: TestScope, private val scenario: Scenario) {
        private val location = FakeLocationRepository()
        private val speech = FakeSpeechService()
        private var startMs = 0L
        private val router = LabRouter({ scope.testScheduler.currentTime }, { startMs }, scenario.routerLatencyMs)
        private val ticks = ArrayList<Tick>()
        private lateinit var session: NavigationSession

        // Vérité du véhicule (indépendante de ce que le guidage en déduit).
        private var vRoute = scenario.route
        private var vTracker = RouteTracker(vRoute)
        private var vScript = FixScript(vRoute, scenario.seed)
        private var along: Double? = null
        private var point: GeoPoint = vRoute.geometry.first()
        private var speedKmh = 0.0
        private var bearingDeg = 0.0
        private var heardUpTo = 0

        suspend fun play(): Timeline {
            val d = StandardTestDispatcher(scope.testScheduler)
            val dispatchers = object : DispatcherProvider {
                override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
            }
            session = NavigationSession(
                location, router, NoopSpeedLimitProvider(), speech, FakeUserPreferencesRepository(), FakeNavigationServiceController(),
                { scope.testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers,
                scope.backgroundScope, junctionTopology = scenario.topology ?: NoJunctionTopologyProvider,
            )
            startMs = scope.testScheduler.currentTime
            session.start(RouteOption(scenario.route, "Laboratoire", 0L, null, sameAsFastest = false), RoutePreferences())
            scope.runCurrent()
            record("départ", null)
            for (segment in scenario.segments) play(segment)
            return Timeline(scenario, ticks, router.requests.toList(), router.served.toList())
        }

        private suspend fun play(s: Segment) {
            when (s) {
                is Segment.Drive -> {
                    val step = s.speedKmh / 3.6
                    require(step > 0.0) { "Vitesse nulle : utiliser stop()" }
                    var a = s.fromAlongM ?: ((along ?: error("${scenario.name} : drive(from = null) alors que le véhicule est hors tracé")) + step)
                    speedKmh = s.speedKmh
                    val to = s.toAlongM ?: (vTracker.totalGeometryM - s.toEndMinusM!!)
                    while (a <= to + 1e-6) {
                        moveTo(a)
                        step(s.label, vScript.drive(a, a, s.speedKmh, s.noiseM, s.accuracyM, s.lateralM, s.headingOffsetDeg).single())
                        a += step
                    }
                }
                is Segment.GpsGap -> repeat(s.seconds) { advance(); step("trou GPS", null) }
                is Segment.GpsJump -> repeat(s.seconds) {
                    advance()
                    val a = along ?: error("${scenario.name} : gpsJump() hors tracé")
                    require(speedKmh > 0.0) { "gpsJump() à l'arrêt" }
                    step("saut GPS", vScript.drive(a, a, speedKmh, accuracyM = s.accuracyM, lateralM = s.lateralM).single())
                }
                is Segment.Stop -> {
                    speedKmh = 0.0
                    val a = along ?: error("${scenario.name} : stop() hors tracé")
                    repeat(s.seconds) { step("arrêt", vScript.jitter(a, 1, s.jitterM, s.accuracyM).single()) }
                }
                is Segment.Leave -> {
                    val a = along
                    bearingDeg = when {
                        s.bearingDeg != null -> s.bearingDeg
                        a != null -> bearing(vTracker.pointAt((a - s.chordM).coerceAtLeast(0.0)), vTracker.pointAt(a))
                        else -> bearingDeg
                    }.let { ((it + s.turnDeg) % 360.0 + 360.0) % 360.0 }
                    along = null
                    speedKmh = s.speedKmh
                    for (i in 0 until s.seconds) {
                        if (s.untilServed && router.served.isNotEmpty()) break
                        advance()
                        step("quitte", LocationFix(point, bearingDeg.toFloat(), (speedKmh / 3.6).toFloat(), s.accuracyM, 0L))
                    }
                }
                is Segment.RouterAnswers -> router.answer = s.answer
                Segment.FollowServedRoute -> {
                    vRoute = router.served.lastOrNull()?.route
                        ?: throw AssertionError("${scenario.name} : le routeur n'a rendu aucun tracé\n" + describe(Timeline(scenario, ticks, router.requests, router.served)))
                    vTracker = RouteTracker(vRoute)
                    vScript = FixScript(vRoute, scenario.seed)
                    // Position vraie reportée sur le nouveau tracé (projection par un suivi neuf, cap du véhicule compris).
                    along = RouteTracker(vRoute).update(LocationFix(point, bearingDeg.toFloat(), (speedKmh / 3.6).toFloat(), 5f, 0L)).distanceAlongM
                }
            }
        }

        private fun moveTo(a: Double) {
            along = a
            point = vTracker.pointAt(a)
        }

        /** Une seconde de déplacement vrai, sans rien émettre. */
        private fun advance() {
            val a = along
            if (a != null) moveTo(min(a + speedKmh / 3.6, vTracker.totalGeometryM)) else point = offset(point, bearingDeg, speedKmh / 3.6)
        }

        /** Une seconde : l'horloge avance, le fix (s'il existe) est émis, la session le traite, l'état et la voix sont relevés. */
        private suspend fun step(label: String, fix: LocationFix?) {
            scope.advanceTimeBy(1_000)
            val stamped = fix?.copy(timestampMs = scope.testScheduler.currentTime)
            if (stamped != null) location.emit(stamped)
            scope.runCurrent()
            record(label, stamped)
        }

        private fun record(label: String, fix: LocationFix?) {
            val said = speech.spoken.subList(heardUpTo, speech.spoken.size).toList()
            heardUpTo = speech.spoken.size
            val t = ((scope.testScheduler.currentTime - startMs) / 1000).toInt()
            ticks += Tick(t, label, fix, along, point, speedKmh, vRoute.id, session.state.value, said, router.requests.size)
        }
    }

    /** [p] déplacé de [meters] dans la direction [bearingDeg] (plan local, même formule que [FixScript]). */
    fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        if (meters == 0.0) return p
        val rad = bearingDeg * PI / 180.0
        val dLat = meters * cos(rad) / TestRoutes.METERS_PER_DEG_LAT
        val dLon = meters * sin(rad) / (TestRoutes.METERS_PER_DEG_LAT * cos(p.lat * PI / 180.0))
        return GeoPoint(p.lat + dLat, p.lon + dLon)
    }

    /**
     * Tracé « recalculé » dérivé d'un tracé réel : la fin de [real] à partir de l'abscisse [rejoinAlongM] (géométrie et manœuvres
     * réelles, indices décalés), précédée d'un seul segment droit depuis [origin] (la position d'où le recalcul est demandé).
     * Seul ce segment de raccord n'est pas une route enregistrée : les jeux de données ne contiennent pas de second itinéraire réel
     * partant d'une position hors tracé.
     */
    fun tailRoute(real: Route, rejoinAlongM: Double, origin: GeoPoint, id: String = "${real.id}-recalcul"): Route {
        val t = RouteTracker(real)
        val first = t.cumDistM.indexOfFirst { it > rejoinAlongM }
        require(first > 0) { "Abscisse de raccord hors du tracé" }
        val rejoin = t.pointAt(rejoinAlongM)
        val shift = 2 - first
        val current = real.maneuvers.indices.last { t.maneuverStartM[it] <= rejoinAlongM }
        val rest = real.maneuvers.drop(current + 1).map { it.copy(beginShapeIndex = it.beginShapeIndex + shift, endShapeIndex = it.endShapeIndex?.plus(shift)) }
        require(rest.isNotEmpty()) { "Aucune manœuvre après le raccord" }
        val headM = origin.distanceTo(rejoin) + (t.maneuverStartM[current + 1] - rejoinAlongM)
        val old = real.maneuvers[current]
        val pace = if (old.lengthMeters > 0.0) old.durationSeconds / old.lengthMeters else 0.0
        val depart = old.copy(
            type = ManeuverType.DEPART, lengthMeters = headM, durationSeconds = headM * pace, beginShapeIndex = 0,
            roundaboutExit = null, verbalAlert = null, verbalPost = null, endShapeIndex = rest.first().beginShapeIndex,
        )
        val geometry = listOf(origin, rejoin) + real.geometry.drop(first)
        val length = (0 until geometry.lastIndex).sumOf { geometry[it].distanceTo(geometry[it + 1]) }
        val maneuvers = listOf(depart) + rest
        return real.copy(id = id, distanceMeters = length, durationSeconds = maneuvers.sumOf { it.durationSeconds }, geometry = geometry, maneuvers = maneuvers)
    }

    // --- Lecture de la voix ---

    enum class Spoken { DEPART, DISTANCE, POSITIONING, NOW, CONFIRMATION, REROUTING, REROUTE_FAILED, GPS_LOST, ARRIVAL }

    /** Début de la phrase « maintenant » de [m] (sans le point final : elle peut être suivie de « , puis … »). */
    fun nowStem(m: Maneuver): String = (m.verbalPre ?: m.instruction).trimEnd().removeSuffix(".")

    /** Phrase d'anticipation de [m] telle qu'elle suit « Dans X, » (minuscule initiale, sans le point final). */
    fun alertStem(m: Maneuver): String =
        (m.verbalAlert ?: m.verbalPre ?: m.instruction).replaceFirstChar { it.lowercase(Locale.FRANCE) }.trimEnd().removeSuffix(".")

    /** Vrai si [text] est l'annonce « maintenant » (éventuellement enchaînée) de [m]. */
    fun isNow(text: String, m: Maneuver): Boolean = text.startsWith(nowStem(m))

    /** Vrai si [text] est une annonce à distance (« Dans X, … ») de [m]. */
    fun isDistance(text: String, m: Maneuver): Boolean = text.startsWith("Dans ") && text.substringAfter(", ").startsWith(alertStem(m))

    /** Nature de [text] par rapport à la manœuvre [k] de [route] (celle que l'écran montre) ; null = phrase étrangère à cette manœuvre. */
    fun classify(route: Route, k: Int, text: String): Spoken? {
        val m = route.maneuvers.getOrNull(k) ?: return null
        return when {
            text == SpokenPhrases.REROUTING -> Spoken.REROUTING
            text == SpokenPhrases.REROUTE_FAILED -> Spoken.REROUTE_FAILED
            text == SpokenPhrases.GPS_LOST -> Spoken.GPS_LOST
            text == PositioningPolicy.phrase(m) -> Spoken.POSITIONING
            isDistance(text, m) -> Spoken.DISTANCE
            isNow(text, m) -> if (m.type == ManeuverType.ARRIVE) Spoken.ARRIVAL else Spoken.NOW
            k > 0 && text == route.maneuvers[k - 1].verbalPost -> Spoken.CONFIRMATION
            else -> null
        }
    }

    /**
     * Contradictions entre la voix et l'état affiché à la même seconde. Règles :
     * - toute phrase doit se rapporter à la manœuvre affichée (ou être le départ, le recalcul, la confirmation de la précédente) ;
     * - « Dans X, … » : X est la distance affichée (même arrondi) ;
     * - « maintenant » : jamais sur une position extrapolée ou GPS perdu, jamais en mode CRUISE pour une manœuvre à préparer ;
     * - « Quittez le rond-point » : seulement dans l'anneau et, si le décompte est fiable, quand la prochaine sortie est la bonne ;
     * - « Recalcul de l'itinéraire. » : seulement quand l'état montre le recalcul ;
     * - aucune annonce de manœuvre quand le véhicule est mesuré à plus de 100 m du tracé.
     * [from] : première seconde examinée.
     */
    fun voiceContradictions(tl: Timeline, from: Int = 0): List<String> {
        val out = ArrayList<String>()
        for (tick in tl.ticks.filter { it.t >= from }) for (text in tick.spoken) {
            fun bad(why: String) { out += "t=${tick.t} « $text » : $why" }
            val state = tick.state
            if (tick.t == 0) {
                if (!isNow(text, tl.scenario.route.maneuvers.first())) bad("au départ, seule la phrase de départ est attendue")
                continue
            }
            if (state is NavigationState.Arrived) {
                if (!isNow(text, state.route.maneuvers.last())) bad("à l'arrivée, seule la phrase d'arrivée est attendue")
                continue
            }
            val g = state as? NavigationState.Guiding
            if (g == null) { bad("dit alors que l'état est $state"); continue }
            val k = g.progress.nextManeuverIndex
            val m = g.route.maneuvers[k.coerceIn(0, g.route.maneuvers.lastIndex)]
            val kind = classify(g.route, k, text)
            if (kind == null) { bad("phrase étrangère à la manœuvre affichée (n=$k « ${nowStem(m)} », tracé ${g.route.id})"); continue }
            if (kind == Spoken.REROUTING || kind == Spoken.REROUTE_FAILED) {
                if (!g.rerouting && g.situation.mode != GuidanceMode.REROUTING) bad("l'état n'affiche pas le recalcul")
                continue
            }
            if (kind == Spoken.GPS_LOST) {
                if (!g.gpsLost) bad("l'état n'affiche pas la perte du GPS")
                continue
            }
            if (kind == Spoken.CONFIRMATION) continue
            if (!g.progress.extrapolated && g.progress.offRouteM > GuidanceConstants.OFF_ROUTE_HARD_M) {
                bad("manœuvre annoncée à ${g.progress.offRouteM.toInt()} m du tracé")
            }
            if (g.rerouting) bad("manœuvre annoncée pendant le recalcul")
            if (kind == Spoken.DISTANCE) {
                val shown = SpokenPhrases.distance(g.progress.distanceToNextManeuverM)
                if (!text.startsWith("Dans $shown, ")) bad("distance dite ≠ distance affichée ($shown)")
            }
            if (kind == Spoken.NOW || kind == Spoken.ARRIVAL) {
                if (g.progress.extrapolated) bad("« maintenant » sur une position extrapolée")
                if (g.gpsLost) bad("« maintenant » alors que le GPS est perdu")
                val toPrepare = SituationEngine.kindOf(m, chained = false) != null
                if (toPrepare && g.situation.mode == GuidanceMode.CRUISE) bad("« maintenant » alors que l'écran est en mode CRUISE")
                if (m.type == ManeuverType.ROUNDABOUT_EXIT) {
                    val live = g.roundaboutLive
                    if (live == null || live.phase != RoundaboutPhase.INSIDE) bad("sortie dite hors de l'anneau (schéma ${live?.phase})")
                    else if (live.layout.counted && live.layout.intermediateCount > 0 && !live.exitIsNext) bad("sortie dite alors qu'il reste ${live.exitsRemaining} sortie(s) à laisser")
                }
            }
        }
        return out
    }

    /** Annonces répétées : même phrase dite plus d'une fois pour la même manœuvre du même tracé. */
    fun repeatedAnnouncements(tl: Timeline): List<String> =
        tl.ticks.flatMap { tick -> tick.spoken.map { Triple(tick.routeId, tick.nextIndex, it) to tick.t } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .map { (key, seconds) -> "« ${key.third} » (n=${key.second}) dit ${seconds.size} fois : t=$seconds" }

    /**
     * Oscillations de mode : pour une même manœuvre visée, un mode quitté puis retrouvé (A→B→A). Les modes de [ignoring] sont
     * retirés avant l'examen (p. ex. GPS_UNCERTAIN pendant une perte GPS voulue par le scénario).
     */
    fun modeOscillations(tl: Timeline, ignoring: Set<GuidanceMode> = emptySet()): List<String> =
        tl.ticks.filter { it.guiding != null }.groupBy { it.routeId to it.nextIndex }.mapNotNull { (key, ticks) ->
            val modes = ticks.mapNotNull { it.mode }.filter { it !in ignoring }.collapsed()
            if (modes.size != modes.distinct().size) "manœuvre ${key.second} (${key.first}) : ${modes.joinToString("→")}" else null
        }

    /** Reculs de l'indice de manœuvre sur un même tracé (k puis k−1). */
    fun indexRegressions(tl: Timeline): List<String> =
        tl.ticks.filter { it.guiding != null }.zipWithNext().filter { (a, b) -> a.routeId == b.routeId && b.nextIndex!! < a.nextIndex!! }
            .map { (a, b) -> "t=${b.t} : n=${a.nextIndex} → n=${b.nextIndex}" }

    /**
     * Préavis (s) de la dernière annonce de la manœuvre [index] du tracé de départ : distance vraie restante à la seconde où elle
     * est dite, divisée par la vitesse vraie. Null si rien n'a été dit pour cette manœuvre.
     */
    fun lastAnnouncementLeadS(tl: Timeline, index: Int): Double? {
        val route = tl.scenario.route
        val start = RouteTracker(route).maneuverStartM[index]
        val m = route.maneuvers[index]
        val tick = tl.ticks.lastOrNull { t -> t.routeId == route.id && t.spoken.any { isNow(it, m) || isDistance(it, m) } } ?: return null
        val a = tick.trueAlongM ?: return null
        return if (tick.trueSpeedKmh > 0.0) (start - a) / (tick.trueSpeedKmh / 3.6) else null
    }

    // --- Affichage ---

    /** Chronologie lisible, une ligne par seconde de [from] à [to] : fix, état, voix. À mettre dans les messages d'assertion. */
    fun describe(tl: Timeline, from: Int = 0, to: Int = Int.MAX_VALUE): String = buildString {
        append("Scénario « ${tl.scenario.name} » — tracé ${tl.scenario.route.id}, ${tl.ticks.size} s, ${tl.requests.size} recalcul(s) demandé(s)")
        if (tl.requests.isNotEmpty()) append(" à t=${tl.requests.map { it.t }}")
        if (tl.served.isNotEmpty()) append(", tracé rendu à t=${tl.served.map { it.t }}")
        append('\n')
        append("   t | tronçon  | fix (vrai s, vitesse, précision)   | état publié\n")
        for (tick in tl.ticks) {
            if (tick.t < from || tick.t > to) continue
            append(String.format(Locale.ROOT, "%4d | %-8s | %-34s | %s", tick.t, tick.label.take(8), fixText(tick), stateText(tick)))
            tick.spoken.forEach { append("\n       >>> « $it »") }
            append('\n')
        }
    }

    private fun fixText(tick: Tick): String {
        val where = tick.trueAlongM?.let { String.format(Locale.ROOT, "s=%.0f", it) } ?: "hors tracé"
        val f = tick.fix ?: return "aucun fix ($where)"
        val v = f.speedMps?.let { String.format(Locale.ROOT, "%.0f km/h", it * 3.6) } ?: "v ?"
        return String.format(Locale.ROOT, "%s %s ±%.0f m", where, v, f.accuracyM)
    }

    private fun stateText(tick: Tick): String {
        val g = tick.guiding ?: return tick.state::class.simpleName.orEmpty()
        val p = g.progress
        val s = g.situation
        return buildString {
            append(String.format(Locale.ROOT, "n=%d d=%.0f s=%.0f écart=%.0f %s", p.nextManeuverIndex, p.distanceToNextManeuverM, p.distanceAlongM, p.offRouteM, s.mode))
            if (s.chained) append(" enchaîné")
            s.secondsToManeuver?.let { append(String.format(Locale.ROOT, " %.0fs", it)) }
            g.roundaboutLive?.let { r ->
                append(" | rp ${r.phase} passées=${r.exitsPassed} restantes=${r.exitsRemaining ?: "?"} ${r.confidence}")
                if (r.exitIsNext) append(" sortie-suivante")
            }
            if (p.extrapolated) append(" [extrapolé]")
            if (g.gpsLost) append(" [GPS perdu]")
            if (g.rerouting) append(" [recalcul]")
            if (g.route.id != tick.vehicleRouteId) append(" [tracé ${g.route.id}]")
            append(" (req=${tick.rerouteRequests})")
        }
    }
}
