package fr.roadly.core.data.testing

import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

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
    val vigilance = MutableStateFlow(GuidanceDefaults.VIGILANCE_ENABLED)
    val vigilanceBeep = MutableStateFlow(GuidanceDefaults.VIGILANCE_SOUND)
    val rainLimit = MutableStateFlow(GuidanceDefaults.RAIN_LOWERS_LIMIT)
    override val vigilanceEnabled: Flow<Boolean> = vigilance
    override suspend fun setVigilanceEnabled(value: Boolean) { vigilance.value = value }
    override val vigilanceSound: Flow<Boolean> = vigilanceBeep
    override suspend fun setVigilanceSound(value: Boolean) { vigilanceBeep.value = value }
    override val rainLowersLimit: Flow<Boolean> = rainLimit
    override suspend fun setRainLowersLimit(value: Boolean) { rainLimit.value = value }
    val map3d = MutableStateFlow(GuidanceDefaults.MAP_3D)
    override val map3dEnabled: Flow<Boolean> = map3d
    override suspend fun setMap3dEnabled(value: Boolean) { map3d.value = value }
    val junction3d = MutableStateFlow(GuidanceDefaults.JUNCTION_3D)
    override val junction3dEnabled: Flow<Boolean> = junction3d
    override suspend fun setJunction3dEnabled(value: Boolean) { junction3d.value = value }
    val detail = MutableStateFlow(GuidanceDefaults.MAP_DETAIL)
    override val mapDetail: Flow<MapDetail> = detail
    override suspend fun setMapDetail(value: MapDetail) { detail.value = value }
    /** E2 : vue au départ choisie ; null = jamais choisie (elle suit alors [map3d], comme l'implémentation DataStore). */
    val cameraDefault = MutableStateFlow<CameraMode3D?>(null)
    override val defaultCameraMode: Flow<CameraMode3D> = combine(cameraDefault, map3d) { chosen, on -> chosen ?: CameraMode3D.default(on) }
    override suspend fun setDefaultCameraMode(value: CameraMode3D) { cameraDefault.value = value }
    val zoom = MutableStateFlow(GuidanceDefaults.ZOOM_OFFSET)
    override val zoomOffset: Flow<Int> = zoom
    override suspend fun setZoomOffset(value: Int) { if (value in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS) zoom.value = value }
    val pitch = MutableStateFlow(GuidanceDefaults.PITCH_PRESET)
    override val pitchPreset: Flow<PitchPreset> = pitch
    override suspend fun setPitchPreset(value: PitchPreset) { pitch.value = value }
    val rotate = MutableStateFlow(GuidanceDefaults.AUTO_ROTATE)
    override val autoRotate: Flow<Boolean> = rotate
    override suspend fun setAutoRotate(value: Boolean) { rotate.value = value }
    val thenPreview = MutableStateFlow(GuidanceDefaults.SHOW_NEXT_MANEUVER)
    override val showNextManeuverPreview: Flow<Boolean> = thenPreview
    override suspend fun setShowNextManeuverPreview(value: Boolean) { thenPreview.value = value }
    val powerSave = MutableStateFlow(GuidanceDefaults.POWER_SAVE)
    override val powerSaveMode: Flow<PowerSaveMode> = powerSave
    override suspend fun setPowerSaveMode(value: PowerSaveMode) { powerSave.value = value }
    val tint = MutableStateFlow<String?>(null)
    override val cockpitTint: Flow<String?> = tint
    override suspend fun setCockpitTint(name: String?) { tint.value = name }
    val last = MutableStateFlow<LastGuidance?>(null)
    override val lastGuidance: Flow<LastGuidance?> = last
    override suspend fun setLastGuidance(value: LastGuidance?) { last.value = value }
}
