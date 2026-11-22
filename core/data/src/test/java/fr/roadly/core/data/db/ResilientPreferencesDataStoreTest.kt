package fr.roadly.core.data.db

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import fr.roadly.core.data.repository.DataStoreUserPreferencesRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** NAV-6 : une erreur de lecture des réglages ne remonte plus jusqu'au guidage. */
class ResilientPreferencesDataStoreTest {
    private val voice = booleanPreferencesKey("voice.enabled")

    /** Magasin dont la lecture rend [content] puis échoue avec [failure] (si elle est fournie). */
    private class ScriptedStore(var content: Preferences?, var failure: Throwable?) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            content?.let { emit(it) }
            failure?.let { throw it }
        }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(content ?: emptyPreferences()).also { content = it }
    }

    @Test fun `a read error yields empty preferences, so every setting reads its default`() = runTest {
        val repo = DataStoreUserPreferencesRepository(ResilientPreferencesDataStore(ScriptedStore(null, IOException("disque"))))
        assertEquals(GuidanceDefaults.VOICE_ENABLED, repo.voiceEnabled.first())
        assertEquals(GuidanceDefaults.VOICE_VOLUME, repo.voiceVolume.first())
        assertEquals(GuidanceDefaults.SPEED_TOLERANCE_KMH, repo.speedAlertToleranceKmh.first())
    }

    @Test fun `a corrupted file is read as empty preferences`() = runTest {
        val store = ResilientPreferencesDataStore(ScriptedStore(null, CorruptionException("illisible")))
        assertEquals(emptyPreferences(), store.data.first())
    }

    @Test fun `after a good read, an error yields the last content read, not the defaults`() = runTest {
        val scripted = ScriptedStore(preferencesOf(voice to false), null)
        val store = ResilientPreferencesDataStore(scripted)
        assertFalse(store.data.first()[voice]!!)
        scripted.content = null
        scripted.failure = IOException("disque")
        assertFalse("la voix coupée par l'utilisateur le reste", store.data.first()[voice]!!)
    }

    @Test fun `a value written is the one read back after a later read error`() = runTest {
        val scripted = ScriptedStore(null, null)
        val store = ResilientPreferencesDataStore(scripted)
        store.edit { it[voice] = false }
        scripted.content = null
        scripted.failure = IOException("disque")
        assertFalse(store.data.first()[voice]!!)
    }

    @Test fun `the flow emits the good content then the fallback, and completes`() = runTest {
        val store = ResilientPreferencesDataStore(ScriptedStore(preferencesOf(voice to true), IOException("disque")))
        val all = store.data.toList()
        assertEquals(2, all.size)
        assertTrue(all.all { it[voice] == true })
    }

    @Test fun `an error that is not an input-output error is not hidden`() = runTest {
        val store = ResilientPreferencesDataStore(ScriptedStore(null, IllegalStateException("bogue")))
        try {
            store.data.first()
            fail("l'erreur devait remonter")
        } catch (e: IllegalStateException) {
            assertEquals("bogue", e.message)
        }
    }
}
