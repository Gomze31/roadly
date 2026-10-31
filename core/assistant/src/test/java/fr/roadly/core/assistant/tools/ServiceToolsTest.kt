package fr.roadly.core.assistant.tools

import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.FuelPrice
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.NavRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceToolsTest {
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun ToolOutput.data() = (this as ToolOutput.Done).data
    private fun station(id: String, p: GeoPoint, e10: Double?) =
        FuelStation(id, "Station $id", "Rue $id", p, listOfNotNull(e10?.let { FuelPrice(FuelType.E10, it, 0L) }), open24h = false, services = emptyList())
    private class Stations(val list: List<FuelStation>) : FuelStationProvider {
        override val id = "fake"
        override suspend fun nearby(near: GeoPoint, radiusKm: Int, limit: Int) = Outcome.Success(list)
    }
    /** Échoue sur les appels dont [failWhen] est vrai (rang de l'appel, 0 = premier), sinon renvoie [list]. */
    private class FlakyStations(val list: List<FuelStation>, val failWhen: (Int) -> Boolean) : FuelStationProvider {
        override val id = "fake"
        var calls = 0
        override suspend fun nearby(near: GeoPoint, radiusKm: Int, limit: Int): Outcome<List<FuelStation>> =
            if (failWhen(calls++)) Outcome.Failure(AppError.Network) else Outcome.Success(list)
    }
    private class Pois(val list: List<Place>) : NearbyPoiProvider {
        override val id = "fake"
        override suspend fun nearby(category: PoiCategory, near: GeoPoint, limit: Int) = Outcome.Success(list)
    }
    /** Échoue pour les catégories de [failing], renvoie [list] pour les autres. */
    private class FlakyPois(val list: List<Place>, val failing: Set<PoiCategory>) : NearbyPoiProvider {
        override val id = "fake"
        override suspend fun nearby(category: PoiCategory, near: GeoPoint, limit: Int): Outcome<List<Place>> =
            if (category in failing) Outcome.Failure(AppError.Network) else Outcome.Success(list)
    }

    @Test fun `traffic is honestly unavailable`() = runTest {
        val f = GuidingFixture(this)
        assertEquals("unavailable", ShowTrafficTool().run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
    }

    @Test fun `gas stations open the filtered search when not guiding`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val cards = AssistantCards()
        val tool = ShowNearbyTool("showGasStations", "Stations", listOf(PoiCategory.FUEL), "Stations", f.session, Pois(listOf(f.place("p1", f.tracker.pointAt(400.0)))), f.location, f.navigator, cards, f.places)
        f.navigator.requests.test {
            val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
            assertEquals(NavRequest.OpenSearch("FUEL"), awaitItem())
            assertEquals("true", out.str("opened_search"))
            assertEquals("p1", out["places"]!!.jsonArray.single().jsonObject.str("place_id"))
        }
        assertEquals(null, cards.card.value)
    }

    @Test fun `nearby places in guidance fill the card`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(200.0)
        val cards = AssistantCards()
        val tool = ShowNearbyTool("showParking", "Parkings", listOf(PoiCategory.PARKING), "Parkings", f.session, Pois(listOf(f.place("p1", f.tracker.pointAt(400.0)))), f.location, f.navigator, cards, f.places)
        val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("false", out.str("opened_search"))
        assertEquals("p1", cards.card.value!!.rows.single().placeId)
    }

    @Test fun `nearby places failing everywhere are an error, never an empty list`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(200.0)
        val cards = AssistantCards()
        val tool = ShowNearbyTool("showGasStations", "Stations", listOf(PoiCategory.FUEL), "Stations", f.session, FlakyPois(emptyList(), setOf(PoiCategory.FUEL)), f.location, f.navigator, cards, f.places)
        val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("error", out.str("status"))
        assertEquals("nearby_unavailable", out.str("code"))
        assertNull(out["places"])
        assertNull(cards.card.value)
    }

    @Test fun `nearby places keep what succeeded and flag the partial result`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(200.0)
        val tool = ShowNearbyTool("showNearbyServices", "Services", listOf(PoiCategory.FUEL, PoiCategory.REST_AREA), "Services", f.session, FlakyPois(listOf(f.place("p1", f.tracker.pointAt(400.0))), setOf(PoiCategory.FUEL)), f.location, f.navigator, AssistantCards(), f.places)
        val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertEquals("true", out.str("partial"))
        assertEquals("p1", out["places"]!!.jsonArray.single().jsonObject.str("place_id"))
    }

    @Test fun `fuel prices are sorted cheapest first and stations without the fuel are skipped`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        val tool = GetFuelPricesTool(f.session, Stations(listOf(station("a", f.tracker.pointAt(300.0), 1.90), station("b", f.tracker.pointAt(600.0), 1.75), station("c", f.tracker.pointAt(700.0), null))), f.vehicles, f.location, f.places)
        val rows = tool.run(JsonObject(emptyMap()), f.ctx()).data()["stations"]!!.jsonArray
        assertEquals(listOf("Station b", "Station a"), rows.map { it.jsonObject.str("name") })
        assertEquals("1.75", rows[0].jsonObject.str("price_eur"))
    }

    @Test fun `fuel prices from a failing provider are an error`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        val tool = GetFuelPricesTool(f.session, FlakyStations(emptyList()) { true }, f.vehicles, f.location, f.places)
        val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("error", out.str("status"))
        assertEquals("prices_unavailable", out.str("code"))
    }

    @Test fun `cheapest fuel on route keeps stations near the route ahead and computes the real detour`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        f.guideAt(200.0)
        val ahead = station("ahead", f.tracker.pointAt(900.0), 1.70)
        val behind = station("behind", f.tracker.pointAt(50.0), 1.50)
        val far = station("far", GeoPoint(f.tracker.pointAt(900.0).lat + 0.05, f.tracker.pointAt(900.0).lon), 1.40)
        f.durationFor = { r -> if (r.waypoints.isNotEmpty()) f.route.durationSeconds + 240 else f.route.durationSeconds }
        val cards = AssistantCards()
        val tool = FindCheapestFuelOnRouteTool(f.session, f.guidance, Stations(listOf(ahead, behind, far)), f.vehicles, f.routing, f.prefs, cards, f.places)
        val rows = tool.run(JsonObject(emptyMap()), f.ctx()).data()["stations"]!!.jsonArray
        assertEquals(listOf("Station ahead"), rows.map { it.jsonObject.str("name") })
        assertTrue(rows[0].jsonObject.str("detour_min").toInt() >= 2)
        assertEquals("fuel:ahead", rows[0].jsonObject.str("place_id"))
        assertEquals("Station ahead", cards.card.value!!.rows.single().title)
    }

    @Test fun `cheapest fuel on route with a failing provider is an error, never no station`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        f.guideAt(200.0)
        val cards = AssistantCards()
        val tool = FindCheapestFuelOnRouteTool(f.session, f.guidance, FlakyStations(emptyList()) { true }, f.vehicles, f.routing, f.prefs, cards, f.places)
        val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("error", out.str("status"))
        assertEquals("fuel_unavailable", out.str("code"))
        assertNull(cards.card.value)
    }

    @Test fun `cheapest fuel on route keeps results and flags partial when some samples failed`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        f.guideAt(200.0)
        val ahead = station("ahead", f.tracker.pointAt(900.0), 1.70)
        val tool = FindCheapestFuelOnRouteTool(f.session, f.guidance, FlakyStations(listOf(ahead)) { it == 0 }, f.vehicles, f.routing, f.prefs, AssistantCards(), f.places)
        val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertEquals("true", out.str("partial"))
        assertEquals(listOf("Station ahead"), out["stations"]!!.jsonArray.map { it.jsonObject.str("name") })
    }

    @Test fun `trip cost uses the profile and says tolls are not estimated`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        f.guideAt(200.0)
        val out = GetEstimatedTripCostTool(f.session, f.vehicles).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertTrue(out.str("tolls").contains("non estimés"))
    }

    @Test fun `reports need a configured community service`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val off = FakeCommunity(configured = false)
        assertEquals("unavailable", ReportIncidentTool(f.session, f.location, off).run(buildJsonObject { put("type", "ACCIDENT") }, f.ctx()).data().str("status"))
        val report = CommunityReport("r1", ReportKind.ACCIDENT, f.tracker.pointAt(0.0), 0L, 1L, 0, 0, mine = true)
        val on = FakeCommunity(configured = true, reports = listOf(report))
        assertEquals("ok", ReportIncidentTool(f.session, f.location, on).run(buildJsonObject { put("type", "ACCIDENT") }, f.ctx()).data().str("status"))
        assertEquals(listOf(ReportKind.ACCIDENT), on.sent)
        ConfirmIncidentTool(on).run(buildJsonObject { put("reportId", "r1") }, f.ctx())
        assertEquals(listOf("r1"), on.votes)
    }

    @Test fun `a failed report is an error, not a success`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val on = FakeCommunity(configured = true)
        assertEquals("error", ReportIncidentTool(f.session, f.location, on).run(buildJsonObject { put("type", "JAM") }, f.ctx()).data().str("status"))
    }

    @Test fun `changing the vehicle type is confirmed and keeps the other settings`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        val asked = ChangeVehicleProfileTool(f.vehicles).run(buildJsonObject { put("type", "MOTORCYCLE") }, f.ctx()) as ToolOutput.NeedsConfirmation
        asked.action()
        assertEquals(VehicleProfile(VehicleType.MOTORCYCLE, FuelType.E10, 6.0, 1.8), f.vehicle)
    }
}
