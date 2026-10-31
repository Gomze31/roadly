package fr.roadly.core.assistant

import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.audio.FocusChange
import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantSettings
import fr.roadly.core.assistant.settings.SecretCipher
import fr.roadly.core.assistant.settings.UsageMeter
import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import fr.roadly.core.assistant.testing.FakeAudioFocus
import fr.roadly.core.assistant.testing.FakeAudioInput
import fr.roadly.core.assistant.testing.FakeAudioOutput
import fr.roadly.core.assistant.testing.FakeVoiceProvider
import fr.roadly.core.assistant.tools.AssistantCards
import fr.roadly.core.assistant.tools.EndConversationTool
import fr.roadly.core.assistant.tools.GuidingFixture
import fr.roadly.core.assistant.tools.PendingActions
import fr.roadly.core.assistant.tools.ToolRegistry
import fr.roadly.core.navigation.testing.FakeSpeechService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantControllerTest {
    private val identity = object : SecretCipher {
        override fun encrypt(plain: String) = plain
        override fun decrypt(cipherText: String) = cipherText
    }

    private inner class Rig(
        scope: TestScope,
        buildKey: String = "key",
        settings: AssistantSettings = AssistantSettings(consentGranted = true),
        results: ArrayDeque<Connection> = ArrayDeque(),
        val output: AudioOutput = FakeAudioOutput(),
    ) {
        val f = GuidingFixture(scope)
        val provider = FakeVoiceProvider(results)
        val input = FakeAudioInput()
        val focus = FakeAudioFocus()
        val speech = FakeSpeechService()
        val repo = FakeAssistantSettingsRepository(settings)
        val pending = PendingActions()
        val registry = ToolRegistry(setOf(EndConversationTool()), pending)
        val controller = AssistantController(
            provider, registry, pending, input, output, focus, speech, ApiKeyProvider(repo, identity, buildKey), repo, UsageMeter(repo),
            f.session, f.guidance, f.places, AssistantCards(), f.clock, f.zone, scope.backgroundScope,
        )
        val voice get() = provider.sessions.last()
        val fakeOutput get() = output as FakeAudioOutput
    }

    @Test fun `tapping opens a session that streams the microphone`() = runTest {
        val r = Rig(this)
        r.controller.toggle(); runCurrent()
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
        assertEquals("key", r.provider.configs.single().apiKey)
        assertTrue(r.provider.configs.single().systemInstruction.contains("français"))
        r.input.push(byteArrayOf(1, 2))
        assertEquals(1, r.voice.audioFrames.size)
        assertTrue(r.focus.held)
        r.controller.toggle(); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.voice.closed)
        assertFalse(r.focus.held)
    }

    @Test fun `a GPS announcement pauses the assistant voice and microphone, then resumes`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0))); runCurrent()
        r.speech.setSpeaking(true); runCurrent()
        assertTrue(r.fakeOutput.paused)
        assertEquals(AssistantPhase.PAUSED_FOR_GUIDANCE, r.controller.state.value.phase)
        assertEquals(1, r.voice.streamEnds)
        r.input.push(byteArrayOf(1))
        assertTrue("le modèle n'entend pas la voix GPS", r.voice.audioFrames.isEmpty())
        r.speech.setSpeaking(false); runCurrent()
        assertTrue("reprise après 300 ms seulement", r.fakeOutput.paused)
        advanceTimeBy(301); runCurrent()
        assertFalse(r.fakeOutput.paused)
        r.input.push(byteArrayOf(1))
        assertEquals(1, r.voice.audioFrames.size)
    }

    @Test fun `barge-in flushes the assistant voice`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0)))
        r.voice.emit(AssistantEvent.Interrupted); runCurrent()
        assertEquals(1, r.fakeOutput.flushes)
    }

    @Test fun `the session closes after 15 s without speech and records usage`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        advanceTimeBy(14_000); runCurrent()
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.voice.closed)
        assertTrue(r.repo.currentUsage.micSeconds >= 15)
    }

    @Test fun `speech keeps the session open`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        advanceTimeBy(10_000); r.voice.emit(AssistantEvent.UserTranscript("combien de temps")); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
        assertEquals("combien de temps", r.controller.state.value.userText)
    }

    @Test fun `tool calls are executed and endConversation closes once the voice has played`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.ToolCalls(listOf(ToolCall("c1", EndConversationTool.NAME, JsonObject(emptyMap())))))
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0)))
        r.voice.emit(AssistantEvent.TurnComplete); runCurrent()
        assertEquals("c1", r.voice.toolResults.single().id)
        assertEquals(AssistantPhase.SPEAKING, r.controller.state.value.phase)
        r.fakeOutput.drain(); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
    }

    @Test fun `no key, daily cap or refused connection are announced in the queue, never flushing guidance`() = runTest {
        val noKey = Rig(this, buildKey = "")
        noKey.controller.start(); runCurrent()
        assertEquals(AssistantError.NO_KEY, noKey.controller.state.value.error)
        assertTrue(noKey.provider.configs.isEmpty())

        val capped = Rig(this, settings = AssistantSettings(consentGranted = true, dailyCapMinutes = 0))
        capped.controller.start(); runCurrent()
        assertEquals(AssistantError.DAILY_CAP, capped.controller.state.value.error)

        val offline = Rig(this, results = ArrayDeque(listOf(Connection.Refused(AssistantError.NETWORK))))
        offline.controller.start(); runCurrent()
        assertEquals(AssistantPhase.IDLE, offline.controller.state.value.phase)
        assertTrue(offline.speech.spoken.single().contains("guidage continue"))
        assertEquals(listOf(false), offline.speech.flushes)
    }

    @Test fun `goAway reconnects with the latest resumption handle`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.ResumptionHandle("h1"))
        r.voice.emit(AssistantEvent.GoAway(5_000)); runCurrent()
        assertEquals(2, r.provider.configs.size)
        assertEquals("h1", r.provider.configs[1].resumptionHandle)
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
    }

    @Test fun `a lost network mid-session reconnects once, then gives up with a spoken message`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.Failed(AssistantError.NETWORK)); runCurrent()
        assertEquals(2, r.provider.sessions.size)
        r.voice.emit(AssistantEvent.Failed(AssistantError.NETWORK)); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.speech.spoken.last().contains("guidage continue"))
    }

    @Test fun `phone call focus loss pauses, regain resumes`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.focus.trigger(FocusChange.PAUSE)
        assertTrue(r.fakeOutput.paused)
        r.focus.trigger(FocusChange.RESUME)
        assertFalse(r.fakeOutput.paused)
        r.focus.trigger(FocusChange.LOST); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
    }

    @Test fun `an unexpected failure ends the session with a spoken message instead of crashing`() = runTest {
        val broken = object : AudioOutput {
            override val level: StateFlow<Float> = MutableStateFlow(0f)
            override val playing = MutableStateFlow(false)
            override fun start() = Unit
            override fun enqueue(pcm: ByteArray) = throw IllegalStateException("AudioTrack mort")
            override fun pause() = Unit
            override fun resume() = Unit
            override fun flush() = Unit
            override fun stop() = Unit
        }
        val r = Rig(this, output = broken)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0))); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.voice.closed)
        assertTrue(r.speech.spoken.last().contains("guidage continue"))
        assertEquals(false, r.speech.flushes.last())
    }
}
