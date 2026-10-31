package fr.roadly.core.data.community

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Accès au projet Supabase de l'exploitant ; [isConfigured] = URL https valide (clair toléré seulement en local) et clé non vide. */
data class CommunityConfig(val supabaseUrl: String = "", val anonKey: String = "") {
    val isConfigured: Boolean get() {
        val url = supabaseUrl.toHttpUrlOrNull() ?: return false
        return anonKey.isNotBlank() && (url.isHttps || url.host == "localhost" || url.host == "127.0.0.1")
    }
}

interface CommunityConfigRepository {
    val config: Flow<CommunityConfig>
    suspend fun save(config: CommunityConfig)
    suspend fun clear()
    /** Pseudonyme d'appareil (UUID), généré une fois et conservé ; aucune donnée personnelle. */
    suspend fun deviceId(): String
}

@Singleton
class DataStoreCommunityConfigRepository @Inject constructor(private val store: DataStore<Preferences>) : CommunityConfigRepository {
    private val kUrl = stringPreferencesKey("community.supabase.url")
    private val kKey = stringPreferencesKey("community.supabase.anon")
    private val kDevice = stringPreferencesKey("community.device.id")

    override val config: Flow<CommunityConfig> = store.data.map { CommunityConfig(it[kUrl].orEmpty(), it[kKey].orEmpty()) }
    override suspend fun save(config: CommunityConfig) { store.edit { it[kUrl] = config.supabaseUrl.trim().trimEnd('/'); it[kKey] = config.anonKey.trim() } }
    override suspend fun clear() { store.edit { it.remove(kUrl); it.remove(kKey) } }
    override suspend fun deviceId(): String {
        store.data.first()[kDevice]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { it[kDevice] = id }
        return id
    }
}
