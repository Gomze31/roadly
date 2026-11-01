package fr.roadly.core.assistant.settings

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface SecretCipher {
    fun encrypt(plain: String): String
    /** null si le texte est illisible (clé Keystore perdue après réinstallation, données corrompues). */
    fun decrypt(cipherText: String): String?
}

enum class KeySource { OVERRIDE, BUILD, NONE }

/** Clé Gemini : saisie dans Réglages (chiffrée) sinon `GEMINI_API_KEY` de `local.properties` (BuildConfig). */
@Singleton
class ApiKeyProvider @Inject constructor(
    private val settings: AssistantSettingsRepository,
    private val cipher: SecretCipher,
    @param:Named(BUILD_KEY) private val buildKey: String,
) {
    suspend fun key(): String? = override() ?: buildKey.takeIf { it.isNotBlank() }

    suspend fun source(): KeySource = when {
        override() != null -> KeySource.OVERRIDE
        buildKey.isNotBlank() -> KeySource.BUILD
        else -> KeySource.NONE
    }

    suspend fun save(key: String) {
        val k = key.trim()
        settings.setEncryptedKey(if (k.isEmpty()) null else cipher.encrypt(k))
    }

    suspend fun clearOverride() = settings.setEncryptedKey(null)

    private suspend fun override(): String? = settings.settings.first().encryptedKey?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }

    companion object { const val BUILD_KEY = "geminiBuildKey" }
}
