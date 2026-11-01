package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Une seule action en attente à la fois (affichée avec Confirmer / Annuler). */
@Singleton
class PendingActions @Inject constructor() {
    data class Pending(val id: String, val callId: String, val summary: String, val createdAtMs: Long, val action: suspend () -> JsonObject)

    private val _current = MutableStateFlow<Pending?>(null)
    val current: StateFlow<Pending?> = _current.asStateFlow()
    private var seq = 0

    fun register(callId: String, out: ToolOutput.NeedsConfirmation, nowMs: Long): JsonObject {
        val p = Pending("a${++seq}", callId, out.summary, nowMs, out.action)
        _current.value = p
        return buildJsonObject {
            put("status", "needs_confirmation")
            put("action_id", p.id)
            put("summary", p.summary)
            out.details.forEach { (k, v) -> put(k, v) }
        }
    }

    /** Exécute si l'utilisateur a dit « oui » (sans négation) **après** la demande, dans les 30 s. */
    suspend fun confirm(actionId: String?, ctx: ToolContext): JsonObject {
        val p = _current.value?.takeIf { it.id == actionId } ?: return ToolJson.error("unknown_action", "Aucune action en attente avec cet identifiant.")
        if (ctx.nowMs - p.createdAtMs > TTL_MS) {
            _current.value = null
            return ToolJson.error("expired", "La demande a expiré ; redemandez à l'utilisateur.")
        }
        if (!Affirmation.isYes(ctx.userSpeechSince(p.createdAtMs))) {
            return ToolJson.error("not_confirmed", "L'utilisateur n'a pas clairement accepté à voix haute ; redemandez-lui ou appelez cancelAction.")
        }
        _current.value = null
        return p.action()
    }

    /** Puce « Confirmer » : geste explicite de l'utilisateur, pas de vérification vocale. */
    suspend fun confirmFromUi(): JsonObject? {
        val p = _current.value ?: return null
        _current.value = null
        return p.action()
    }

    fun cancel(actionId: String?): JsonObject {
        if (_current.value?.id != actionId) return ToolJson.error("unknown_action", "Aucune action en attente avec cet identifiant.")
        _current.value = null
        return ToolJson.ok { put("cancelled", true) }
    }

    fun cancelFromUi(): Boolean = (_current.value != null).also { _current.value = null }

    fun cancelCreatedBy(callIds: List<String>) { if (_current.value?.callId in callIds) _current.value = null }

    fun clear() { _current.value = null }

    companion object {
        const val TTL_MS = 30_000L
        const val CONFIRM = "confirmAction"
        const val CANCEL = "cancelAction"
        private val ACTION_ID = ToolParam("actionId", ParamType.STRING, "Identifiant action_id reçu avec needs_confirmation.")
        val SCHEMAS = listOf(
            ToolSchema(CONFIRM, "Exécute l'action en attente, uniquement si l'utilisateur vient d'accepter clairement à voix haute.", listOf(ACTION_ID)),
            ToolSchema(CANCEL, "Annule l'action en attente (refus ou hésitation de l'utilisateur).", listOf(ACTION_ID)),
        )
    }
}

/** Accord vocal explicite en français ; toute négation l'emporte (refuser à tort est sans danger). */
internal object Affirmation {
    private val yes = Regex("""(?U)\b(oui|ouais|ok|okay|d'accord|vas-y|vas y|confirme|confirmé|confirmée|exactement|parfait|c'est ça|carrément|allez)\b""")
    private val no = Regex("""(?U)\b(non|annule|annuler|pas|attends|stop|surtout|laisse tomber)\b""")
    fun isYes(text: String): Boolean {
        val t = text.lowercase(Locale.FRENCH).replace('’', '\'')
        return yes.containsMatchIn(t) && !no.containsMatchIn(t)
    }
}
