package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ToolRegistryTest {
    private var executed = 0
    private val echo = object : AssistantTool {
        override val schema = ToolSchema("echo", "Renvoie", listOf(ToolParam("n", ParamType.INTEGER, "Nombre"), ToolParam("mode", ParamType.STRING, "Mode", required = false, enumValues = listOf("a", "b"))))
        override val needsLocationConsent = false
        override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.Done(ToolJson.ok { put("n", args.int("n")) })
    }
    private val located = object : AssistantTool {
        override val schema = ToolSchema("where", "Position")
        override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.Done(ToolJson.ok())
    }
    private val dangerous = object : AssistantTool {
        override val schema = ToolSchema("stopNavigation", "Arrêter")
        override val needsLocationConsent = false
        override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.NeedsConfirmation("Arrêter le guidage") { executed++; ToolJson.ok() }
    }
    private val failing = object : AssistantTool {
        override val schema = ToolSchema("boom", "Plante")
        override val needsLocationConsent = false
        override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput = error("panne")
    }
    private val pending = PendingActions()
    private val registry = ToolRegistry(setOf(echo, located, dangerous, failing), pending)
    private var speech = ""
    private fun ctx(now: Long = 1_000, consent: Boolean = true) = ToolContext(consent, now, ZoneId.of("Europe/Paris")) { speech }
    private fun call(name: String, args: JsonObject = JsonObject(emptyMap()), id: String = "c-$name") = ToolCall(id, name, args)
    private fun JsonObject.status() = this["status"]!!.jsonPrimitive.content

    @Test fun `schemas include every tool plus confirmAction and cancelAction`() {
        assertEquals(setOf("boom", "cancelAction", "confirmAction", "echo", "stopNavigation", "where"), registry.schemas().map { it.name }.toSet())
    }

    @Test fun `unknown tool, missing or ill-typed argument and bad enum are structured errors`() = runTest {
        assertEquals("unknown_tool", registry.execute(call("nope"), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("invalid_arguments", registry.execute(call("echo"), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("invalid_arguments", registry.execute(call("echo", buildJsonObject { put("n", "deux") }), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("invalid_arguments", registry.execute(call("echo", buildJsonObject { put("n", 2); put("mode", "z") }), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("2", registry.execute(call("echo", buildJsonObject { put("n", 2) }), ctx()).payload["n"]!!.jsonPrimitive.content)
    }

    @Test fun `a crashing tool returns an internal error instead of throwing`() = runTest {
        assertEquals("internal", registry.execute(call("boom"), ctx()).payload["code"]!!.jsonPrimitive.content)
    }

    @Test fun `location tools need consent`() = runTest {
        assertEquals("consent_required", registry.execute(call("where"), ctx(consent = false)).payload.status())
        assertEquals("ok", registry.execute(call("where"), ctx()).payload.status())
    }

    @Test fun `no more than 8 calls per turn`() = runTest {
        repeat(8) { registry.execute(call("echo", buildJsonObject { put("n", it) }), ctx()) }
        assertEquals("too_many_calls", registry.execute(call("echo", buildJsonObject { put("n", 9) }), ctx()).payload["code"]!!.jsonPrimitive.content)
        registry.resetTurn()
        assertEquals("ok", registry.execute(call("echo", buildJsonObject { put("n", 1) }), ctx()).payload.status())
    }

    @Test fun `confirmation runs the action only after a spoken yes heard after the request`() = runTest {
        val asked = registry.execute(call("stopNavigation"), ctx(now = 1_000)).payload
        assertEquals("needs_confirmation", asked.status())
        val id = asked["action_id"]!!.jsonPrimitive.content
        val confirm = call(PendingActions.CONFIRM, buildJsonObject { put("actionId", id) })
        speech = "euh"
        assertEquals("not_confirmed", registry.execute(confirm, ctx(now = 2_000)).payload["code"]!!.jsonPrimitive.content)
        assertEquals(0, executed)
        speech = "oui vas-y"
        assertEquals("ok", registry.execute(confirm, ctx(now = 3_000)).payload.status())
        assertEquals(1, executed)
        assertNull(pending.current.value)
    }

    @Test fun `an expired request is refused`() = runTest {
        val id = registry.execute(call("stopNavigation"), ctx(now = 0)).payload["action_id"]!!.jsonPrimitive.content
        speech = "oui"
        assertEquals("expired", registry.execute(call(PendingActions.CONFIRM, buildJsonObject { put("actionId", id) }), ctx(now = 31_000)).payload["code"]!!.jsonPrimitive.content)
        assertEquals(0, executed)
    }

    @Test fun `the on-screen confirm chip runs the action without a spoken yes`() = runTest {
        registry.execute(call("stopNavigation"), ctx())
        assertTrue(pending.confirmFromUi() != null)
        assertEquals(1, executed)
    }

    @Test fun `cancelling or a server cancellation drops the pending action`() = runTest {
        val id = registry.execute(call("stopNavigation", id = "c9"), ctx()).payload["action_id"]!!.jsonPrimitive.content
        pending.cancelCreatedBy(listOf("c9"))
        assertNull(pending.current.value)
        registry.execute(call("stopNavigation"), ctx())
        assertEquals("ok", registry.execute(call(PendingActions.CANCEL, buildJsonObject { put("actionId", pending.current.value!!.id) }), ctx()).payload.status())
        assertNull(pending.current.value)
        assertTrue(id.isNotBlank())
    }
}
