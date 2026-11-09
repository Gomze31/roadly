package fr.roadly.core.routing

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelSpeedModelTest {
    @Test fun `consumption is lowest around 80 km per h and 110 saves about 17 percent on 130`() {
        assertTrue(FuelSpeedModel.factor(80.0) < FuelSpeedModel.factor(30.0))
        assertTrue(FuelSpeedModel.factor(80.0) < FuelSpeedModel.factor(130.0))
        val saving = 1 - FuelSpeedModel.factor(110.0) / FuelSpeedModel.factor(130.0)
        assertEquals(0.17, saving, 0.03)
        // Électrique : la ville est sobre, l'autoroute coûte.
        assertTrue(FuelSpeedModel.factor(40.0, FuelType.ELECTRIC) < FuelSpeedModel.factor(130.0, FuelType.ELECTRIC))
    }

    private fun route(c: RouteCosting, vararg parts: Pair<Double, Double>): Route {
        val ms = parts.mapIndexed { i, (km, kmh) -> Maneuver(ManeuverType.CONTINUE, "x", null, km * 1000, km / kmh * 3600, i, null) }
        val g = listOf(GeoPoint(43.6, 1.4 + c.ordinal * 0.01), GeoPoint(43.2, 2.3))
        return Route(c.name, c, ms.sumOf { it.lengthMeters }, ms.sumOf { it.durationSeconds }, g, ms, false, "fake")
    }

    @Test fun `equivalent distance weights each maneuver by its speed`() {
        val r = route(RouteCosting.FASTEST, 10.0 to 130.0, 10.0 to 80.0)
        assertEquals(10_000 * 1.30 + 10_000 * 0.95, FuelSpeedModel.equivalentMeters(r), 1.0)
    }

    @Test fun `the variant saving at least 5 percent on the fastest is flagged, with litres when the profile gives consumption`() = runTest {
        val fast = route(RouteCosting.FASTEST, 95.0 to 125.0)   // A61 à 130
        val short = route(RouteCosting.SHORTEST, 93.0 to 80.0)  // nationale à 80
        val toll = route(RouteCosting.NO_TOLL, 96.0 to 75.0)
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(
                when (request.costing) { RouteCosting.FASTEST -> fast; RouteCosting.SHORTEST -> short; RouteCosting.NO_TOLL -> toll },
            ))
        }
        val car = VehicleProfile(VehicleType.SEDAN, FuelType.DIESEL, 6.0, 1.75)
        val out = (RouteAlternativesUseCase(routing)(GeoPoint(43.6, 1.4), GeoPoint(43.2, 2.3), car, 0L) as Outcome.Success).value
        val eco = out.single { it.ecoSavingPct != null }
        assertEquals(RouteCosting.SHORTEST, eco.route.costing)
        assertTrue(eco.ecoSavingPct!! in 20..35)
        assertTrue(eco.ecoSavedLitres!! > 1.0)
        // Le coût tient compte de la vitesse : à distance égale, l'autoroute coûte plus.
        assertTrue(out.first().cost!!.fuelEur > eco.cost!!.fuelEur)
    }

    @Test fun `no badge when nothing saves 5 percent`() = runTest {
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(route(request.costing, 50.0 to 80.0 + request.costing.ordinal)))
        }
        val out = (RouteAlternativesUseCase(routing)(GeoPoint(43.6, 1.4), GeoPoint(43.2, 2.3), null, 0L) as Outcome.Success).value
        assertTrue(out.all { it.ecoSavingPct == null })
        assertNull(out.first().ecoSavedLitres)
    }
}
