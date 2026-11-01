package fr.roadly.core.data.update

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.common.update.AppUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Chiffrement AES-GCM par une clé du Keystore Android (non exportable) : le jeton GitHub n'est jamais stocké en clair. */
@Singleton
class KeystoreTokenCipher @Inject constructor() {
    private val alias = "roadly_github_token"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** Null si la donnée est illisible (clé supprimée, sauvegarde restaurée ailleurs…). */
    fun decrypt(encoded: String): String? = runCatching {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12))) }
        String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }.getOrNull()
}

@Singleton
class DataStoreUpdateSettings @Inject constructor(
    private val store: DataStore<Preferences>,
    private val cipher: KeystoreTokenCipher,
) : UpdateSettings {
    private val kRepo = stringPreferencesKey("update.repository")
    private val kToken = stringPreferencesKey("update.token.enc")
    private val kAuto = booleanPreferencesKey("update.auto")
    private val kLast = longPreferencesKey("update.last.check")
    private val kSkipped = intPreferencesKey("update.skipped")

    override val repository: Flow<String> = store.data.map { it[kRepo] ?: AppUpdates.DEFAULT_REPOSITORY }
    override suspend fun setRepository(value: String) { store.edit { it[kRepo] = value.trim() } }
    override val token: Flow<String?> = store.data.map { p -> p[kToken]?.let { cipher.decrypt(it) } }
    override suspend fun setToken(value: String?) {
        store.edit { if (value.isNullOrBlank()) it.remove(kToken) else it[kToken] = cipher.encrypt(value.trim()) }
    }
    override val autoCheck: Flow<Boolean> = store.data.map { it[kAuto] ?: true }
    override suspend fun setAutoCheck(value: Boolean) { store.edit { it[kAuto] = value } }
    override val lastCheckAtMs: Flow<Long?> = store.data.map { it[kLast] }
    override suspend fun setLastCheckAtMs(value: Long) { store.edit { it[kLast] = value } }
    override val skippedVersionCode: Flow<Int?> = store.data.map { it[kSkipped] }
    override suspend fun setSkippedVersionCode(value: Int?) { store.edit { if (value == null) it.remove(kSkipped) else it[kSkipped] = value } }
}
