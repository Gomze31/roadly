package fr.roadly.core.assistant

import fr.roadly.core.assistant.provider.AssistantError

/** Phrases courtes annoncées (en file, jamais en coupant le guidage) et affichées dans la fenêtre compacte. */
object AssistantMessages {
    fun of(error: AssistantError): String = when (error) {
        AssistantError.NO_KEY -> "Ajoutez votre clé Gemini dans les réglages de Roadly AI."
        AssistantError.INVALID_KEY -> "Clé API refusée, vérifiez-la dans les réglages."
        AssistantError.QUOTA -> "Quota Gemini atteint pour le moment."
        AssistantError.DAILY_CAP -> "Plafond quotidien de Roadly AI atteint."
        AssistantError.MIC_UNAVAILABLE -> "Micro indisponible."
        AssistantError.DISABLED -> "Roadly AI est désactivé dans les réglages."
        AssistantError.NETWORK, AssistantError.TIMEOUT, AssistantError.PROTOCOL -> "Roadly AI est indisponible, le guidage continue."
    }
}
