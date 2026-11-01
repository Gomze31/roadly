package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Dispatch des appels du modèle : validation, consentement, plafond par tour, confirmation, erreurs jamais propagées. */
@Singleton
class ToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards AssistantTool>,
    private val pending: PendingActions,
) {
    private val byName = tools.associateBy { it.schema.name }
    private var callsThisTurn = 0

    fun schemas(): List<ToolSchema> = byName.values.map { it.schema }.sortedBy { it.name } + PendingActions.SCHEMAS

    fun resetTurn() { callsThisTurn = 0 }

    suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult {
        val payload = try {
            dispatch(call, ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolJson.error("internal", e.message ?: e.javaClass.simpleName)
        }
        return ToolResult(call.id, call.name, payload)
    }

    private suspend fun dispatch(call: ToolCall, ctx: ToolContext): JsonObject {
        if (++callsThisTurn > MAX_CALLS_PER_TURN) return ToolJson.error("too_many_calls", "Limite de $MAX_CALLS_PER_TURN appels atteinte pour ce tour ; répondez avec ce que vous savez.")
        when (call.name) {
            PendingActions.CONFIRM -> return pending.confirm(call.args.string("actionId"), ctx)
            PendingActions.CANCEL -> return pending.cancel(call.args.string("actionId"))
        }
        val tool = byName[call.name] ?: return ToolJson.error("unknown_tool", "Outil inconnu : ${call.name}.")
        ToolArgs.validate(tool.schema, call.args)?.let { return ToolJson.error("invalid_arguments", it) }
        if (tool.needsLocationConsent && !ctx.consentGranted) return ToolJson.consentRequired()
        return when (val out = tool.run(call.args, ctx)) {
            is ToolOutput.Done -> out.data
            is ToolOutput.NeedsConfirmation -> pending.register(call.id, out, ctx.nowMs)
        }
    }

    companion object { const val MAX_CALLS_PER_TURN = 8 }
}
