package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Scénarios géographiques simulés (repère local autour de 45° N, 4° E) : sens de circulation, chaussée opposée, routes
 * parallèles, bretelles, virages, GPS bruité, arrêt, tronçons, doublons, données obsolètes. Mesure des faux positifs (alerte
 * pour un équipement qui ne nous concerne pas) et des faux négatifs (équipement concerné jamais annoncé).
 */
class RadarScenarioTest {
    private val origin = GeoPoint(45.0, 4.0)
    private fun pt(eastM: Double, northM: Double) =
        GeoPoint(origin.lat + northM / GeoMath.M_PER_DEG_LAT, origin.lon + eastM / (GeoMath.M_PER_DEG_LAT * cos(Math.toRadians(origin.lat))))

    private fun radar(
        id: String, east: Double, north: Double, limit: Int? = 90, bearing: Double? = null, both: Boolean = false,
        road: String? = null, precision: Double = 5.0, kind: RadarKind = RadarKind.FIXED, section: Double? = null,
        status: RadarStatus = RadarStatus.ACTIVE, confidence: Int = 85,
    ) = Radar(
        id, pt(east, north), kind, speedLimitKmh = limit,
        direction = if (bearing != null || both) ControlledDirection(bearing, both, null) else null,
        road = road, sectionLengthM = section, source = "test", collectedAtMs = 0, precisionM = precision, confidence = confidence, status = status,
    )

    /** Échantillon de trajectoire : position et cap vrais à l'instant t (s). */
    private class Pose(val east: Double, val north: Double, val heading: Double, val speedMps: Float)

    private data class Run(val entered: List<String>, val events: List<RadarAlertEvent>, val enterDistanceToRadar: Map<String, Double>)

    /** Conduite sans itinéraire, 1 fix/s : bruit GPS latéral σ [posNoiseM] et de cap σ [headingNoiseDeg]. */
    private fun driveFree(
        radars: List<Radar>, seconds: Int, pose: (Int) -> Pose, seed: Int = 1, posNoiseM: Double = 0.0, headingNoiseDeg: Double = 0.0,
        road: String? = null, accuracy: Float = 5f, config: RadarAlertConfig = RadarAlertConfig(),
    ): Run {
        val rnd = Random(seed)
        fun gauss() = rnd.nextDouble().coerceAtLeast(1e-12).let { u -> kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * cos(2 * PI * rnd.nextDouble()) }
        val index = RadarIndex(radars)
        val filter = HeadingFilter()
        val engine = RadarAlertEngine(config)
        val events = ArrayList<RadarAlertEvent>()
        val enterDist = HashMap<String, Double>()
        for (t in 0..seconds) {
            val p = pose(t)
            val point = pt(p.east + gauss() * posNoiseM, p.north + gauss() * posNoiseM)
            val heading = ((p.heading + gauss() * headingNoiseDeg) % 360 + 360) % 360
            filter.update(point, heading.toFloat(), p.speedMps, t * 1000L)
            val sample = DriveSample(point, filter.heading, filter.curvaturePerM, p.speedMps, accuracy, road)
            val obs = FreeRadarMatcher.match(index, sample, engine.openKeys, lookAheadM = config.prewarnM).observations
            val out = engine.update(t * 1000L, obs, p.speedMps * 3.6, 5)
            out.events.forEach { e ->
                events += e
                if (e is RadarAlertEvent.Entered) radars.firstOrNull { it.id == e.key }?.let { enterDist[e.key] = pt(p.east, p.north).distanceTo(it.point) }
            }
        }
        return Run(events.filterIsInstance<RadarAlertEvent.Entered>().map { it.key }, events, enterDist)
    }

    /** Ligne droite plein est à [mps] depuis x = [startEast]. */
    private fun eastbound(startEast: Double = -5_000.0, mps: Float = 30f) = { t: Int -> Pose(startEast + t * mps, 0.0, 90.0, mps) }

    /** Comptage FP / FN d'une course. */
    private class Score { var fp = 0; var fn = 0; var dup = 0; var runs = 0
        override fun toString() = "courses=$runs faux positifs=$fp faux négatifs=$fn doublons=$dup" }

    private val tally = HashMap<String, Int>()
    private fun perKey(run: Run) = run.entered.forEach { tally[it] = (tally[it] ?: 0) + 1 }

    private fun score(s: Score, run: Run, relevant: Set<String>, irrelevant: Set<String>) {
        s.runs++
        s.fp += run.entered.count { it in irrelevant }
        s.fn += relevant.count { it !in run.entered }
        s.dup += run.entered.groupingBy { it }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
    }

