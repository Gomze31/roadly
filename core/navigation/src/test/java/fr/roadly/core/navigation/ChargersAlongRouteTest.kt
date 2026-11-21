package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.charging.ChargingStation
import fr.roadly.core.data.charging.ChargingStationProvider
import fr.roadly.core.data.charging.Connector
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargersAlongRouteTest {
    private val route = (0..60).map { GeoPoint(45.0, 1.0 + it * 0.0127) } // ~60 km vers l'est
    private val geo = RouteGeometry(route)

    private fun st(id: String, at: GeoPoint, kw: Int) = ChargingStation(id, "Borne $id", null, null, at, kw, 2, setOf(Connector.CCS), false, null, null, null, pdcIds = listOf("${id}1", "${id}2"))

    private class Fake(val all: List<ChargingStation>, val fail: Boolean = false, val statuses: Map<String, fr.roadly.core.data.charging.PdcStatus> = emptyMap()) : ChargingStationProvider {
        override suspend fun status(pdcIds: List<String>) = Outcome.Success(statuses.filterKeys { it in pdcIds })
        var calls = 0
        override suspend fun fastIn(box: BoundingBox, minKw: Int): Outcome<List<ChargingStation>> {
            synchronized(this) { calls++ }
            if (fail) return Outcome.Failure(AppError.Timeout)
            return Outcome.Success(all.filter { it.point.lat in box.south..box.north && it.point.lon in box.west..box.east && it.maxKw >= minKw })
        }
    }

    @Test fun `fast chargers near the route and ahead come in route order, duplicates from overlapping boxes once`() = runTest {
        val far = st("far", geo.pointAt(40_000.0), 150)
        val near = st("near", GeoPoint(45.0 + 0.012, geo.pointAt(15_000.0).lon), 50)   // ~1,3 km du tracé
        val off = st("off", GeoPoint(45.05, geo.pointAt(30_000.0).lon), 300)          // 5,5 km : trop loin
        val behind = st("behind", geo.pointAt(2_000.0), 100)
        val fake = Fake(listOf(far, near, off, behind))
        val out = (ChargersAlongRoute(fake).ahead(route, fromAlongM = 5_000.0) as Outcome.Success).value
        assertEquals(listOf("near", "far"), out.map { it.station.id })
        assertEquals(10_000.0, out[0].aheadM, 300.0)
        assertTrue(out[0].offsetM in 1_200.0..1_450.0)
        assertTrue("une zone tous les 20 km", fake.calls in 2..4)
    }

    @Test fun `a station whose charge points are all out of service goes last, status text is honest about its age`() = runTest {
        val now = 1_800_000_000_000L
        val a = st("a", geo.pointAt(10_000.0), 150)
        val b = st("b", geo.pointAt(20_000.0), 150)
        val down = fr.roadly.core.data.charging.PdcStatus(false, fr.roadly.core.data.charging.Occupancy.UNKNOWN, now - 3 * 86_400_000L)
        val freeNow = fr.roadly.core.data.charging.PdcStatus(true, fr.roadly.core.data.charging.Occupancy.FREE, now - 5 * 60_000L)
        val fake = Fake(listOf(a, b), statuses = mapOf("a1" to down, "a2" to down, "b1" to freeNow, "b2" to down))
        val out = (ChargersAlongRoute(fake) { now }.ahead(route, 0.0) as Outcome.Success).value
        assertEquals(listOf("b", "a"), out.map { it.station.id })
        assertTrue(out.last().status!!.allDown)
        assertEquals("1 libre sur 2 · 1 hors service sur 2 · il y a 5 min", out.first().status!!.describe(now))
        assertTrue(out.last().status!!.describe(now, java.time.ZoneOffset.UTC)!!.startsWith("hors service · relevé du "))
    }

    @Test fun `dense city chargers do not hide the rest of the trip`() = runTest {
        val city = (1..12).map { st("c$it", geo.pointAt(1_000.0 + it * 500), 50 + it) }
        val later = st("later", geo.pointAt(45_000.0), 150)
        val out = (ChargersAlongRoute(Fake(city + later)).ahead(route, 0.0) as Outcome.Success).value
        assertTrue(out.any { it.station.id == "later" })
        // Dans la première tranche de 15 km, seules les 2 plus puissantes restent.
        assertEquals(listOf("c12", "c11", "later"), out.map { it.station.id })
    }

    @Test fun `a motorway service area on the other carriageway is left out, a town charger on the left is kept`() = runTest {
        // Tracé vers l'est : le nord est à gauche.
        val at = geo.pointAt(20_000.0)
        val north = st("nord", GeoPoint(at.lat + 0.0015, at.lon), 300).copy(name = "Electra A61 - Arzens - Aire Nord")
        val south = st("sud", GeoPoint(at.lat - 0.0015, at.lon + 0.0001), 300).copy(name = "Electra A61 - Arzens - Aire Sud")
        val town = st("ville", GeoPoint(at.lat + 0.0015, at.lon + 0.03), 150).copy(name = "Carrefour Market")
        val out = (ChargersAlongRoute(Fake(listOf(north, south, town))).ahead(route, 0.0) as Outcome.Success).value
        assertEquals(setOf("sud", "ville"), out.map { it.station.id }.toSet())
    }

    @Test fun `failure everywhere is reported`() = runTest {
        assertTrue(ChargersAlongRoute(Fake(emptyList(), fail = true)).ahead(route, 0.0) is Outcome.Failure)
    }
}
