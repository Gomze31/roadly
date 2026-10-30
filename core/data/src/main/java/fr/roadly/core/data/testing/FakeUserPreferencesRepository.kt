package fr.roadly.core.data.testing

import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Double de test en mémoire, mêmes règles de bornage que l'implémentation DataStore. */
class FakeUserPreferencesRepository : UserPreferencesRepository {
    val theme = MutableStateFlow(ThemeMode.AUTO_SOLAR)
    val rationaleSeen = MutableStateFlow(false)
    val deniedForever = MutableStateFlow(false)
    val route = MutableStateFlow(RoutePreferences())
    val voice = MutableStateFlow(GuidanceDefaults.VOICE_ENABLED)
    val volume = MutableStateFlow(GuidanceDefaults.VOICE_VOLUME)
    val tolerance = MutableStateFlow(GuidanceDefaults.SPEED_TOLERANCE_KMH)
    val sound = MutableStateFlow(GuidanceDefaults.SPEED_ALERT_SOUND)

    override val themeMode: Flow<ThemeMode> = theme
    override suspend fun setThemeMode(mode: ThemeMode) { theme.value = mode }
    override val locationRationaleSeen: Flow<Boolean> = rationaleSeen
    override suspend fun setLocationRationaleSeen() { rationaleSeen.value = true }
    override val locationDeniedForever: Flow<Boolean> = deniedForever
    override suspend fun setLocationDeniedForever(value: Boolean) { deniedForever.value = value }
    override val routePreferences: Flow<RoutePreferences> = route
    override suspend fun setRoutePreferences(value: RoutePreferences) { route.value = value }
    override val voiceEnabled: Flow<Boolean> = voice
    override suspend fun setVoiceEnabled(value: Boolean) { voice.value = value }
    override val voiceVolume: Flow<Int> = volume
    override suspend fun setVoiceVolume(value: Int) { volume.value = value.coerceIn(0, 100) }
    override val speedAlertToleranceKmh: Flow<Int> = tolerance
    override suspend fun setSpeedAlertToleranceKmh(value: Int) { if (value in GuidanceDefaults.ALLOWED_TOLERANCES_KMH) tolerance.value = value }
    override val speedAlertSound: Flow<Boolean> = sound
    override suspend fun setSpeedAlertSound(value: Boolean) { sound.value = value }
}
