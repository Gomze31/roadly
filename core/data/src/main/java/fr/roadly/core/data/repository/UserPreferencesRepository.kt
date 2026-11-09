package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
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
    /** E2 « Bâtiments 3D » (clé `map.3d`, ex-« Carte 3D en guidage ») : bâtiments extrudés en vue 3D. */
    const val MAP_3D = true
    /** Vue 3D des carrefours activée par défaut. */
    const val JUNCTION_3D = true
    /** Niveau de détail de la carte en guidage. */
    val MAP_DETAIL = MapDetail.NORMAL
    /** E2 : vue au départ du guidage ; sans choix enregistré, elle suit [MAP_3D] (migration). */
    val DEFAULT_CAMERA_MODE = CameraMode3D.FOLLOW_3D
    /** E2 : décalage de zoom du guidage, et valeurs admises (plus large, normal, plus proche). */
    const val ZOOM_OFFSET = 0
    val ALLOWED_ZOOM_OFFSETS = listOf(-1, 0, 1)
    /** E2 : inclinaison de croisière en 3D. */
    val PITCH_PRESET = PitchPreset.STANDARD
    /** E2 : carte orientée selon le cap ; non = nord en haut au départ. */
    const val AUTO_ROTATE = true
    /** E2 : ligne « puis … » sous le bandeau (D1). */
    const val SHOW_NEXT_MANEUVER = true
    /** E2 : économie d'énergie du guidage. */
    val POWER_SAVE = PowerSaveMode.AUTO
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
    /** Annonce vocale des baisses de limitation (« Limitation 70. »). */
    val limitDropVoice: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setLimitDropVoice(value: Boolean) = Unit
    /** Rappel de pause après deux heures de conduite. */
    val breakReminder: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setBreakReminder(value: Boolean) = Unit
    /** Annonce vocale des ralentisseurs (OSM) et des virages serrés sur le trajet. */
    val roadHazardVoice: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setRoadHazardVoice(value: Boolean) = Unit
    /** Limitation abaissée par temps de pluie (R413-2) quand une limitation est connue. */
    val rainLowersLimit: Flow<Boolean>
    suspend fun setRainLowersLimit(value: Boolean)
    /** E2 « Bâtiments 3D » (ex-« Carte 3D en guidage ») : bâtiments extrudés en vue 3D. */
    val map3dEnabled: Flow<Boolean>
    suspend fun setMap3dEnabled(value: Boolean)
    /** Vue 3D des carrefours (sorties, bifurcations, ronds-points) pendant le guidage. */
    val junction3dEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(GuidanceDefaults.JUNCTION_3D)
    suspend fun setJunction3dEnabled(value: Boolean) = Unit
    /** C2 : niveau de détail de la carte en guidage (REDUCED : sans points d'intérêt ni noms des petites rues). */
    val mapDetail: Flow<MapDetail>
    suspend fun setMapDetail(value: MapDetail)
    /** E2 : vue au départ du guidage (3D / 2D) ; tant qu'elle n'a pas été choisie, elle suit [map3dEnabled] (migration de l'ancien réglage). */
    val defaultCameraMode: Flow<CameraMode3D>
    suspend fun setDefaultCameraMode(value: CameraMode3D)
    /** E2 : décalage de zoom du guidage ; seules −1, 0 et +1 sont acceptées, toute autre valeur est ignorée. */
    val zoomOffset: Flow<Int>
    suspend fun setZoomOffset(value: Int)
    /** E2 : inclinaison de croisière en 3D. */
    val pitchPreset: Flow<PitchPreset>
    suspend fun setPitchPreset(value: PitchPreset)
    /** E2 : rotation automatique (cap en haut) ; non = nord en haut au départ. */
    val autoRotate: Flow<Boolean>
    suspend fun setAutoRotate(value: Boolean)
    /** E2 : ligne « puis … » de la manœuvre d'après sous le bandeau. */
    val showNextManeuverPreview: Flow<Boolean>
    suspend fun setShowNextManeuverPreview(value: Boolean)
    /** E2 : économie d'énergie du guidage (AUTO = économiseur d'Android). */
    val powerSaveMode: Flow<PowerSaveMode>
    suspend fun setPowerSaveMode(value: PowerSaveMode)
    /** Teinte du cockpit (nom de `CockpitTint`) ; null = violet par défaut. */
    val cockpitTint: Flow<String?>
    suspend fun setCockpitTint(name: String?)
    /** B6 : course en cours, mémorisée au démarrage du guidage ; null après un arrêt ou une arrivée. */
    val lastGuidance: Flow<LastGuidance?>
    suspend fun setLastGuidance(value: LastGuidance?)
    /** Accueil du premier lancement (7 étapes) terminé ou ignoré. */
    val onboardingDone: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setOnboardingDone(value: Boolean) = Unit
    /** Zones à faibles émissions : frontière sur la carte et alerte sur l'itinéraire selon la vignette Crit'Air. */
    val zfeEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setZfeEnabled(value: Boolean) = Unit
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) : UserPreferencesRepository {
    private val kTheme = stringPreferencesKey("theme.mode")
    private val kRationale = booleanPreferencesKey("location.rationale.seen")
    private val kDeniedForever = booleanPreferencesKey("location.denied.forever")
    private val kAvoidTolls = booleanPreferencesKey("route.avoid.tolls")
    private val kAvoidHighways = booleanPreferencesKey("route.avoid.highways")
    private val kAvoidFerries = booleanPreferencesKey("route.avoid.ferries")
    private val kAvoidTunnels = booleanPreferencesKey("route.avoid.tunnels")
    private val kAvoidUnpaved = booleanPreferencesKey("route.avoid.unpaved")
    private val kVoiceEnabled = booleanPreferencesKey("voice.enabled")
    private val kVoiceVolume = intPreferencesKey("voice.volume")
    private val kTolerance = intPreferencesKey("speed.alert.tolerance")
    private val kSpeedSound = booleanPreferencesKey("speed.alert.sound")
    private val kVigilance = booleanPreferencesKey("vigilance.enabled")
    private val kVigilanceSound = booleanPreferencesKey("vigilance.sound")
    private val kLimitDropVoice = booleanPreferencesKey("coach.limit.drop.voice")
    private val kBreakReminder = booleanPreferencesKey("coach.break.reminder")
    private val kRoadHazardVoice = booleanPreferencesKey("coach.road.hazard.voice")
    private val kRainLimit = booleanPreferencesKey("rain.lowers.limit")
    private val kMap3d = booleanPreferencesKey("map.3d")
    private val kJunction3d = booleanPreferencesKey("map.junction3d")
    private val kMapDetail = stringPreferencesKey("map.detail")
    private val kTint = stringPreferencesKey("cockpit.tint")
    private val kCameraDefault = stringPreferencesKey("map.camera.default")
    private val kZoomOffset = intPreferencesKey("map.zoom.offset")
    private val kPitch = stringPreferencesKey("map.pitch")
    private val kAutoRotate = booleanPreferencesKey("map.auto.rotate")
    private val kThenPreview = booleanPreferencesKey("guidance.then.preview")
    private val kPowerSave = stringPreferencesKey("power.save.mode")
    private val kLastLat = doublePreferencesKey("guidance.last.lat")
    private val kLastLon = doublePreferencesKey("guidance.last.lon")
    private val kLastName = stringPreferencesKey("guidance.last.name")
    private val kLastRoute = stringPreferencesKey("guidance.last.route")
    private val kLastAt = longPreferencesKey("guidance.last.at")
    private val kOnboarding = booleanPreferencesKey("onboarding.done")
    private val kZfe = booleanPreferencesKey("zfe.enabled")

    override val themeMode: Flow<ThemeMode> = store.data.map { p -> p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO_SOLAR }
    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[kTheme] = mode.name } }
    override val locationRationaleSeen: Flow<Boolean> = store.data.map { it[kRationale] ?: false }
    override suspend fun setLocationRationaleSeen() { store.edit { it[kRationale] = true } }
    override val locationDeniedForever: Flow<Boolean> = store.data.map { it[kDeniedForever] ?: false }
    override suspend fun setLocationDeniedForever(value: Boolean) { store.edit { it[kDeniedForever] = value } }

    override val routePreferences: Flow<RoutePreferences> = store.data.map { p ->
        RoutePreferences(avoidTolls = p[kAvoidTolls] ?: false, avoidHighways = p[kAvoidHighways] ?: false, avoidFerries = p[kAvoidFerries] ?: false, avoidTunnels = p[kAvoidTunnels] ?: false, avoidUnpaved = p[kAvoidUnpaved] ?: false)
    }
    override suspend fun setRoutePreferences(value: RoutePreferences) {
        store.edit { it[kAvoidTolls] = value.avoidTolls; it[kAvoidHighways] = value.avoidHighways; it[kAvoidFerries] = value.avoidFerries; it[kAvoidTunnels] = value.avoidTunnels; it[kAvoidUnpaved] = value.avoidUnpaved }
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
    override val limitDropVoice: Flow<Boolean> = store.data.map { it[kLimitDropVoice] ?: true }
    override suspend fun setLimitDropVoice(value: Boolean) { store.edit { it[kLimitDropVoice] = value } }
    override val breakReminder: Flow<Boolean> = store.data.map { it[kBreakReminder] ?: true }
    override suspend fun setBreakReminder(value: Boolean) { store.edit { it[kBreakReminder] = value } }
    override val roadHazardVoice: Flow<Boolean> = store.data.map { it[kRoadHazardVoice] ?: true }
    override suspend fun setRoadHazardVoice(value: Boolean) { store.edit { it[kRoadHazardVoice] = value } }
    override val rainLowersLimit: Flow<Boolean> = store.data.map { it[kRainLimit] ?: GuidanceDefaults.RAIN_LOWERS_LIMIT }
    override suspend fun setRainLowersLimit(value: Boolean) { store.edit { it[kRainLimit] = value } }
    override val map3dEnabled: Flow<Boolean> = store.data.map { it[kMap3d] ?: GuidanceDefaults.MAP_3D }
    override suspend fun setMap3dEnabled(value: Boolean) { store.edit { it[kMap3d] = value } }
    override val junction3dEnabled: Flow<Boolean> = store.data.map { it[kJunction3d] ?: GuidanceDefaults.JUNCTION_3D }
    override suspend fun setJunction3dEnabled(value: Boolean) { store.edit { it[kJunction3d] = value } }
    override val mapDetail: Flow<MapDetail> = store.data.map { p -> p[kMapDetail]?.let { runCatching { MapDetail.valueOf(it) }.getOrNull() } ?: GuidanceDefaults.MAP_DETAIL }
    override suspend fun setMapDetail(value: MapDetail) { store.edit { it[kMapDetail] = value.name } }
    override val defaultCameraMode: Flow<CameraMode3D> = store.data.map { p ->
        p[kCameraDefault]?.let { runCatching { CameraMode3D.valueOf(it) }.getOrNull() } ?: CameraMode3D.default(p[kMap3d] ?: GuidanceDefaults.MAP_3D)
    }
    override suspend fun setDefaultCameraMode(value: CameraMode3D) { store.edit { it[kCameraDefault] = value.name } }
    override val zoomOffset: Flow<Int> = store.data.map { p -> p[kZoomOffset]?.takeIf { it in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS } ?: GuidanceDefaults.ZOOM_OFFSET }
    override suspend fun setZoomOffset(value: Int) {
        if (value !in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS) return
        store.edit { it[kZoomOffset] = value }
    }
    override val pitchPreset: Flow<PitchPreset> = store.data.map { p -> p[kPitch]?.let { runCatching { PitchPreset.valueOf(it) }.getOrNull() } ?: GuidanceDefaults.PITCH_PRESET }
    override suspend fun setPitchPreset(value: PitchPreset) { store.edit { it[kPitch] = value.name } }
    override val autoRotate: Flow<Boolean> = store.data.map { it[kAutoRotate] ?: GuidanceDefaults.AUTO_ROTATE }
    override suspend fun setAutoRotate(value: Boolean) { store.edit { it[kAutoRotate] = value } }
    override val showNextManeuverPreview: Flow<Boolean> = store.data.map { it[kThenPreview] ?: GuidanceDefaults.SHOW_NEXT_MANEUVER }
    override suspend fun setShowNextManeuverPreview(value: Boolean) { store.edit { it[kThenPreview] = value } }
    override val powerSaveMode: Flow<PowerSaveMode> = store.data.map { p -> p[kPowerSave]?.let { runCatching { PowerSaveMode.valueOf(it) }.getOrNull() } ?: GuidanceDefaults.POWER_SAVE }
    override suspend fun setPowerSaveMode(value: PowerSaveMode) { store.edit { it[kPowerSave] = value.name } }
    override val cockpitTint: Flow<String?> = store.data.map { it[kTint] }
    override suspend fun setCockpitTint(name: String?) { store.edit { if (name == null) it.remove(kTint) else it[kTint] = name } }
    override val onboardingDone: Flow<Boolean> = store.data.map { it[kOnboarding] ?: false }
    override suspend fun setOnboardingDone(value: Boolean) { store.edit { it[kOnboarding] = value } }
    override val zfeEnabled: Flow<Boolean> = store.data.map { it[kZfe] ?: true }
    override suspend fun setZfeEnabled(value: Boolean) { store.edit { it[kZfe] = value } }
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
