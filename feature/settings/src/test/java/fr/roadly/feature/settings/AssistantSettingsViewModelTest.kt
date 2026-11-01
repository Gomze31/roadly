package fr.roadly.feature.settings

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantKeyVerifier
import fr.roadly.core.assistant.settings.KeySource
import fr.roadly.core.assistant.settings.SecretCipher
import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import fr.roadly.core.assistant.testing.FakeVoiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantSettingsViewModelTest {
    private val main = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()
    private val identity = object : SecretCipher {
        override fun encrypt(plain: String) = plain
        override fun decrypt(cipherText: String) = cipherText
    }

    @Test fun `saving a key switches the source to override and verifies it`() = runTest(main) {
        val repo = FakeAssistantSettingsRepository()
        val provider = FakeVoiceProvider(ArrayDeque(listOf(Connection.Refused(AssistantError.INVALID_KEY))))
        val vm = AssistantSettingsViewModel(repo, ApiKeyProvider(repo, identity, ""), AssistantKeyVerifier(provider))
        runCurrent()
        assertEquals(KeySource.NONE, vm.source.value)
        vm.saveKey("abc"); runCurrent()
        assertEquals(KeySource.OVERRIDE, vm.source.value)
        assertEquals("abc", provider.configs.single().apiKey)
        assertEquals(KeyCheck.Failed(AssistantError.INVALID_KEY), vm.verify.value)
        vm.clearKey(); runCurrent()
        assertEquals(KeySource.NONE, vm.source.value)
    }

    @Test fun `verifying with no key at all fails without connecting`() = runTest(main) {
        val repo = FakeAssistantSettingsRepository()
        val provider = FakeVoiceProvider()
        val vm = AssistantSettingsViewModel(repo, ApiKeyProvider(repo, identity, ""), AssistantKeyVerifier(provider))
        vm.verifyNow(); runCurrent()
        assertEquals(KeyCheck.Failed(AssistantError.NO_KEY), vm.verify.value)
        assertEquals(0, provider.configs.size)
    }
}
