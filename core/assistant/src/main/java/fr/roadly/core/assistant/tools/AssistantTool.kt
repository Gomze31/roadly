package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.ZoneId

/** Contexte d'un appel : consentement courant, horloge, et ce que l'utilisateur a dit depuis un instant donné. */
data class ToolContext(
    val consentGranted: Boolean,
    val nowMs: Long,
    val zone: ZoneId,
    val userSpeechSince: (Long) -> String,
)

sealed interface ToolOutput {
    data class Done(val data: JsonObject) : ToolOutput
    /** Action à conséquences : exécutée seulement après confirmation (voix vérifiée ou puce à l'écran). */
    class NeedsConfirmation(val summary: String, val details: JsonObject = JsonObject(emptyMap()), val action: suspend () -> JsonObject) : ToolOutput
}

interface AssistantTool {
    val schema: ToolSchema
    /** Vrai si l'outil lit ou transmet la position, la destination ou l'itinéraire. */
    val needsLocationConsent: Boolean get() = true
    suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput
}

object ToolJson {
    inline fun ok(build: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject { put("status", "ok"); build() }
    fun unavailable(reason: String): JsonObject = buildJsonObject { put("status", "unavailable"); put("reason", reason) }
    fun error(code: String, message: String): JsonObject = buildJsonObject { put("status", "error"); put("code", code); put("message", message) }
    fun consentRequired(): JsonObject = buildJsonObject {
        put("status", "consent_required")
        put("message", "L'utilisateur n'a pas autorisé le partage de sa position et de son trajet (Réglages → Roadly AI).")
    }
}

fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

/** Validation des arguments d'après le schéma ; renvoie un message d'erreur, ou null si valides. */
object ToolArgs {
    fun validate(schema: ToolSchema, args: JsonObject): String? {
        for (p in schema.parameters) {
            val v = args[p.name] as? JsonPrimitive
            if (v == null || (v.isString && v.content.isBlank())) {
                if (p.required) return "Paramètre « ${p.name} » manquant." else continue
            }
            val ok = when (p.type) {
                ParamType.STRING -> v.isString && (p.enumValues == null || v.content in p.enumValues)
                ParamType.BOOLEAN -> v.booleanOrNull != null
                ParamType.INTEGER -> v.intOrNull != null || v.doubleOrNull?.let { it % 1.0 == 0.0 } == true
                ParamType.NUMBER -> v.doubleOrNull != null
            }
            if (!ok) return "Paramètre « ${p.name} » invalide" + (p.enumValues?.let { " (valeurs : ${it.joinToString()})" } ?: "") + "."
        }
        return null
    }
}
