package fr.roadly.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** VAL-4 : adresse de la BAN (Géoplateforme) et reprise des réglages enregistrés avec l'ancienne adresse. */
class ProviderConfigRepositoryTest {
    private class InMemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(state.value).also { state.value = it }
    }

    private val repo = DataStoreProviderConfigRepository(InMemoryStore())

    @Test fun `the default address search host is the Geoplateforme`() = runTest {
        assertEquals("https://data.geopf.fr/geocodage", repo.config.first().banBaseUrl)
    }

    @Test fun `a device that saved its providers with the retired address follows the new default`() = runTest {
        // L'écran Fournisseurs enregistre toutes les adresses, même inchangées : l'ancien défaut se retrouve donc enregistré.
        repo.override(ProviderConfig.DEFAULT.copy(banBaseUrl = ProviderConfig.BAN_LEGACY, valhallaBaseUrl = "https://valhalla.exemple.fr"))
        val read = repo.config.first()
        assertEquals(ProviderConfig.BAN_DEFAULT, read.banBaseUrl)
        assertEquals("les autres adresses choisies sont gardées", "https://valhalla.exemple.fr", read.valhallaBaseUrl)
    }

    @Test fun `an address chosen by the user is kept`() = runTest {
        repo.override(ProviderConfig.DEFAULT.copy(banBaseUrl = "https://ban.exemple.fr"))
        assertEquals("https://ban.exemple.fr", repo.config.first().banBaseUrl)
    }

    @Test fun `reset returns to the defaults`() = runTest {
        repo.override(ProviderConfig.DEFAULT.copy(banBaseUrl = "https://ban.exemple.fr"))
        repo.reset()
        assertEquals(ProviderConfig.DEFAULT, repo.config.first())
    }
}
