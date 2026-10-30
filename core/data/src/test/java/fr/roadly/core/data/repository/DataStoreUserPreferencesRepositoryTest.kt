package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val repo = DataStoreUserPreferencesRepository(InMemoryPreferencesDataStore())

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
}
