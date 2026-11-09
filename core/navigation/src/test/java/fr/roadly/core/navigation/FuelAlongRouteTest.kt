package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.model.FuelPrice
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelAlongRouteTest {
    /** Tracé ouest → est de ~40 km sur le parallèle 45°. */
    private val route = (0..40).map { GeoPoint(45.0, 1.0 + it * 0.0127) }
    private val geo = RouteGeometry(route)

    private fun station(id: String, at: GeoPoint, diesel: Double?) =
        FuelStation(id, "Station $id", null, at, listOfNotNull(diesel?.let { FuelPrice(FuelType.DIESEL, it, null) }), false, emptyList())

    private class Fake(val all: List<FuelStation>, val fail: Boolean = false) : FuelStationProvider {
        override val id = "fake"
        val asked = mutableListOf<GeoPoint>()
        override suspend fun nearby(near: GeoPoint, radiusKm: Int, limit: Int): Outcome<List<FuelStation>> {
            synchronized(asked) { asked += near }
            if (fail) return Outcome.Failure(AppError.Timeout)
            return Outcome.Success(all.filter { it.point.distanceTo(near) <= radiusKm * 1000.0 })
        }
    }

    @Test fun `stations ahead and near the route are sorted by price, the others are left out`() = runTest {
        val cheapFar = station("cheap", GeoPoint(45.0 + 0.009, geo.pointAt(30_000.0).lon), 1.62)   // 1 km du tracé, 30 km devant
        val dear = station("dear", geo.pointAt(5_000.0), 1.79)                                       // sur le tracé, 5 km devant
        val behind = station("behind", geo.pointAt(1_000.0), 1.50)                                   // derrière la position
        val offRoute = station("off", GeoPoint(45.05, geo.pointAt(10_000.0).lon), 1.40)              // 5,5 km du tracé
        val noDiesel = station("nod", geo.pointAt(12_000.0), null)
        val fake = Fake(listOf(cheapFar, dear, behind, offRoute, noDiesel))
        val out = (FuelAlongRoute(fake).ahead(route, fromAlongM = 2_000.0, fuel = FuelType.DIESEL) as Outcome.Success).value
        assertEquals(listOf("cheap", "dear", "nod"), out.map { it.station.id })
        assertEquals(28_000.0, out[0].aheadM, 300.0)
        assertEquals(1_000.0, out[0].offsetM, 50.0)
        assertEquals(2_000.0, out[0].detourM, 100.0)
        // Un cercle tous les 15 km le long des 38 km restants.
        assertEquals(3, fake.asked.size)
    }

    @Test fun `a motorway station on the left serves the other carriageway and is left out`() = runTest {
        // Tracé vers l'est : le nord est à gauche, le sud à droite.
        val at = geo.pointAt(20_000.0)
        val north = station("nord", GeoPoint(at.lat + 0.0007, at.lon), 1.70).copy(onMotorway = true)
        val south = station("sud", GeoPoint(at.lat - 0.0007, at.lon), 1.75).copy(onMotorway = true)
        val town = station("ville", GeoPoint(at.lat + 0.005, at.lon), 1.72)
        val out = (FuelAlongRoute(Fake(listOf(north, south, town))).ahead(route, 0.0, FuelType.DIESEL) as Outcome.Success).value
        assertEquals(listOf("ville", "sud"), out.map { it.station.id })
    }

    @Test fun `a provider failure everywhere is reported, a partial one is not`() = runTest {
        val failing = Fake(emptyList(), fail = true)
        assertTrue(FuelAlongRoute(failing).ahead(route, 0.0, FuelType.DIESEL) is Outcome.Failure)
        // Chaque cercle est retenté une fois.
        assertEquals(2 * 3, failing.asked.size)
        val empty = FuelAlongRoute(Fake(emptyList())).ahead(route, 0.0, FuelType.DIESEL)
        assertEquals(emptyList<StationAhead>(), (empty as Outcome.Success).value)
    }

    @Test fun `point at interpolates along the geometry`() {
        val p = geo.pointAt(geo.totalM / 2)
        assertEquals(1.254, p.lon, 0.001)
        assertEquals(route.first(), geo.pointAt(-5.0))
        assertEquals(route.last().lon, geo.pointAt(geo.totalM + 100).lon, 1e-9)
    }
}
