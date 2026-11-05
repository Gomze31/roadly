package fr.roadly.core.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jeux officiels réels (Licence Ouverte 2.0) : liste DSR de décembre 2025 et « Radars automatiques » 2018. */
class RadarDataTest {
    private fun resource(name: String, latin1: Boolean): String {
        val bytes = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()
        return if (latin1) String(bytes, Charsets.ISO_8859_1) else String(bytes, Charsets.UTF_8)
    }

    private val fixed by lazy { RadarSources.parseFixedList(resource("radars-fixes-2025-12.csv", latin1 = true)) }
    private val old by lazy { RadarSources.parse2018(resource("radars-automatiques-2018.csv", latin1 = false)) }

    @Test fun `2025 list parses all rows with official codes`() {
        assertEquals(3309, fixed.size)
        val kinds = fixed.groupingBy { RadarKind.fromCode(it.kindCode) }.eachCount()
        assertEquals(1492, kinds[RadarKind.NEW_GENERATION])
        assertEquals(66, kinds[RadarKind.SECTION])
        assertEquals(27, kinds[RadarKind.URBAN])
        assertNull(kinds[RadarKind.UNKNOWN])
    }

    @Test fun `utf-8 misdecoding of the latin-1 header is tolerated`() {
        val asUtf8 = String(javaClass.classLoader!!.getResourceAsStream("radars-fixes-2025-12.csv")!!.readBytes(), Charsets.UTF_8)
        assertEquals(3309, RadarSources.parseFixedList(asUtf8).size)
    }

    @Test fun `merge keeps the official list, enriches from 2018 and reports quality`() {
        val r = RadarMerger.merge(fixed, old, collectedAtMs = 1L, sourceVersion = "2025-12")
        val rep = r.report
        println("Fusion réelle : $rep")
        assertEquals(3309, rep.read)
        assertEquals(rep.read - rep.rejectedInvalid - rep.rejectedDuplicateId, rep.kept)
        assertTrue("enrichis : ${rep.enriched}", rep.enriched >= 2_000)
        // Mesuré le 2026-11-05 : 674 équipements avec un sens exploitable (cardinal ou double sens) sur 3 309.
        assertTrue("sens connu : ${rep.withDirection}", rep.withDirection >= 600)
        // NA dans la colonne VMA : jamais remplacé par une valeur devinée.
        assertTrue(r.radars.count { it.speedLimitKmh == null } >= 600)
        // Tronçons : longueur reprise du jeu 2018 quand l'équipement est apparié.
        assertTrue(r.radars.any { it.kind == RadarKind.SECTION && it.sectionLengthM != null })
        assertTrue(r.radars.all { it.source == RadarSourceIds.FIXED_FR })
        assertTrue(r.radars.filter { it.enrichedFrom == null }.all { it.direction == null && it.road == null })
    }

    @Test fun `invalid rows and duplicate ids are rejected, not repaired`() {
        val rows = listOf(
            RawRadar("1", 45.0, 4.0, "45.00000", "4.00000", "ETF", speedLimitText = "90"),
            RawRadar("1", 45.1, 4.1, "45.10000", "4.10000", "ETF"),
            RawRadar("2", 0.0, 0.0, "0", "0", "ETF"),
            RawRadar(null, 45.0, 4.0, "45", "4", "ETF"),
            RawRadar("3", 45.2, 4.2, "45.2", "4.2", "ETF", speedLimitText = "95"),
        )
        val r = RadarMerger.merge(rows, emptyList(), 0, null)
        assertEquals(listOf("1", "3"), r.radars.map { it.id })
        assertEquals(2, r.report.rejectedInvalid)
        assertEquals(1, r.report.rejectedDuplicateId)
        assertNull("95 n'est pas une limitation réglementaire", r.radars[1].speedLimitKmh)
        assertTrue("1 décimale : ≈ 5,6 km d'incertitude, score bas", r.radars[1].confidence < RadarQuality.MIN_ALERT)
    }

    @Test fun `precision follows published decimals`() {
        assertEquals(5.0, RadarQuality.precisionFromText("45.123456", "4.123456"), 1e-9)
        assertEquals(55.66, RadarQuality.precisionFromText("45.123", "4.12345"), 0.01)
    }

    @Test fun `direction texts`() {
        assertEquals(180.0, DirectionParser.parse("Nord vers Sud")!!.bearingDeg!!, 1e-9)
        assertEquals(45.0, DirectionParser.parse("Sud-Ouest vers Nord-Est")!!.bearingDeg!!, 1e-9)
        assertEquals(135.0, DirectionParser.parse("Nord Ouest vers Sud Est")!!.bearingDeg!!, 1e-9)
        assertTrue(DirectionParser.parse("DOUBLE SENS")!!.bothWays)
        val city = DirectionParser.parse("PARIS vers LYON")!!
        assertNull(city.bearingDeg); assertFalse(city.isKnownOneWay); assertEquals("PARIS vers LYON", city.label)
        assertNull("incohérent : pas un sens", DirectionParser.parse("Nord vers Est")!!.bearingDeg)
        assertNull(DirectionParser.parse(""))
    }

    @Test fun `road refs`() {
        assertEquals("N12", RoadRef.normalize("RN12"))
        assertEquals("A7", RoadRef.normalize("A 7"))
        assertNull(RoadRef.normalize("Avenue Victoria"))
        assertTrue(RoadRef.conflict("A7", listOf("D 7")))
        assertFalse(RoadRef.conflict("A7", listOf("A 7", "E 15")))
        assertFalse("route courante inconnue", RoadRef.conflict("A7", listOf("Rue de la Paix")))
    }

    @Test fun `diff between versions`() {
        val base = RadarMerger.merge(fixed.take(100), emptyList(), 0, null).radars
        val next = base.drop(10).map { if (it.id == base[20].id) it.copy(point = GeoMathTestPoints.shift(it.point, 100.0)) else it } +
            base.first().copy(id = "nouveau")
        assertEquals(RadarDiff(added = 1, removed = 10, moved = 1, limitChanged = 0), RadarDiff.of(base, next))
    }
}

internal object GeoMathTestPoints {
    fun shift(p: fr.roadly.core.model.GeoPoint, northM: Double) = fr.roadly.core.model.GeoPoint(p.lat + northM / GeoMath.M_PER_DEG_LAT, p.lon)
}
