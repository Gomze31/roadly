package fr.roadly.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ProviderConfigRepository {
    val config: Flow<ProviderConfig>
    suspend fun override(config: ProviderConfig)
    suspend fun reset()
}

@Singleton
class DataStoreProviderConfigRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : ProviderConfigRepository {
    private val kBan = stringPreferencesKey("provider.ban")
    private val kPhoton = stringPreferencesKey("provider.photon")
    private val kValhalla = stringPreferencesKey("provider.valhalla")
    private val kLight = stringPreferencesKey("provider.style.light")
    private val kDark = stringPreferencesKey("provider.style.dark")
    private val kOverpass = stringPreferencesKey("provider.overpass")

    override val config: Flow<ProviderConfig> = store.data.map { p ->
        val d = ProviderConfig.DEFAULT
        ProviderConfig(
            banBaseUrl = p[kBan] ?: d.banBaseUrl,
            photonBaseUrl = p[kPhoton] ?: d.photonBaseUrl,
            valhallaBaseUrl = p[kValhalla] ?: d.valhallaBaseUrl,
            mapStyleLightUrl = p[kLight] ?: d.mapStyleLightUrl,
            mapStyleDarkUrl = p[kDark] ?: d.mapStyleDarkUrl,
            overpassBaseUrl = p[kOverpass] ?: d.overpassBaseUrl,
        )
    }

    override suspend fun override(config: ProviderConfig) {
        val config = config.normalized()
        store.edit { p ->
            p[kBan] = config.banBaseUrl; p[kPhoton] = config.photonBaseUrl; p[kValhalla] = config.valhallaBaseUrl
            p[kLight] = config.mapStyleLightUrl; p[kDark] = config.mapStyleDarkUrl; p[kOverpass] = config.overpassBaseUrl
        }
    }

    override suspend fun reset() { store.edit { p -> listOf(kBan, kPhoton, kValhalla, kLight, kDark, kOverpass).forEach { p.remove(it) } } }
}
