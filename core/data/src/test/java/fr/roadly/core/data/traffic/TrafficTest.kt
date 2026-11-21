package fr.roadly.core.data.traffic

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class TrafficTest {
    private fun fixture() = javaClass.classLoader!!.getResource("fixtures/bison_fute_extrait.xml")!!.readText()
    /** 9 novembre 2026, 12 h : le bouchon de l'A47 (créé à 11 h 49) est en cours. */
    private val noon = OffsetDateTime.parse("2026-11-09T12:00:00+01:00").toInstant().toEpochMilli()

    private fun at(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test fun `real Bison Futé extract gives jam, breakdown, accident and closure, and ignores rerouting`() {
        val events = DatexParser.parse(fixture(), at("2026-11-09T17:00:00+01:00"))
        val kinds = events.map { it.kind }.toSet()
        for (k in listOf(TrafficKind.JAM, TrafficKind.BROKEN_DOWN, TrafficKind.ACCIDENT)) {
            assertTrue("$k absent de ${events.map { it.kind to it.id }}", k in kinds)
        }
        // Travaux de l'A47 terminés à 14 h 42 : présents à midi, plus à 17 h.
        assertTrue(DatexParser.parse(fixture(), noon).any { it.kind == TrafficKind.ROADWORK && it.id == "261109-000952-1" })
        assertTrue(events.none { it.id == "261109-000952-1" })
        // Fermeture annoncée pour le 3 décembre : présente ce jour-là seulement.
        assertFalse(TrafficKind.CLOSURE in kinds)
        assertTrue(DatexParser.parse(fixture(), at("2026-12-03T10:00:00+01:00")).any { it.kind == TrafficKind.CLOSURE })
        assertTrue("une déviation seule ne donne aucun événement", events.none { it.id.startsWith("260113-001342") })
        val jam = events.first { it.kind == TrafficKind.JAM }
        assertEquals("A47", jam.road)
        assertEquals("De Saint-Étienne vers Lyon", jam.direction)
        assertEquals(2757, jam.queueLengthM)
        assertEquals(2, jam.points.size)
        assertEquals(GeoPoint(45.494045, 4.536312), jam.points[0])
    }

    @Test fun `an event starting more than an hour later is not shown yet`() {
        assertTrue(DatexParser.parse(fixture(), noon).none { it.kind == TrafficKind.ACCIDENT })
    }

    @Test fun `records whose end time has passed are dropped`() {
        val later = OffsetDateTime.parse("2027-02-01T12:00:00+01:00").toInstant().toEpochMilli()
        val events = DatexParser.parse(fixture(), later)
        assertTrue(events.none { it.endEpochMs != null && it.endEpochMs!! < later })
    }

    @Test fun `road numbers are shortened`() {
        assertEquals("A47", DatexParser.normalizeRoad("A0047"))
        assertEquals("N20", DatexParser.normalizeRoad("N0020"))
        assertEquals("RN88", DatexParser.normalizeRoad("RN88"))
    }

    private val a47 = listOf(GeoPoint(45.4900, 4.5250), GeoPoint(45.494045, 4.536312), GeoPoint(45.503475, 4.5658755), GeoPoint(45.5060, 4.5750))

    @Test fun `a jam on the route is placed along it, in the driving direction only`() {
        val jam = DatexParser.parse(fixture(), noon).first { it.kind == TrafficKind.JAM }
        val hit = TrafficOnRoute.along(a47, listOf(jam)).singleOrNull()
        assertNotNull("même sens (Saint-Étienne → Lyon) : concerné", hit)
        assertTrue(hit!!.alongM in 800.0..1100.0)
        assertTrue("sens inverse : autre chaussée", TrafficOnRoute.along(a47.reversed(), listOf(jam)).isEmpty())
    }

    @Test fun `events far from the route are ignored`() {
        val events = DatexParser.parse(fixture(), noon)
        val elsewhere = listOf(GeoPoint(43.60, 1.44), GeoPoint(43.61, 1.45))
        assertTrue(TrafficOnRoute.along(elsewhere, events).isEmpty())
    }
}

/** Contrôle sur le flux complet, si fourni : -PtrafficFile=/chemin/content.xml (inactif sinon). */
class TrafficFullFeedTest {
    @Test fun `the full national feed parses quickly`() {
        val path = System.getProperty("roadly.traffic.file")
        org.junit.Assume.assumeTrue(path != null && java.io.File(path).exists())
        val xml = java.io.File(path!!).readText()
        val t0 = System.nanoTime()
        val events = DatexParser.parse(xml, System.currentTimeMillis())
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("événements=${events.size} en ${ms} ms : " + events.groupingBy { it.kind }.eachCount())
        assertTrue(events.size > 50)
        assertTrue("lecture trop lente : $ms ms", ms < 5_000)
    }
}