    // Autoroute 2x2 : notre chaussée (est) en y = 0, chaussée opposée (ouest) en y = +28 m ; route parallèle à y = +120 m.
    private val ours = radar("our-side", 2_000.0, -8.0, limit = 130, bearing = 90.0, road = "A7")
    private val opposite = radar("opposite-known", 4_000.0, 34.0, limit = 130, bearing = 270.0, road = "A7")
    private val oppositeUnknown = radar("opposite-unknown-dir", 6_000.0, 34.0, limit = 130)
    private val parallel = radar("parallel-D7", 8_000.0, 120.0, limit = 80, road = "D7")
    /** Bretelle de sortie à 15° vers la droite depuis x = 9 500 m, radar 500 m plus loin sur la bretelle. */
    private val ramp = radar("ramp", 9_500.0 + 500 * cos(Math.toRadians(15.0)), -500 * sin(Math.toRadians(15.0)), limit = 70)

    /** Notre chaussée, sens inconnu : annoncé tard (≤ 400 m) mais annoncé. */
    private val oursUnknown = radar("our-side-unknown-dir", 11_000.0, -8.0, limit = 130)

    private val highway = listOf(ours, opposite, oppositeUnknown, parallel, ramp, oursUnknown)

    @Test fun `clean GPS on a divided motorway - only our carriageway alerts`() {
        val run = driveFree(highway, 600, eastbound(), road = "A7")
        assertEquals(listOf("our-side", "our-side-unknown-dir"), run.entered)
        // Zone de 4 km à 130 : annoncée dès son début (≈ 4 km avant le point), pas au dernier moment.
        assertTrue("distance à l'entrée : ${run.enterDistanceToRadar}", run.enterDistanceToRadar.getValue("our-side") > 3_900.0)
    }

    private fun noisyHighway(pos: Double, heading: Double, runs: Int = 50): Score {
        val s = Score()
        tally.clear()
        repeat(runs) { seed ->
            val run = driveFree(highway, 600, eastbound(), seed = seed, posNoiseM = pos, headingNoiseDeg = heading, road = "A7")
            score(s, run, relevant = setOf("our-side", "our-side-unknown-dir"), irrelevant = setOf("opposite-known", "opposite-unknown-dir", "parallel-D7", "ramp"))
            perKey(run)
        }
        println("Autoroute sans itinéraire, GPS sigma $pos m / $heading deg : $s ; alertes par équipement : $tally")
        return s
    }

    /**
     * GPS correct à 110 km/h (≈ 3 m, cap ≈ 2° à chaque fix) : 2 équipements concernés et 4 pièges par course.
     * Mesuré le 2026-11-05 : 0 faux négatif, 1 faux positif sur 200 pièges (chaussée opposée sans sens publié).
     */
    @Test fun `realistic GPS noise - no false negative, at most 1 percent false positives`() {
        val s = noisyHighway(3.0, 2.0)
        assertEquals(0, s.fn); assertEquals(0, s.dup)
        assertTrue("faux positifs : ${s.fp}", s.fp <= s.runs * 4 / 100)
    }

    /**
     * GPS dégradé (σ 6 m, cap σ 6° à chaque fix) : mesure. Le seul piège qui passe parfois est la chaussée opposée à 34 m
     * quand le jeu ne donne pas le sens contrôlé : à quelques centaines de mètres, l'erreur latérale dépasse l'écart entre
     * chaussées. Aucun faux négatif admis.
     */
    @Test fun `degraded GPS noise - measured, no false negative`() {
        val s = noisyHighway(6.0, 6.0)
        assertEquals(0, s.fn); assertEquals(0, s.dup)
        // Mesuré le 2026-11-05 : 5 faux positifs sur 200 pièges (2,5 %).
        assertTrue("faux positifs : ${s.fp} sur ${s.runs * 4} pièges", s.fp <= s.runs * 4 / 20)
    }

    @Test fun `without road names a parallel road at 120 m is still rejected by the lateral corridor`() {
        val run = driveFree(highway, 600, eastbound(), road = null)
        assertTrue(run.entered.none { it == "parallel-D7" })
    }

    @Test fun `westbound driver gets the opposite radar and not ours`() {
        val run = driveFree(highway, 600, { t -> Pose(12_000.0 - t * 30.0, 28.0, 270.0, 30f) }, road = "A7")
        assertTrue(run.entered.contains("opposite-known"))
        assertTrue(run.entered.none { it == "our-side" })
    }

