package fr.roadly.core.assistant.tools

import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.getOrNull
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(listOf(home), resolver.resolve("à la maison", null).getOrNull())
        assertEquals(listOf(home), resolver.resolve("chez moi", null).getOrNull())
        assertEquals(listOf(home), resolver.resolve("Mon domicile", null).getOrNull())
        assertEquals(home, f.places["home"])
    }

    @Test fun `work words resolve to the saved work favourite`() = runTest {
        val f = GuidingFixture(this)
        val work = f.place("work", f.tracker.pointAt(900.0), "Bureau Airbus")
        val resolver = DestinationResolver(FakeFavorites(work = work), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        assertEquals(listOf(work), resolver.resolve("au travail", null).getOrNull())
        assertEquals(listOf(work), resolver.resolve("le bureau", null).getOrNull())
        assertEquals(work, f.places["work"])
    }

    @Test fun `a place merely containing a home or work word goes to the geocoder`() = runTest {
        val f = GuidingFixture(this)
        val home = f.place("home", f.tracker.pointAt(900.0), "Maison")
        val work = f.place("work", f.tracker.pointAt(900.0), "Bureau Airbus")
        val carree = f.place("carree", f.tracker.pointAt(1_300.0), "Maison Carrée")
        val poste = f.place("poste", f.tracker.pointAt(1_200.0), "Bureau de poste")
        val geocoder = FakeGeocoder(mapOf("Maison Carrée" to listOf(carree), "bureau de poste" to listOf(poste)))
        val resolver = DestinationResolver(FakeFavorites(home = home, work = work), FakeRecents(), geocoder, f.location, f.places)
        assertEquals(listOf(carree), resolver.resolve("Maison Carrée", null).getOrNull())
        assertEquals(listOf(poste), resolver.resolve("bureau de poste", null).getOrNull())
        assertEquals(listOf("Maison Carrée", "bureau de poste"), geocoder.queries)
    }

    @Test fun `a geocoder failure is search_unavailable, not not_found`() = runTest {
        val f = GuidingFixture(this)
        val geocoder = FakeGeocoder(emptyMap(), failure = AppError.Network)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), geocoder, f.location, f.places)
        assertEquals(Outcome.Failure(AppError.Network), resolver.resolve("capitole", null))
        val out = DestinationTool("startNavigation", "Démarrer", "Démarrer le guidage vers", resolver, f.guidance, f.navigator).run(buildJsonObject { put("destination", "capitole") }, f.ctx()).data()
        assertEquals("search_unavailable", out.str("code"))
        assertTrue(out.str("message").contains("réseau indisponible"))
    }

    @Test fun `status reports a failing community service as unavailable, never as no reports`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val out = GetNavigationStatusTool(f.session, f.guidance, FakeCommunity(configured = true, nearbyFailure = AppError.Network)).run(JsonObject(emptyMap()), f.ctx()).data()
        assertTrue(out.str("community_reports").startsWith("indisponible"))
        assertTrue(out.str("community_reports").contains("réseau indisponible"))
        assertNull(out["reports_ahead"])
    }

    @Test fun `status lists reports_ahead when the community service answers with none`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val out = GetNavigationStatusTool(f.session, f.guidance, FakeCommunity(configured = true)).run(JsonObject(emptyMap()), f.ctx()).data()
        assertNull(out["community_reports"])
        assertEquals(0, out["reports_ahead"]!!.jsonArray.size)
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
