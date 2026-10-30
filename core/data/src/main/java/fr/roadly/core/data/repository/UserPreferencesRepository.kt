package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO_SOLAR }

/** Valeurs par défaut et bornes des réglages de guidage (spec Phase 2 §2 et §6). */
object GuidanceDefaults {
    const val VOICE_ENABLED = true
    const val VOICE_VOLUME = 100
    const val SPEED_TOLERANCE_KMH = 5
    /** L'alerte sonore de dépassement est une option : désactivée par défaut. */
    const val SPEED_ALERT_SOUND = false
    val ALLOWED_TOLERANCES_KMH = listOf(0, 5, 10)
}

interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    val locationRationaleSeen: Flow<Boolean>
    suspend fun setLocationRationaleSeen()
    val locationDeniedForever: Flow<Boolean>
    suspend fun setLocationDeniedForever(value: Boolean)

    val routePreferences: Flow<RoutePreferences>
    suspend fun setRoutePreferences(value: RoutePreferences)
    val voiceEnabled: Flow<Boolean>
    suspend fun setVoiceEnabled(value: Boolean)
    /** Volume des annonces 0–100 (appliqué via `TextToSpeech.Engine.KEY_PARAM_VOLUME`). */
    val voiceVolume: Flow<Int>
    /** Borné à 0–100. */
    suspend fun setVoiceVolume(value: Int)
    val speedAlertToleranceKmh: Flow<Int>
    /** Seules 0, 5 et 10 km/h sont acceptées ; toute autre valeur est ignorée. */
    suspend fun setSpeedAlertToleranceKmh(value: Int)
    val speedAlertSound: Flow<Boolean>
    suspend fun setSpeedAlertSound(value: Boolean)
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) : UserPreferencesRepository {
    private val kTheme = stringPreferencesKey("theme.mode")
    private val kRationale = booleanPreferencesKey("location.rationale.seen")
    private val kDeniedForever = booleanPreferencesKey("location.denied.forever")
    private val kAvoidTolls = booleanPreferencesKey("route.avoid.tolls")
    private val kAvoidHighways = booleanPreferencesKey("route.avoid.highways")
    private val kAvoidFerries = booleanPreferencesKey("route.avoid.ferries")
    private val kVoiceEnabled = booleanPreferencesKey("voice.enabled")
    private val kVoiceVolume = intPreferencesKey("voice.volume")
    private val kTolerance = intPreferencesKey("speed.alert.tolerance")
    private val kSpeedSound = booleanPreferencesKey("speed.alert.sound")

    override val themeMode: Flow<ThemeMode> = store.data.map { p -> p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO_SOLAR }
    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[kTheme] = mode.name } }
    override val locationRationaleSeen: Flow<Boolean> = store.data.map { it[kRationale] ?: false }
    override suspend fun setLocationRationaleSeen() { store.edit { it[kRationale] = true } }
    override val locationDeniedForever: Flow<Boolean> = store.data.map { it[kDeniedForever] ?: false }
    override suspend fun setLocationDeniedForever(value: Boolean) { store.edit { it[kDeniedForever] = value } }

    override val routePreferences: Flow<RoutePreferences> = store.data.map { p ->
        RoutePreferences(avoidTolls = p[kAvoidTolls] ?: false, avoidHighways = p[kAvoidHighways] ?: false, avoidFerries = p[kAvoidFerries] ?: false)
    }
    override suspend fun setRoutePreferences(value: RoutePreferences) {
        store.edit { it[kAvoidTolls] = value.avoidTolls; it[kAvoidHighways] = value.avoidHighways; it[kAvoidFerries] = value.avoidFerries }
    }
    override val voiceEnabled: Flow<Boolean> = store.data.map { it[kVoiceEnabled] ?: GuidanceDefaults.VOICE_ENABLED }
    override suspend fun setVoiceEnabled(value: Boolean) { store.edit { it[kVoiceEnabled] = value } }
    override val voiceVolume: Flow<Int> = store.data.map { (it[kVoiceVolume] ?: GuidanceDefaults.VOICE_VOLUME).coerceIn(0, 100) }
    override suspend fun setVoiceVolume(value: Int) { store.edit { it[kVoiceVolume] = value.coerceIn(0, 100) } }
    override val speedAlertToleranceKmh: Flow<Int> = store.data.map { p ->
        p[kTolerance]?.takeIf { it in GuidanceDefaults.ALLOWED_TOLERANCES_KMH } ?: GuidanceDefaults.SPEED_TOLERANCE_KMH
    }
    override suspend fun setSpeedAlertToleranceKmh(value: Int) {
        if (value !in GuidanceDefaults.ALLOWED_TOLERANCES_KMH) return
        store.edit { it[kTolerance] = value }
    }
    override val speedAlertSound: Flow<Boolean> = store.data.map { it[kSpeedSound] ?: GuidanceDefaults.SPEED_ALERT_SOUND }
    override suspend fun setSpeedAlertSound(value: Boolean) { store.edit { it[kSpeedSound] = value } }
}
