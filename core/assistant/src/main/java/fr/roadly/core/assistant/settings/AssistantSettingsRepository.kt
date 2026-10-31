package fr.roadly.core.assistant.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantSettings(
    val enabled: Boolean = true,
    val consentGranted: Boolean = false,
    val dailyCapMinutes: Int = 30,
    /** Clé saisie dans Réglages, chiffrée par [SecretCipher] ; null = clé de build. */
    val encryptedKey: String? = null,
)

/** Usage du jour [day] (`yyyy-MM-dd`, fuseau local). */
data class AssistantUsage(val day: String = "", val tokens: Long = 0, val micSeconds: Long = 0)

interface AssistantSettingsRepository {
    val settings: Flow<AssistantSettings>
    val usage: Flow<AssistantUsage>
    suspend fun setEnabled(value: Boolean)
    suspend fun setConsent(value: Boolean)
    suspend fun setDailyCap(minutes: Int)
    suspend fun setEncryptedKey(value: String?)
    /** Ajoute à l'usage de [day] ; un autre jour que celui enregistré repart de zéro. */
    suspend fun addUsage(day: String, tokens: Long, micSeconds: Long)
}

@Singleton
class DataStoreAssistantSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : AssistantSettingsRepository {
    private val kEnabled = booleanPreferencesKey("assistant.enabled")
    private val kConsent = booleanPreferencesKey("assistant.consent")
    private val kCap = intPreferencesKey("assistant.cap.minutes")
    private val kKey = stringPreferencesKey("assistant.key.encrypted")
    private val kDay = stringPreferencesKey("assistant.usage.day")
    private val kTokens = longPreferencesKey("assistant.usage.tokens")
    private val kMic = longPreferencesKey("assistant.usage.mic")

    override val settings: Flow<AssistantSettings> = store.data.map {
        AssistantSettings(it[kEnabled] ?: true, it[kConsent] ?: false, it[kCap] ?: 30, it[kKey])
    }
    override val usage: Flow<AssistantUsage> = store.data.map { AssistantUsage(it[kDay].orEmpty(), it[kTokens] ?: 0, it[kMic] ?: 0) }

    override suspend fun setEnabled(value: Boolean) { store.edit { it[kEnabled] = value } }
    override suspend fun setConsent(value: Boolean) { store.edit { it[kConsent] = value } }
    override suspend fun setDailyCap(minutes: Int) { store.edit { it[kCap] = minutes.coerceIn(5, 240) } }
    override suspend fun setEncryptedKey(value: String?) { store.edit { if (value == null) it.remove(kKey) else it[kKey] = value } }
    override suspend fun addUsage(day: String, tokens: Long, micSeconds: Long) {
        store.edit {
            val same = it[kDay] == day
            it[kDay] = day
            it[kTokens] = (if (same) it[kTokens] ?: 0 else 0) + tokens
            it[kMic] = (if (same) it[kMic] ?: 0 else 0) + micSeconds
        }
    }
}
