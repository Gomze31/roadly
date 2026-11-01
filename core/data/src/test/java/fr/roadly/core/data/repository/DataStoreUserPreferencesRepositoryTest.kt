package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DataStore en mémoire : le `DataStore` fichier échoue sous Windows au second `edit` (JVM : `File.renameTo` ne remplace pas
 * une cible existante), alors que la logique testée ici (clés, bornes, valeurs par défaut) ne dépend pas du stockage disque.
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock { transform(state.value).also { state.value = it } }
}

class DataStoreUserPreferencesRepositoryTest {
    private val store = InMemoryPreferencesDataStore()
    private val repo = DataStoreUserPreferencesRepository(store)

    @Test fun `guidance defaults`() = runTest {
        assertEquals(RoutePreferences(), repo.routePreferences.first())
        assertEquals(GuidanceDefaults.VOICE_ENABLED, repo.voiceEnabled.first())
        assertEquals(GuidanceDefaults.VOICE_VOLUME, repo.voiceVolume.first())
        assertEquals(GuidanceDefaults.SPEED_TOLERANCE_KMH, repo.speedAlertToleranceKmh.first())
        assertEquals(GuidanceDefaults.SPEED_ALERT_SOUND, repo.speedAlertSound.first())
    }

    @Test fun `route preferences round trip`() = runTest {
        repo.setRoutePreferences(RoutePreferences(avoidTolls = true, avoidHighways = false, avoidFerries = true))
        assertEquals(RoutePreferences(avoidTolls = true, avoidHighways = false, avoidFerries = true), repo.routePreferences.first())
    }

    @Test fun `voice volume is clamped to 0-100`() = runTest {
        repo.setVoiceVolume(140); assertEquals(100, repo.voiceVolume.first())
        repo.setVoiceVolume(-3); assertEquals(0, repo.voiceVolume.first())
        repo.setVoiceVolume(42); assertEquals(42, repo.voiceVolume.first())
    }

    @Test fun `tolerance accepts only 0 5 10`() = runTest {
        repo.setSpeedAlertToleranceKmh(10); assertEquals(10, repo.speedAlertToleranceKmh.first())
        repo.setSpeedAlertToleranceKmh(7); assertEquals(10, repo.speedAlertToleranceKmh.first())
        repo.setSpeedAlertToleranceKmh(0); assertEquals(0, repo.speedAlertToleranceKmh.first())
    }

    @Test fun `voice switch and over speed sound persist`() = runTest {
        repo.setVoiceEnabled(false); repo.setSpeedAlertSound(true)
        assertEquals(false, repo.voiceEnabled.first())
        assertEquals(true, repo.speedAlertSound.first())
    }

    @Test fun `last guidance round trip and clear`() = runTest {
        assertNull(repo.lastGuidance.first())
        val saved = LastGuidance(GeoPoint(48.8738, 2.2950), "Arc de Triomphe", "route-1", 1_782_131_520_000L)
        repo.setLastGuidance(saved)
        assertEquals(saved, repo.lastGuidance.first())
        repo.setLastGuidance(saved.copy(destinationName = null))
        assertEquals(null, repo.lastGuidance.first()!!.destinationName)
        repo.setLastGuidance(null)
        assertNull(repo.lastGuidance.first())
    }

    @Test fun `map detail defaults to normal, persists and ignores unknown values`() = runTest {
        assertEquals(MapDetail.NORMAL, repo.mapDetail.first())
        repo.setMapDetail(MapDetail.REDUCED)
        assertEquals(MapDetail.REDUCED, repo.mapDetail.first())
        store.edit { it[stringPreferencesKey("map.detail")] = "ULTRA" }
        assertEquals(MapDetail.NORMAL, repo.mapDetail.first())
    }

    @Test fun `navigation display settings default, persist and ignore invalid values`() = runTest {
        assertEquals(CameraMode3D.FOLLOW_3D, repo.defaultCameraMode.first())
        assertEquals(0, repo.zoomOffset.first())
        assertEquals(PitchPreset.STANDARD, repo.pitchPreset.first())
        assertEquals(true, repo.autoRotate.first())
        assertEquals(true, repo.showNextManeuverPreview.first())
        assertEquals(PowerSaveMode.AUTO, repo.powerSaveMode.first())
        repo.setDefaultCameraMode(CameraMode3D.FOLLOW_2D); repo.setZoomOffset(-1); repo.setPitchPreset(PitchPreset.HIGH)
        repo.setAutoRotate(false); repo.setShowNextManeuverPreview(false); repo.setPowerSaveMode(PowerSaveMode.ON)
        assertEquals(CameraMode3D.FOLLOW_2D, repo.defaultCameraMode.first())
        assertEquals(-1, repo.zoomOffset.first())
        assertEquals(PitchPreset.HIGH, repo.pitchPreset.first())
        assertEquals(false, repo.autoRotate.first())
        assertEquals(false, repo.showNextManeuverPreview.first())
        assertEquals(PowerSaveMode.ON, repo.powerSaveMode.first())
        repo.setZoomOffset(2)
        assertEquals("hors de −1 / 0 / +1 : ignoré", -1, repo.zoomOffset.first())
        store.edit {
            it[stringPreferencesKey("map.pitch")] = "VERTICAL"; it[stringPreferencesKey("power.save.mode")] = "TURBO"
            it[stringPreferencesKey("map.camera.default")] = "ORBIT"; it[intPreferencesKey("map.zoom.offset")] = 5
        }
        assertEquals(PitchPreset.STANDARD, repo.pitchPreset.first())
        assertEquals(PowerSaveMode.AUTO, repo.powerSaveMode.first())
        assertEquals(CameraMode3D.FOLLOW_3D, repo.defaultCameraMode.first())
        assertEquals(0, repo.zoomOffset.first())
    }

    @Test fun `without a chosen start view, the former 3d map setting decides it`() = runTest {
        repo.setMap3dEnabled(false)
        assertEquals(CameraMode3D.FOLLOW_2D, repo.defaultCameraMode.first())
        repo.setDefaultCameraMode(CameraMode3D.FOLLOW_3D)
        assertEquals("choix explicite : indépendant des bâtiments", CameraMode3D.FOLLOW_3D, repo.defaultCameraMode.first())
        assertEquals(false, repo.map3dEnabled.first())
    }
}
