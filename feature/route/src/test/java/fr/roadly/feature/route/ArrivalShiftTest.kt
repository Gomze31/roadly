package fr.roadly.feature.route

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.routing.RouteOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalShiftTest {
    private fun option(costing: RouteCosting, durationS: Double) = RouteOption(
        Route(costing.name, costing, 1000.0, durationS, listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), listOf(Maneuver(ManeuverType.ARRIVE, "", null, 0.0, 0.0, 1, null)), false, "fake"),
        costing.name, 0L, null, sameAsFastest = false,
    )

    @Test fun `departure is arrival minus the fastest duration and every option gets its own arrival`() {
        val arrive = 1_000_000L
        val (shifted, depart) = RouteViewModel.shiftToArrival(listOf(option(RouteCosting.SHORTEST, 900.0), option(RouteCosting.FASTEST, 600.0)), arrive)
        assertEquals(arrive - 600_000L, depart)
        assertEquals(arrive, shifted.first { it.route.costing == RouteCosting.FASTEST }.arrivalEpochMs)
        assertEquals(arrive + 300_000L, shifted.first { it.route.costing == RouteCosting.SHORTEST }.arrivalEpochMs)
    }
}
