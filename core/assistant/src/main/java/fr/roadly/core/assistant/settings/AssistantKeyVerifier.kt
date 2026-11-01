package fr.roadly.core.assistant.settings

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import javax.inject.Inject

/** « Vérifier la clé » : connexion, attente de setupComplete, fermeture ; aucun micro. */
class AssistantKeyVerifier @Inject constructor(private val provider: VoiceAssistantProvider) {
    suspend fun verify(key: String): AssistantError? = when (val c = provider.connect(SessionConfig(key.trim(), "Test de connexion Roadly.", emptyList()))) {
        is Connection.Open -> { c.session.close(); null }
        is Connection.Refused -> c.error
    }
}
