package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.GeoPoint
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
    const val VIGILANCE_ENABLED = true
    const val VIGILANCE_SOUND = true
    const val RAIN_LOWERS_LIMIT = false
    /** Carte inclinée + bâtiments 3D pendant le guidage. */
    const val MAP_3D = true
}

/** B6 : course de guidage en cours ou interrompue (mort du processus) — base du « Reprendre le guidage » de l'Étape E3. */
data class LastGuidance(val destination: GeoPoint, val destinationName: String?, val routeId: String, val startedAtEpochMs: Long)

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

    /** Zones de vigilance (France) : annonces et affichage dans Horizon. */
    val vigilanceEnabled: Flow<Boolean>
    suspend fun setVigilanceEnabled(value: Boolean)
    /** Bip à l'entrée d'une zone de vigilance. */
    val vigilanceSound: Flow<Boolean>
    suspend fun setVigilanceSound(value: Boolean)
    /** Limitation abaissée par temps de pluie (R413-2) quand une limitation est connue. */
    val rainLowersLimit: Flow<Boolean>
    suspend fun setRainLowersLimit(value: Boolean)
    /** Perspective 3D et bâtiments extrudés pendant le guidage. */
    val map3dEnabled: Flow<Boolean>
    suspend fun setMap3dEnabled(value: Boolean)
    /** Teinte du cockpit (nom de `CockpitTint`) ; null = violet par défaut. */
    val cockpitTint: Flow<String?>
    suspend fun setCockpitTint(name: String?)
    /** B6 : course en cours, mémorisée au démarrage du guidage ; null après un arrêt ou une arrivée. */
    val lastGuidance: Flow<LastGuidance?>
    suspend fun setLastGuidance(value: LastGuidance?)
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
    private val kVigilance = booleanPreferencesKey("vigilance.enabled")
    private val kVigilanceSound = booleanPreferencesKey("vigilance.sound")
    private val kRainLimit = booleanPreferencesKey("rain.lowers.limit")
    private val kMap3d = booleanPreferencesKey("map.3d")
    private val kTint = stringPreferencesKey("cockpit.tint")
    private val kLastLat = doublePreferencesKey("guidance.last.lat")
    private val kLastLon = doublePreferencesKey("guidance.last.lon")
    private val kLastName = stringPreferencesKey("guidance.last.name")
    private val kLastRoute = stringPreferencesKey("guidance.last.route")
    private val kLastAt = longPreferencesKey("guidance.last.at")

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
    override val vigilanceEnabled: Flow<Boolean> = store.data.map { it[kVigilance] ?: GuidanceDefaults.VIGILANCE_ENABLED }
    override suspend fun setVigilanceEnabled(value: Boolean) { store.edit { it[kVigilance] = value } }
    override val vigilanceSound: Flow<Boolean> = store.data.map { it[kVigilanceSound] ?: GuidanceDefaults.VIGILANCE_SOUND }
    override suspend fun setVigilanceSound(value: Boolean) { store.edit { it[kVigilanceSound] = value } }
    override val rainLowersLimit: Flow<Boolean> = store.data.map { it[kRainLimit] ?: GuidanceDefaults.RAIN_LOWERS_LIMIT }
    override suspend fun setRainLowersLimit(value: Boolean) { store.edit { it[kRainLimit] = value } }
    override val map3dEnabled: Flow<Boolean> = store.data.map { it[kMap3d] ?: GuidanceDefaults.MAP_3D }
    override suspend fun setMap3dEnabled(value: Boolean) { store.edit { it[kMap3d] = value } }
    override val cockpitTint: Flow<String?> = store.data.map { it[kTint] }
    override suspend fun setCockpitTint(name: String?) { store.edit { if (name == null) it.remove(kTint) else it[kTint] = name } }
    override val lastGuidance: Flow<LastGuidance?> = store.data.map { p ->
        val lat = p[kLastLat]; val lon = p[kLastLon]; val routeId = p[kLastRoute]; val at = p[kLastAt]
        if (lat == null || lon == null || routeId == null || at == null) null else LastGuidance(GeoPoint(lat, lon), p[kLastName], routeId, at)
    }
    override suspend fun setLastGuidance(value: LastGuidance?) {
        store.edit {
            if (value == null) {
                it.remove(kLastLat); it.remove(kLastLon); it.remove(kLastName); it.remove(kLastRoute); it.remove(kLastAt)
            } else {
                it[kLastLat] = value.destination.lat; it[kLastLon] = value.destination.lon
                it[kLastRoute] = value.routeId; it[kLastAt] = value.startedAtEpochMs
                if (value.destinationName == null) it.remove(kLastName) else it[kLastName] = value.destinationName
            }
        }
    }
}
