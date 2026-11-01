package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GeminiLiveProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val tool = ToolSchema("avoidTolls", "Éviter les péages", listOf(
        ToolParam("enabled", ParamType.BOOLEAN, "Activer"),
        ToolParam("mode", ParamType.STRING, "Mode", required = false, enumValues = listOf("A", "B")),
    ))

    @Test fun `setup carries model, audio modality, voice, tools, transcription, VAD and resumption handle`() {
        val setup = json.parseToJsonElement(GeminiLiveProtocol.setup(SessionConfig("k", "Règles", listOf(tool), "Kore", "h1"))).jsonObject["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", setup["model"]!!.jsonPrimitive.content)
        assertEquals("AUDIO", setup["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        assertTrue(setup.toString().contains("\"voiceName\":\"Kore\""))
        assertEquals("Règles", setup["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val decl = setup["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject
        assertEquals("BLOCKING", decl["behavior"]!!.jsonPrimitive.content)
        assertEquals("BOOLEAN", decl["parameters"]!!.jsonObject["properties"]!!.jsonObject["enabled"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("enabled"), decl["parameters"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(decl.toString().contains("\"enum\":[\"A\",\"B\"]"))
        assertTrue("inputAudioTranscription" in setup && "outputAudioTranscription" in setup)
        assertEquals("h1", setup["sessionResumption"]!!.jsonObject["handle"]!!.jsonPrimitive.content)
        assertTrue("automaticActivityDetection" in setup["realtimeInputConfig"]!!.jsonObject)
    }

    @Test fun `a tool without parameters has no parameters object`() {
        assertTrue("parameters" !in GeminiLiveProtocol.declaration(ToolSchema("stopNavigation", "Arrêter")))
    }

    @Test fun `audio is base64 PCM at 16 kHz`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val audio = json.parseToJsonElement(GeminiLiveProtocol.audio(pcm)).jsonObject["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", audio["mimeType"]!!.jsonPrimitive.content)
        assertArrayEquals(pcm, Base64.getDecoder().decode(audio["data"]!!.jsonPrimitive.content))
    }

    @Test fun `tool response echoes id and name`() {
        val msg = GeminiLiveProtocol.toolResponse(listOf(ToolResult("c1", "getArrivalTime", buildJsonObject { put("status", "ok") })))
        val r = json.parseToJsonElement(msg).jsonObject["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
        assertEquals("c1", r["id"]!!.jsonPrimitive.content)
        assertEquals("ok", r["response"]!!.jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test fun `server messages are mapped to events`() {
        val audio = Base64.getEncoder().encodeToString(byteArrayOf(9, 8))
        assertEquals(listOf(AssistantEvent.Ready), GeminiLiveProtocol.parse(json, """{"setupComplete":{}}"""))
        val content = GeminiLiveProtocol.parse(json, """{"serverContent":{"inputTranscription":{"text":"évite "},"outputTranscription":{"text":"D'accord"},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"$audio"}}]},"turnComplete":true}}""")
        assertEquals(AssistantEvent.UserTranscript("évite "), content[0])
        assertEquals(AssistantEvent.AssistantTranscript("D'accord"), content[1])
        assertArrayEquals(byteArrayOf(9, 8), (content[2] as AssistantEvent.AudioChunk).pcm24k)
        assertEquals(AssistantEvent.TurnComplete, content[3])
        assertEquals(listOf(AssistantEvent.Interrupted), GeminiLiveProtocol.parse(json, """{"serverContent":{"interrupted":true}}"""))
        val call = GeminiLiveProtocol.parse(json, """{"toolCall":{"functionCalls":[{"id":"c1","name":"avoidTolls","args":{"enabled":true}}]}}""").single() as AssistantEvent.ToolCalls
        assertEquals("avoidTolls", call.calls.single().name)
        assertEquals("true", call.calls.single().args["enabled"]!!.jsonPrimitive.content)
        assertEquals(listOf(AssistantEvent.ToolCallsCancelled(listOf("c1"))), GeminiLiveProtocol.parse(json, """{"toolCallCancellation":{"ids":["c1"]}}"""))
        assertEquals(listOf(AssistantEvent.ResumptionHandle("h2")), GeminiLiveProtocol.parse(json, """{"sessionResumptionUpdate":{"newHandle":"h2","resumable":true}}"""))
        assertEquals(emptyList<AssistantEvent>(), GeminiLiveProtocol.parse(json, """{"sessionResumptionUpdate":{"newHandle":"h3","resumable":false}}"""))
        assertEquals(listOf(AssistantEvent.GoAway(12_500)), GeminiLiveProtocol.parse(json, """{"goAway":{"timeLeft":"12.5s"}}"""))
        assertEquals(listOf(AssistantEvent.Usage(321)), GeminiLiveProtocol.parse(json, """{"usageMetadata":{"totalTokenCount":321}}"""))
    }

    @Test fun `malformed messages are ignored`() {
        assertEquals(emptyList<AssistantEvent>(), GeminiLiveProtocol.parse(json, "not json"))
        assertEquals(emptyList<AssistantEvent>(), GeminiLiveProtocol.parse(json, """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"%%%"}}]}}}"""))
    }
}