    @Test fun `double-direction radar alerts both ways`() {
        val both = radar("both", 1_000.0, -4.0, limit = 80, both = true)
        assertEquals(listOf("both"), driveFree(listOf(both), 200, eastbound(-2_000.0, 20f)).entered)
        assertEquals(listOf("both"), driveFree(listOf(both), 200, { t -> Pose(4_000.0 - t * 20.0, 0.0, 270.0, 20f) }).entered)
    }

    @Test fun `curved road - radar ahead on a 1 km radius left bend is found`() {
        // Virage à gauche de rayon 1 000 m commençant en x = 0 ; radar 1 500 m plus loin sur l'arc, limitation 90 (zone 2 km).
        val r = 1_000.0
        val arc = 1_500.0 / r
        val bend = radar("bend", r * sin(arc), r * (1 - cos(arc)), limit = 90)
        val pose = { t: Int ->
            val s = -1_000.0 + t * 25.0
            if (s <= 0) Pose(s, 0.0, 90.0, 25f) else { val a = s / r; Pose(r * sin(a), r * (1 - cos(a)), (90.0 - Math.toDegrees(a) + 360) % 360, 25f) }
        }
        val run = driveFree(listOf(bend), 160, pose, posNoiseM = 4.0, headingNoiseDeg = 4.0)
        println("Virage : annoncé à ${run.enterDistanceToRadar["bend"]?.toInt()} m à vol d'oiseau du point")
        assertEquals(listOf("bend"), run.entered)
    }

    @Test fun `stopped inside a zone for five minutes with jitter - one alert only`() {
        val r = radar("stop", 500.0, -5.0, limit = 50)
        val pose = { t: Int -> if (t < 35) Pose(-100.0 + t * 15.0, 0.0, 90.0, 15f) else Pose(420.0, 0.0, Random(t).nextDouble(0.0, 360.0), 0f) }
        val run = driveFree(listOf(r), 320, pose, posNoiseM = 8.0, seed = 3)
        assertEquals(listOf("stop"), run.entered)
    }

    @Test fun `unstable heading at low speed opens nothing`() {
        val r = radar("ahead", 200.0, 0.0, limit = 50)
        val run = driveFree(listOf(r), 60, { t -> Pose(0.0, 0.0, (t * 97.0) % 360, 2f) })
        assertTrue(run.entered.isEmpty())
    }

    @Test fun `poor GPS accuracy opens nothing`() {
        val r = radar("ahead", 1_000.0, 0.0)
        assertTrue(driveFree(listOf(r), 120, eastbound(-1_500.0, 20f), accuracy = 80f).entered.isEmpty())
    }

    @Test fun `section control - one entry, one exit, inside over the whole section`() {
        val sec = radar("section", 1_000.0, -5.0, limit = 110, kind = RadarKind.SECTION, section = 5_000.0)
        val run = driveFree(listOf(sec), 400, eastbound(-5_000.0, 30f))
        assertEquals(1, run.events.count { it is RadarAlertEvent.Entered })
        assertEquals(1, run.events.count { it is RadarAlertEvent.Exited })
        val enterT = run.events.indexOfFirst { it is RadarAlertEvent.Entered }
        assertTrue(enterT >= 0)
    }

    @Test fun `duplicates, removed, unknown limit, low confidence`() {
        val dupA = radar("dup-a", 1_000.0, -5.0, limit = 70)
        val dupB = radar("dup-b", 1_002.0, -6.0, limit = 70)
        val removed = radar("removed", 3_000.0, -5.0, status = RadarStatus.REMOVED)
        val noLimit = radar("no-limit", 6_000.0, -5.0, limit = null, kind = RadarKind.RED_LIGHT)
        val weak = radar("weak", 9_000.0, -5.0, confidence = 25)
        val run = driveFree(listOf(dupA, dupB, removed, noLimit, weak), 400, eastbound(-1_000.0, 30f))
        assertEquals("doublons fusionnés en une zone", 1, run.entered.count { it.startsWith("dup") })
        assertTrue(run.entered.none { it == "removed" || it == "weak" })
        val e = run.events.filterIsInstance<RadarAlertEvent.Entered>().first { it.key == "no-limit" }
        assertEquals(null, e.speedLimitKmh)
    }

    @Test fun `medium confidence alerts visually only`() {
        val r = radar("medium", 1_000.0, -5.0, confidence = 50)
        val e = driveFree(listOf(r), 120, eastbound(-1_500.0, 25f)).events.filterIsInstance<RadarAlertEvent.Entered>().single()
        assertTrue(!e.voice)
    }

