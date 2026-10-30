package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO_SOLAR }

interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    val locationRationaleSeen: Flow<Boolean>
    suspend fun setLocationRationaleSeen()
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) : UserPreferencesRepository {
    private val kTheme = stringPreferencesKey("theme.mode")
    private val kRationale = booleanPreferencesKey("location.rationale.seen")
    override val themeMode: Flow<ThemeMode> = store.data.map { p -> p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO_SOLAR }
    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[kTheme] = mode.name } }
    override val locationRationaleSeen: Flow<Boolean> = store.data.map { it[kRationale] ?: false }
    override suspend fun setLocationRationaleSeen() { store.edit { it[kRationale] = true } }
}
