package fr.roadly.core.assistant.provider

import kotlinx.serialization.json.JsonObject

enum class ParamType { STRING, NUMBER, INTEGER, BOOLEAN }

/** Paramètre d'outil, indépendant du fournisseur ; [enumValues] restreint une chaîne à une liste. */
data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
)

/** Déclaration d'outil neutre ; chaque fournisseur la traduit dans son format. */
data class ToolSchema(val name: String, val description: String, val parameters: List<ToolParam> = emptyList())

data class ToolCall(val id: String, val name: String, val args: JsonObject)

/** Réponse à un appel d'outil : objet JSON court et factuel (`status`, données). */
data class ToolResult(val id: String, val name: String, val payload: JsonObject)
