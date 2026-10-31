package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import java.util.Base64

/** Messages JSON de l'API Gemini Live (WebSocket `BidiGenerateContent`, v1beta). */
internal object GeminiLiveProtocol {
    const val MODEL = "models/gemini-3.8-live"

    fun setup(config: SessionConfig): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", MODEL)
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", config.voice) } }
                }
            }
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", config.systemInstruction) } } }
            if (config.tools.isNotEmpty()) putJsonArray("tools") {
                addJsonObject { putJsonArray("functionDeclarations") { config.tools.forEach { add(declaration(it)) } } }
            }
            putJsonObject("realtimeInputConfig") {
                putJsonObject("automaticActivityDetection") {
                    // Habitacle bruyant : début de parole moins sensible, silence de fin dans la plage recommandée (500-800 ms).
                    put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                    put("prefixPaddingMs", 200)
                    put("silenceDurationMs", 700)
                }
                put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS")
            }
            putJsonObject("inputAudioTranscription") {}
            putJsonObject("outputAudioTranscription") {}
            putJsonObject("sessionResumption") { config.resumptionHandle?.let { put("handle", it) } }
            putJsonObject("contextWindowCompression") { putJsonObject("slidingWindow") {} }
        }
    }.toString()

    /** `behavior` explicite : `gemini-3.8-live` est NON_BLOCKING par défaut, Roadly attend chaque résultat. */
    fun declaration(tool: ToolSchema): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("behavior", "BLOCKING")
        if (tool.parameters.isNotEmpty()) putJsonObject("parameters") {
            put("type", "OBJECT")
            putJsonObject("properties") {
                tool.parameters.forEach { p ->
                    putJsonObject(p.name) {
                        put("type", p.type.name)
                        put("description", p.description)
                        p.enumValues?.let { values -> put("format", "enum"); putJsonArray("enum") { values.forEach { add(it) } } }
                    }
                }
            }
            putJsonArray("required") { tool.parameters.filter { it.required }.forEach { add(it.name) } }
        }
    }

    fun audio(pcm: ByteArray): String = buildJsonObject {
        putJsonObject("realtimeInput") {
            putJsonObject("audio") { put("data", Base64.getEncoder().encodeToString(pcm)); put("mimeType", "audio/pcm;rate=16000") }
        }
    }.toString()

    fun audioStreamEnd(): String = """{"realtimeInput":{"audioStreamEnd":true}}"""

    fun text(text: String): String = buildJsonObject {
        putJsonObject("clientContent") {
            putJsonArray("turns") { addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", text) } } } }
            put("turnComplete", true)
        }
    }.toString()

    fun toolResponse(results: List<ToolResult>): String = buildJsonObject {
        putJsonObject("toolResponse") {
            putJsonArray("functionResponses") {
                results.forEach { r -> addJsonObject { put("id", r.id); put("name", r.name); put("response", r.payload) } }
            }
        }
    }.toString()

    /** Un message serveur peut porter plusieurs événements ; un message illisible n'en produit aucun. */
    fun parse(json: Json, text: String): List<AssistantEvent> = try {
        val root = json.parseToJsonElement(text).jsonObject
        buildList {
            if ("setupComplete" in root) add(AssistantEvent.Ready)
            root["serverContent"]?.jsonObject?.let { sc ->
                sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { add(AssistantEvent.UserTranscript(it)) }
                sc["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { add(AssistantEvent.AssistantTranscript(it)) }
                sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                    part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull?.let {
                        add(AssistantEvent.AudioChunk(Base64.getDecoder().decode(it)))
                    }
                }
                if (sc["interrupted"]?.jsonPrimitive?.booleanOrNull == true) add(AssistantEvent.Interrupted)
                if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) add(AssistantEvent.TurnComplete)
            }
            root["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray?.let { calls ->
                add(AssistantEvent.ToolCalls(calls.mapNotNull { c ->
                    val o = c.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ToolCall(o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), name, o["args"] as? JsonObject ?: JsonObject(emptyMap()))
                }))
            }
            root["toolCallCancellation"]?.jsonObject?.get("ids")?.jsonArray?.let { ids ->
                add(AssistantEvent.ToolCallsCancelled(ids.mapNotNull { it.jsonPrimitive.contentOrNull }))
            }
            root["sessionResumptionUpdate"]?.jsonObject?.let { u ->
                if (u["resumable"]?.jsonPrimitive?.booleanOrNull == true) u["newHandle"]?.jsonPrimitive?.contentOrNull?.let { add(AssistantEvent.ResumptionHandle(it)) }
            }
            root["goAway"]?.jsonObject?.let { add(AssistantEvent.GoAway(parseDurationMs(it["timeLeft"]))) }
            root["usageMetadata"]?.jsonObject?.get("totalTokenCount")?.jsonPrimitive?.intOrNull?.let { add(AssistantEvent.Usage(it)) }
        }
    } catch (e: IllegalArgumentException) {
        // JSON invalide (SerializationException en hérite) ou base64 corrompu.
        emptyList()
    }

    /** Durée protobuf en JSON (`"12.5s"`) ; absente ou illisible → 0. */
    fun parseDurationMs(e: JsonElement?): Long =
        (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L
}