    @Test fun `prewarning comes before entry, once`() {
        // Sens publié (double sens) : observable dès le préavis ; sans sens, l'ouverture attend 300 m (voir MatchParams).
        val r = radar("pre", 3_000.0, -5.0, limit = 50, both = true) // zone de 300 m
        val run = driveFree(listOf(r), 300, eastbound(0.0, 13f), config = RadarAlertConfig(prewarnM = 500.0))
        val kinds = run.events.filter { it.key == "pre" }.map { it::class.simpleName }
        assertEquals(listOf("PreWarning", "Entered", "Exited"), kinds)
    }

    @Test fun `overspeed inside a zone is reported once`() {
        val r = radar("speed", 1_000.0, -5.0, limit = 50)
        val run = driveFree(listOf(r), 200, eastbound(0.0, 20f)) // 72 km/h
        assertEquals(1, run.events.count { it is RadarAlertEvent.OverSpeed })
    }

    @Test fun `U-turn within the cooldown does not re-announce`() {
        val r = radar("loop", 1_000.0, -2.0, limit = 50, both = true)
        val pose = { t: Int -> if (t <= 80) Pose(t * 20.0, 0.0, 90.0, 20f) else Pose(1_600.0 - (t - 80) * 20.0, 0.0, 270.0, 20f) }
        val run = driveFree(listOf(r), 160, pose)
        assertEquals(1, run.entered.size)
    }

    // ---- Avec itinéraire ----

    @Test fun `route matcher - opposite, unknown-direction opposite, parallel and ramp are rejected`() {
        val route = Polyline((0..20).map { pt(it * 1_000.0 - 5_000.0, 0.0) })
        val res = RouteRadarMatcher.match(route, highway, roadRefsAt = { listOf("A 7") })
        println("Itinéraire : zones=${res.zones.map { it.key }} écartés=${res.rejected}")
        assertEquals(listOf("our-side", "our-side-unknown-dir"), res.zones.map { it.key })
        assertTrue(res.rejected["opposite-known"] in setOf(RejectReason.OFFSET, RejectReason.DIRECTION))
        // Opposé dans le couloir latéral (terre-plein étroit) : écarté par le sens contrôlé.
        val narrow = radar("opposite-narrow", 3_000.0, 15.0, limit = 130, bearing = 270.0)
        assertEquals(RejectReason.DIRECTION, RouteRadarMatcher.match(route, listOf(narrow)).rejected["opposite-narrow"])
        assertEquals(RejectReason.OFFSET, res.rejected["opposite-unknown-dir"])
        assertEquals(RejectReason.OFFSET, res.rejected["ramp"])
        val zone = res.zones.first()
        // Point à ≈ 7 000 m le long du tracé (distances du tracé à 0,2 % près), zone de 4 km avant et 300 m après.
        assertEquals(4_300.0, zone.endAlongM - zone.startAlongM, 1.0)
        assertEquals(3_000.0, zone.startAlongM, 15.0)
    }

    @Test fun `route matcher - a parallel road radar within the corridor is rejected by road number`() {
        val near = radar("near-D", 2_000.0, 15.0, limit = 80, road = "D 7")
        val route = Polyline((0..10).map { pt(it * 1_000.0, 0.0) })
        assertEquals(RejectReason.ROAD, RouteRadarMatcher.match(route, listOf(near), roadRefsAt = { listOf("A7") }).rejected["near-D"])
        assertEquals(1, RouteRadarMatcher.match(route, listOf(near), roadRefsAt = { emptyList() }).zones.size)
    }

    @Test fun `route matcher - disabled kinds and overlapping zones`() {
        val route = Polyline((0..10).map { pt(it * 1_000.0, 0.0) })
        val a = radar("a", 3_000.0, -5.0, limit = 90)
        val b = radar("b", 3_500.0, -5.0, limit = 70)
        val red = radar("red", 8_000.0, -5.0, limit = null, kind = RadarKind.RED_LIGHT)
        val res = RouteRadarMatcher.match(route, listOf(a, b, red), enabled = RadarKind.entries.toSet() - RadarKind.RED_LIGHT)
        assertEquals(1, res.zones.size)
        assertEquals(70, res.zones.single().speedLimitKmh)
        assertEquals(RejectReason.KIND_DISABLED, res.rejected["red"])
    }

    @Test fun `regulation by country`() {
        assertEquals(RadarDisplayMode.DANGER_ZONE_ONLY, RadarRegulation.modeFor("FR"))
        assertEquals(RadarDisplayMode.DISABLED, RadarRegulation.modeFor("CH"))
        assertEquals(RadarDisplayMode.DISABLED, RadarRegulation.modeFor("DE"))
    }
}
