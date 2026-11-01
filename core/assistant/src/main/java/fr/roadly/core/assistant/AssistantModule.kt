package fr.roadly.core.assistant

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.assistant.audio.AndroidAssistantAudioFocus
import fr.roadly.core.assistant.audio.AndroidMicCapture
import fr.roadly.core.assistant.audio.AndroidVoicePlayback
import fr.roadly.core.assistant.audio.AudioFocus
import fr.roadly.core.assistant.audio.AudioInput
import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.gemini.GeminiLiveProvider
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.DataStoreAssistantSettingsRepository
import fr.roadly.core.assistant.settings.KeystoreSecretCipher
import fr.roadly.core.assistant.settings.SecretCipher
import java.time.ZoneId
import javax.inject.Named

/** Fournisseur IA (Gemini Live aujourd'hui ; OpenAI = une autre liaison de [VoiceAssistantProvider]), audio, réglages. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {
    @Binds abstract fun provider(impl: GeminiLiveProvider): VoiceAssistantProvider
    @Binds abstract fun input(impl: AndroidMicCapture): AudioInput
    @Binds abstract fun output(impl: AndroidVoicePlayback): AudioOutput
    @Binds abstract fun focus(impl: AndroidAssistantAudioFocus): AudioFocus
    @Binds abstract fun cipher(impl: KeystoreSecretCipher): SecretCipher
    @Binds abstract fun settings(impl: DataStoreAssistantSettingsRepository): AssistantSettingsRepository

    companion object {
        @Provides @Named(ApiKeyProvider.BUILD_KEY) fun buildKey(): String = BuildConfig.GEMINI_API_KEY
        @Provides fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
