package fr.roadly.core.assistant.testing

import fr.roadly.core.assistant.settings.AssistantSettings
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.AssistantUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAssistantSettingsRepository(initial: AssistantSettings = AssistantSettings()) : AssistantSettingsRepository {
    private val state = MutableStateFlow(initial)
    private val usageState = MutableStateFlow(AssistantUsage())
    override val settings = state
    override val usage = usageState
    val current: AssistantSettings get() = state.value
    val currentUsage: AssistantUsage get() = usageState.value
    override suspend fun setEnabled(value: Boolean) = state.update { it.copy(enabled = value) }
    override suspend fun setConsent(value: Boolean) = state.update { it.copy(consentGranted = value) }
    override suspend fun setDailyCap(minutes: Int) = state.update { it.copy(dailyCapMinutes = minutes) }
    override suspend fun setEncryptedKey(value: String?) = state.update { it.copy(encryptedKey = value) }
    override suspend fun addUsage(day: String, tokens: Long, micSeconds: Long) = usageState.update {
        if (it.day == day) it.copy(tokens = it.tokens + tokens, micSeconds = it.micSeconds + micSeconds) else AssistantUsage(day, tokens, micSeconds)
    }
}
