package fr.roadly.core.assistant.tools

import app.cash.turbine.test
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationToolsTest {
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun ToolOutput.data() = (this as ToolOutput.Done).data

    @Test fun `status says not guiding when idle`() = runTest {
        val f = GuidingFixture(this)
        val out = GetNavigationStatusTool(f.session, f.guidance, FakeCommunity()).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("false", out.str("guiding"))
    }

    @Test fun `status reports real remaining distance, arrival, next instruction and unknown traffic`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val g = f.session.state.value as NavigationState.Guiding
        val out = GetNavigationStatusTool(f.session, f.guidance, FakeCommunity()).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("true", out.str("guiding"))
        assertEquals("Capitole", out.str("destination"))
        assertEquals(ToolFormat.km(g.progress.remainingDistanceM).toString(), out.str("remaining_km"))
        assertEquals(ToolFormat.clock(g.etaEpochMs, f.zone), out.str("arrival_time"))
        assertTrue(out.str("next_instruction").isNotBlank())
        assertTrue(out.str("traffic").startsWith("inconnu"))
        assertEquals("non configuré", out.str("community_reports"))
    }

    @Test fun `next instruction is unavailable without guidance`() = runTest {
        val f = GuidingFixture(this)
        assertEquals("unavailable", GetNextInstructionTool(f.session).run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
    }

    @Test fun `start navigation asks confirmation, then starts guidance and opens the guidance screen`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val capitole = f.place("cap", f.tracker.pointAt(1_300.0), "Place du Capitole")
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(mapOf("capitole" to listOf(capitole))), f.location, f.places)
        val tool = DestinationTool("startNavigation", "Démarrer", "Démarrer le guidage vers", resolver, f.guidance, f.navigator)
        val out = tool.run(buildJsonObject { put("destination", "capitole") }, f.ctx()) as ToolOutput.NeedsConfirmation
        assertTrue(out.summary.contains("Place du Capitole"))
        assertTrue(f.session.state.value !is NavigationState.Guiding)
        f.navigator.requests.test {
            assertEquals("ok", out.action().str("status"))
            runCurrent()
            assertEquals(NavRequest.OpenGuidance, awaitItem())
        }
        assertEquals("Place du Capitole", f.guidance.destinationName)
    }

    @Test fun `home words resolve to the saved home favourite`() = runTest {
        val f = GuidingFixture(this)
        val home = f.place("home", f.tracker.pointAt(900.0), "Maison")
        val resolver = DestinationResolver(FakeFavorites(home = home), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        assertEquals(listOf(home), resolver.resolve("à la maison", null))
        assertEquals(listOf(home), resolver.resolve("chez moi", null))
    }

    @Test fun `an unknown destination is a not_found error, never a guess`() = runTest {
        val f = GuidingFixture(this)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        val out = DestinationTool("startNavigation", "Démarrer", "Démarrer le guidage vers", resolver, f.guidance, f.navigator).run(buildJsonObject { put("destination", "zzz") }, f.ctx()).data()
        assertEquals("not_found", out.str("code"))
    }

    @Test fun `stop navigation is unavailable when idle and confirmed before stopping`() = runTest {
        val f = GuidingFixture(this)
        assertEquals("unavailable", StopNavigationTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
        f.guideAt(300.0)
        val out = StopNavigationTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()) as ToolOutput.NeedsConfirmation
        out.action(); runCurrent()
        assertEquals(NavigationState.Idle, f.session.state.value)
    }

    @Test fun `recalculate reroutes from the snapped position`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        // Le recalcul redémarre la session (progression remise au départ) : on compare à la position projetée d'avant.
        val snappedBefore = (f.session.state.value as NavigationState.Guiding).progress.snapped
        val out = RecalculateRouteTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertEquals(snappedBefore, f.requests.last().origin)
    }
}
