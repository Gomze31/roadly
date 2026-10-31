package fr.roadly.core.assistant.tools

import fr.roadly.core.common.AppError
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
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
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class RouteToolsTest {
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun ToolOutput.data() = (this as ToolOutput.Done).data
    private fun avoidTolls(f: GuidingFixture) = AvoidOptionTool("avoidTolls", "Éviter les péages", { p, on -> p.copy(avoidTolls = on) }, f.prefs, f.guidance, f.session)

    @Test fun `avoiding tolls while guiding persists, reroutes and compares remaining durations`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(1000.0)
        // Valeurs capturées avant : le recalcul redémarre la session (progress remis à zéro jusqu'au prochain fix).
        val remainingS = (f.session.state.value as NavigationState.Guiding).progress.remainingDurationS
        val fullS = f.route.durationSeconds
        val reroutedS = fullS - 20 // plus court que la durée complète, plus long que le restant
        assertTrue(remainingS < reroutedS && reroutedS < fullS)
        f.durationFor = { reroutedS }
        val out = avoidTolls(f).run(buildJsonObject { put("enabled", true) }, f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertTrue(f.prefs.routePreferences.first().avoidTolls)
        assertTrue(f.requests.last().preferences.avoidTolls)
        assertEquals(ToolFormat.minutes(remainingS.toDouble()).toString(), out.str("before_min"))
        assertEquals(ToolFormat.minutes(reroutedS).toString(), out.str("after_min"))
        assertTrue(out.str("after_min").toInt() > out.str("before_min").toInt())
        assertTrue("after_arrival" in out)
    }

    @Test fun `avoiding tolls without guidance only saves the preference`() = runTest {
        val f = GuidingFixture(this)
        val out = avoidTolls(f).run(buildJsonObject { put("enabled", true) }, f.ctx()).data()
        assertEquals("false", out.str("guiding"))
        assertTrue(f.prefs.routePreferences.first().avoidTolls)
    }

    @Test fun `alternatives get short ids and selecting one switches guidance`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val routes = ToolRoutes()
        val out = FindAlternativeRoutesTool(f.session, f.guidance, routes).run(JsonObject(emptyMap()), f.ctx()).data()
        val options = out["routes"]!!.jsonArray
        assertEquals(3, options.size)
        assertEquals("r2", options[1].jsonObject.str("route_id"))
        SelectRouteTool(f.session, f.guidance, routes).run(buildJsonObject { put("routeId", "r2") }, f.ctx()).data()
        runCurrent()
        assertEquals(routes["r2"]!!.route.id, (f.session.state.value as NavigationState.Guiding).route.id)
        assertEquals("unknown_route", SelectRouteTool(f.session, f.guidance, routes).run(buildJsonObject { put("routeId", "r9") }, f.ctx()).data().str("code"))
    }

    @Test fun `arrival time while guiding compares with the real ETA`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        val tool = SetArrivalTimeTool(f.session, resolver, RouteAlternativesUseCase(f.routing), f.prefs, f.vehicles, f.location)
        val out = tool.run(buildJsonObject { put("time", "23:59") }, f.ctx()).data()
        assertEquals("true", out.str("on_time"))
        assertTrue(out.str("note").contains("trafic"))
    }

    @Test fun `clock times are parsed in several spoken forms, the next occurrence`() {
        val zone = ZoneId.of("Europe/Paris")
        val now = ZonedDateTime.of(2026, 10, 31, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(ZonedDateTime.of(2026, 10, 31, 18, 30, 0, 0, zone).toInstant().toEpochMilli(), parseClockTime("18:30", now, zone))
        assertEquals(ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, zone).toInstant().toEpochMilli(), parseClockTime("18h", now, zone))
        assertEquals(ZonedDateTime.of(2026, 11, 1, 8, 15, 0, 0, zone).toInstant().toEpochMilli(), parseClockTime("8h15", now, zone))
        assertNull(parseClockTime("demain", now, zone))
    }

    @Test fun `adding a stop is confirmed and reports the extra minutes`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val stop = f.place("s1", f.tracker.pointAt(800.0), "Boulangerie")
        f.places.remember(stop)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        f.durationFor = { r -> if (r.waypoints.isNotEmpty()) f.route.durationSeconds + 300 else f.route.durationSeconds }
        val asked = AddStopTool(f.session, resolver, f.guidance).run(buildJsonObject { put("placeId", "s1") }, f.ctx()) as ToolOutput.NeedsConfirmation
        assertTrue(asked.summary.contains("Boulangerie"))
        val done = asked.action(); runCurrent()
        assertEquals("ok", done.str("status"))
        assertEquals(listOf("s1"), f.guidance.remainingStops().map { it.id })
        val stops = GetStopsTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()).data()["stops"]!!.jsonArray
        assertEquals("s1", stops.single().jsonObject.str("stop_id"))
        val remove = RemoveStopTool(f.session, f.guidance).run(buildJsonObject { put("stopId", "s1") }, f.ctx()) as ToolOutput.NeedsConfirmation
        remove.action(); runCurrent()
        assertTrue(f.guidance.remainingStops().isEmpty())
    }

    @Test fun `route tools refuse honestly without guidance`() = runTest {
        val f = GuidingFixture(this)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        assertEquals("unavailable", AddStopTool(f.session, resolver, f.guidance).run(buildJsonObject { put("destination", "x") }, f.ctx()).data().str("status"))
        assertEquals("unavailable", FindAlternativeRoutesTool(f.session, f.guidance, ToolRoutes()).run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
        assertEquals(RoutePreferences(), f.prefs.routePreferences.first())
    }

    @Test fun `a failing place search is reported as unavailable, never as nothing found`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap(), failure = AppError.Network), f.location, f.places)
        val stop = AddStopTool(f.session, resolver, f.guidance).run(buildJsonObject { put("destination", "boulangerie") }, f.ctx()).data()
        assertEquals("search_unavailable", stop.str("code"))
        assertTrue(stop.str("message").contains("réseau"))
        f.session.stop()
        val tool = SetArrivalTimeTool(f.session, resolver, RouteAlternativesUseCase(f.routing), f.prefs, f.vehicles, f.location)
        val arrival = tool.run(buildJsonObject { put("time", "23:59"); put("destination", "Capitole") }, f.ctx()).data()
        assertEquals("search_unavailable", arrival.str("code"))
        assertTrue(f.guidance.remainingStops().isEmpty())
    }
}
