package fr.roadly.core.assistant.settings

import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyProviderTest {
    private val reversing = object : SecretCipher {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(cipherText: String) = cipherText.reversed()
    }

    @Test fun `the key typed in settings wins over the build key, stored encrypted`() = runTest {
        val repo = FakeAssistantSettingsRepository()
        val keys = ApiKeyProvider(repo, reversing, "build-key")
        assertEquals("build-key", keys.key()); assertEquals(KeySource.BUILD, keys.source())
        keys.save("  perso  ")
        assertEquals("osrep", repo.current.encryptedKey)
        assertEquals("perso", keys.key()); assertEquals(KeySource.OVERRIDE, keys.source())
        keys.clearOverride()
        assertEquals("build-key", keys.key())
    }

    @Test fun `no key anywhere is null`() = runTest {
        val keys = ApiKeyProvider(FakeAssistantSettingsRepository(), reversing, "")
        assertNull(keys.key()); assertEquals(KeySource.NONE, keys.source())
    }
}
