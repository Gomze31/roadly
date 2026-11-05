package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class CommunityMergeTest {
    private val origin = GeoPoint(45.0, 4.0)
    private fun pt(eastM: Double, northM: Double) =
        GeoPoint(origin.lat + northM / GeoMath.M_PER_DEG_LAT, origin.lon + eastM / (GeoMath.M_PER_DEG_LAT * cos(Math.toRadians(origin.lat))))

    private fun official(id: String, east: Double, kind: RadarKind = RadarKind.FIXED, direction: ControlledDirection? = null, north: Double = 0.0) =
        Radar(id, pt(east, north), kind, speedLimitKmh = 90, direction = direction, source = RadarSourceIds.FIXED_FR, collectedAtMs = 0, precisionM = 5.0, confidence = 80)

    private fun cam(ref: String, east: Double, kind: RadarKind = RadarKind.FIXED, approach: Double? = 90.0, north: Double = 0.0) =
        CommunityCamera(ref, pt(east, north), kind, 90, approach)

    @Test fun `close compatible report corroborates and gives a direction hint`() {
        val r = CommunityMerge.merge(listOf(official("A", 0.0)), listOf(cam("x", 20.0)), 1L, "2024-05-21")
        val a = r.radars.single()
        assertEquals(85, a.confidence)
        assertEquals(90.0, a.direction!!.bearingDeg!!, 0.0)
        assertTrue(a.direction!!.hint)
        // Un sens indicatif n'est jamais un sens connu : il n'écarte rien.
        assertFalse(a.direction!!.isKnownOneWay)
        assertEquals(CommunityReport(read = 1, corroborated = 1, directionHints = 1, added = 0, ignored = 0), r.report)
    }

    @Test fun `known official direction is kept`() {
        val known = ControlledDirection(270.0, false, "Est vers Ouest")
        val r = CommunityMerge.merge(listOf(official("A", 0.0, direction = known)), listOf(cam("x", 10.0)), 1L, null)
        assertEquals(known, r.radars.single().direction)
        assertEquals(0, r.report.directionHints)
    }

    @Test fun `far report becomes a community radar, middle distance is ignored`() {
        val r = CommunityMerge.merge(listOf(official("A", 0.0)), listOf(cam("mid", 75.0), cam("far", 2_000.0, approach = null)), 1L, "2024-05-21")
        val added = r.radars.single { it.source == RadarSourceIds.MAPATLAS }
        assertEquals("MA-far", added.id)
        assertEquals(CommunityMerge.COMMUNITY_CONFIDENCE, added.confidence)
        assertTrue(added.confidence < RadarQuality.MIN_VOICE)
        assertNull(added.direction)
        assertEquals("2024-05-21", added.sourceVersion)
        assertEquals(1, r.report.ignored)
        assertEquals(80, r.radars.single { it.id == "A" }.confidence)
    }

    @Test fun `incompatible kinds never match`() {
        // Signalement « feu rouge » à 10 m d'un radar de vitesse : ni confirmation, ni nouvel équipement.
        val r = CommunityMerge.merge(listOf(official("A", 0.0)), listOf(cam("red", 10.0, kind = RadarKind.RED_LIGHT)), 1L, null)
        assertEquals(1, r.radars.size)
        assertEquals(80, r.radars.single().confidence)
        assertEquals(1, r.report.ignored)
    }

    @Test fun `nearest of several reports wins`() {
        val r = CommunityMerge.merge(listOf(official("A", 0.0)), listOf(cam("far", 40.0, approach = 270.0), cam("near", 5.0, approach = 90.0)), 1L, null)
        assertEquals(90.0, r.radars.single().direction!!.bearingDeg!!, 0.0)
        assertEquals(1, r.report.corroborated)
    }

    @Test fun `mapatlas classes and approach letters`() {
        assertEquals(RadarKind.RED_LIGHT, CommunityMerge.kindOf("signal"))
        assertEquals(RadarKind.SECTION, CommunityMerge.kindOf("corridor"))
        assertEquals(RadarKind.FIXED, CommunityMerge.kindOf("spot"))
        assertEquals(RadarKind.UNKNOWN, CommunityMerge.kindOf("mobile"))
        assertEquals(225.0, CommunityMerge.approachDeg("sw")!!, 0.0)
        assertNull(CommunityMerge.approachDeg("both"))
        assertNull(CommunityMerge.approachDeg(null))
    }

    // ---- Sens indicatif dans le moteur sans itinéraire ----

    private fun observe(r: Radar, eastFromRadar: Double, heading: Double): List<ZoneObservation> {
        val sample = DriveSample(pt(eastFromRadar, 0.0), heading, 0.0, 30f, 5f, null)
        return FreeRadarMatcher.match(RadarIndex(listOf(r)), sample, emptySet()).observations
    }

    @Test fun `agreeing hint confirms early, opposite hint only delays`() {
        val agreeing = official("hint-ok", 0.0, direction = ControlledDirection(90.0, false, "MapAtlas", hint = true))
        // 1 500 m avant, cap est : le sens indicatif concorde → zone ouverte comme un sens connu.
        assertEquals(1, observe(agreeing, -1_500.0, 90.0).size)
        val wrong = official("hint-wrong", 0.0, direction = ControlledDirection(270.0, false, "MapAtlas", hint = true))
        // Indication fausse : pas d'alerte précoce…
        assertTrue(observe(wrong, -1_500.0, 90.0).isEmpty())
        // … mais l'équipement n'est jamais écarté : confirmé à courte distance comme un sens inconnu.
        assertEquals(1, observe(wrong, -200.0, 90.0).size)
        val unknown = official("unknown", 0.0)
        assertTrue(observe(unknown, -1_500.0, 90.0).isEmpty())
    }

    @Test fun `route matcher ignores hints for rejection`() {
        val route = Polyline((0..10).map { pt(it * 1_000.0 - 5_000.0, 0.0) })
        val oppositeHint = official("h", 0.0, direction = ControlledDirection(270.0, false, "MapAtlas", hint = true))
        assertEquals(listOf("h"), RouteRadarMatcher.match(route, listOf(oppositeHint)).zones.map { it.key })
    }
}
