package fr.roadly.core.assistant

import fr.roadly.core.assistant.audio.AudioFocus
import fr.roadly.core.assistant.audio.AudioInput
import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.audio.FocusChange
import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.provider.VoiceSession
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.UsageMeter
import fr.roadly.core.assistant.tools.AssistantCards
import fr.roadly.core.assistant.tools.EndConversationTool
import fr.roadly.core.assistant.tools.PendingActions
import fr.roadly.core.assistant.tools.ToolContext
import fr.roadly.core.assistant.tools.ToolPlaces
import fr.roadly.core.assistant.tools.ToolRegistry
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.SpeechService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class AssistantPhase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, PAUSED_FOR_GUIDANCE }

data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val userText: String = "",
    val assistantText: String = "",
    /** Résumé de l'action en attente de confirmation. */
    val pending: String? = null,
    /** Message d'erreur ou d'information (déjà annoncé à voix haute). */
    val message: String? = null,
    val error: AssistantError? = null,
)

/**
 * Session Roadly AI : connexion, micro, voix, outils, auto-fermeture (15 s), reprise de contexte (2 h).
 * Le guidage est prioritaire : pendant une annonce GPS, voix et micro de l'assistant sont suspendus.
 * Aucune erreur de l'assistant ne remonte au processus : elle ferme la session avec un message annoncé.
 */
@Singleton
class AssistantController @Inject constructor(
    private val provider: VoiceAssistantProvider,
    private val registry: ToolRegistry,
    private val pending: PendingActions,
    private val input: AudioInput,
    private val output: AudioOutput,
    private val focus: AudioFocus,
    private val speech: SpeechService,
    private val keys: ApiKeyProvider,
    private val settings: AssistantSettingsRepository,
    private val usage: UsageMeter,
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val places: ToolPlaces,
    private val cards: AssistantCards,
    private val clock: NavigationClock,
    private val zone: ZoneId,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = combine(_state, pending.current) { s, p -> s.copy(pending = p?.summary) }
        .stateIn(scope, SharingStarted.Eagerly, AssistantUiState())
    val micLevel: StateFlow<Float> get() = input.level
    val voiceLevel: StateFlow<Float> get() = output.level

    private var job: Job? = null
    @Volatile private var voice: VoiceSession? = null
    private var resumeHandle: String? = null
    private var resumeHandleAtMs = 0L
    private val heard = mutableListOf<Pair<Long, String>>()
    @Volatile private var lastActivityMs = 0L
    /** -1 = aucune session ouverte (le temps virtuel des tests commence à 0). */
    private var startedAtMs = -1L
    private var tokens = 0L
    @Volatile private var gpsPaused = false
    @Volatile private var focusPaused = false
    private var closeAfterTurn = false
    private var newUserTurn = true
    private var newAssistantTurn = true

    private sealed interface End {
        data object Closed : End
        data class Reconnect(val error: AssistantError?) : End
        data class Fatal(val error: AssistantError) : End
    }

    /** Bouton micro : ouvre une session, ou ferme celle en cours. */
    fun toggle() { if (job?.isActive == true) stop() else start() }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    fun stop() { job?.cancel() }

    fun onAppBackground() = stop()

    fun confirmFromUi() {
        scope.launch {
            runCatching { pending.confirmFromUi() }.getOrNull()?.let { voice?.sendText("[Action confirmée à l'écran par l'utilisateur] $it") }
        }
    }

    fun cancelFromUi() {
        if (pending.cancelFromUi()) voice?.sendText("[Action annulée à l'écran par l'utilisateur]")
    }

    /** Tap sur une ligne de la carte de résultat : ajout direct de l'arrêt (geste explicite). */
    fun addStopFromCard(placeId: String) {
        val place = places[placeId] ?: return
        scope.launch {
            val note = when (runCatching { guidance.addStop(place) }.getOrNull()) {
                is Outcome.Success -> "[Arrêt ajouté à l'écran : ${place.name}]"
                else -> "[Ajout de l'arrêt ${place.name} impossible]"
            }
            cards.clear()
            voice?.sendText(note)
        }
    }

    private suspend fun run() {
        val s = settings.settings.first()
        val key = keys.key()
        val refusal = when {
            !s.enabled -> AssistantError.DISABLED
            key == null -> AssistantError.NO_KEY
            !usage.canStart(clock.nowMs(), zone) -> AssistantError.DAILY_CAP
            else -> null
        }
        if (refusal != null || key == null) return fail(refusal ?: AssistantError.NO_KEY)
        resetConversation()
        _state.value = AssistantUiState(phase = AssistantPhase.CONNECTING)
        try {
            var current = connect(key) ?: return
            if (!input.start(::onMicFrame)) {
                current.close()
                return fail(AssistantError.MIC_UNAVAILABLE)
            }
            focus.acquire(::onFocusChange)
            output.start()
            coroutineScope {
                launch { followGuidanceSpeech() }
                launch { closeWhenIdle() }
                var reconnects = 0
                while (true) {
                    voice = current
                    if (!gpsPaused) setPhase(AssistantPhase.LISTENING)
                    val end = consume(current, this)
                    if (end !is End.Reconnect) {
                        if (end is End.Fatal) announce(end.error)
                        break
                    }
                    current.close()
                    if (reconnects++ >= MAX_RECONNECTS) { announce(end.error ?: AssistantError.NETWORK); break }
                    current = connect(key) ?: break
                }
                coroutineContext.cancelChildren()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Défaillance inattendue (audio, outil, protocole) : la session se ferme, le guidage n'est jamais touché.
            announce(AssistantError.PROTOCOL)
        } finally {
            withContext(NonCancellable) { teardown() }
        }
    }

    private suspend fun connect(key: String): VoiceSession? {
        val handle = resumeHandle?.takeIf { clock.nowMs() - resumeHandleAtMs < RESUME_WINDOW_MS }
        val config = SessionConfig(key, SystemPrompt.build(session.state.value is NavigationState.Guiding), registry.schemas(), VOICE, handle)
        return when (val c = provider.connect(config)) {
            is Connection.Open -> c.session
            is Connection.Refused -> { announce(c.error); null }
        }
    }

    private suspend fun consume(s: VoiceSession, sessionScope: CoroutineScope): End {
        var end: End = End.Closed
        s.events.takeWhile { e ->
            when (e) {
                is AssistantEvent.GoAway -> { end = End.Reconnect(null); false }
                is AssistantEvent.Failed -> {
                    end = if (e.error == AssistantError.NETWORK || e.error == AssistantError.TIMEOUT) End.Reconnect(e.error) else End.Fatal(e.error)
                    false
                }
                AssistantEvent.Closed -> false
                else -> { onEvent(s, e, sessionScope); true }
            }
        }.collect()
        return end
    }

    private suspend fun onEvent(s: VoiceSession, e: AssistantEvent, sessionScope: CoroutineScope) {
        val now = clock.nowMs()
        when (e) {
            is AssistantEvent.UserTranscript -> {
                lastActivityMs = now
                heard += now to e.text
                val base = if (newUserTurn) "" else _state.value.userText
                newUserTurn = false
                _state.update { it.copy(userText = (base + e.text).trim(), message = null, error = null) }
            }
            is AssistantEvent.AssistantTranscript -> {
                val base = if (newAssistantTurn) "" else _state.value.assistantText
                newAssistantTurn = false
                _state.update { it.copy(assistantText = (base + e.text).trim()) }
            }
            is AssistantEvent.AudioChunk -> {
                lastActivityMs = now
                output.enqueue(e.pcm24k)
                if (!gpsPaused) setPhase(AssistantPhase.SPEAKING)
            }
            is AssistantEvent.ToolCalls -> {
                lastActivityMs = now
                if (!gpsPaused) setPhase(AssistantPhase.THINKING)
                val ctx = toolContext()
                s.sendToolResults(e.calls.map { registry.execute(it, ctx) })
                if (e.calls.any { it.name == EndConversationTool.NAME }) closeAfterTurn = true
                lastActivityMs = clock.nowMs()
            }
            is AssistantEvent.ToolCallsCancelled -> pending.cancelCreatedBy(e.ids)
            AssistantEvent.Interrupted -> output.flush()
            AssistantEvent.TurnComplete -> {
                registry.resetTurn()
                newUserTurn = true
                newAssistantTurn = true
                sessionScope.launch {
                    output.playing.first { !it }
                    lastActivityMs = clock.nowMs()
                    if (closeAfterTurn) stop() else if (!gpsPaused) setPhase(AssistantPhase.LISTENING)
                }
            }
            is AssistantEvent.ResumptionHandle -> { resumeHandle = e.handle; resumeHandleAtMs = now }
            is AssistantEvent.Usage -> tokens = maxOf(tokens, e.totalTokens.toLong())
            else -> Unit
        }
    }

    private suspend fun toolContext(): ToolContext {
        val consent = settings.settings.first().consentGranted
        val snapshot = heard.toList()
        return ToolContext(consent, clock.nowMs(), zone) { since -> snapshot.filter { it.first >= since }.joinToString(" ") { it.second } }
    }

    private fun onMicFrame(frame: ByteArray) { if (!gpsPaused && !focusPaused) voice?.sendAudio(frame) }

    /** Priorité absolue au guidage : voix et micro suspendus pendant l'annonce, reprise 300 ms après. */
    private suspend fun followGuidanceSpeech() {
        speech.speaking.collectLatest { speaking ->
            if (speaking) {
                if (!gpsPaused) {
                    gpsPaused = true
                    output.pause()
                    voice?.endAudioStream()
                    setPhase(AssistantPhase.PAUSED_FOR_GUIDANCE)
                }
            } else if (gpsPaused) {
                delay(RESUME_AFTER_GUIDANCE_MS)
                gpsPaused = false
                if (!focusPaused) output.resume()
                lastActivityMs = clock.nowMs()
                setPhase(if (output.playing.value) AssistantPhase.SPEAKING else AssistantPhase.LISTENING)
            }
        }
    }

    /** Appelé par le système (fil principal) : jamais d'exception vers l'appelant. */
    private fun onFocusChange(change: FocusChange) {
        runCatching {
            when (change) {
                FocusChange.LOST -> stop()
                FocusChange.PAUSE -> { focusPaused = true; output.pause(); voice?.endAudioStream() }
                FocusChange.RESUME -> if (focusPaused) {
                    focusPaused = false
                    if (!gpsPaused) output.resume()
                    lastActivityMs = clock.nowMs()
                }
            }
        }.onFailure { stop() }
    }

    private suspend fun closeWhenIdle() {
        while (true) {
            delay(1_000)
            val idle = clock.nowMs() - lastActivityMs
            if (_state.value.phase == AssistantPhase.LISTENING && !output.playing.value && idle >= IDLE_TIMEOUT_MS) { stop(); return }
        }
    }

    private fun setPhase(phase: AssistantPhase) = _state.update { it.copy(phase = phase) }

    private fun announce(error: AssistantError) {
        val text = AssistantMessages.of(error)
        // En file, jamais `flush` : une instruction de guidage en cours n'est pas coupée.
        speech.speak(text, flush = false)
        _state.update { it.copy(message = text, error = error) }
    }

    private fun fail(error: AssistantError) {
        announce(error)
        setPhase(AssistantPhase.IDLE)
    }

    private fun resetConversation() {
        heard.clear()
        tokens = 0
        closeAfterTurn = false
        newUserTurn = true
        newAssistantTurn = true
        startedAtMs = clock.nowMs()
        lastActivityMs = startedAtMs
        cards.clear()
    }

    private suspend fun teardown() {
        runCatching { input.stop() }
        runCatching { output.stop() }
        runCatching { focus.release() }
        runCatching { voice?.close() }
        voice = null
        pending.clear()
        gpsPaused = false
        focusPaused = false
        if (startedAtMs >= 0) runCatching { usage.record(clock.nowMs(), zone, tokens, (clock.nowMs() - startedAtMs) / 1000) }
        startedAtMs = -1L
        setPhase(AssistantPhase.IDLE)
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 15_000L
        const val RESUME_AFTER_GUIDANCE_MS = 300L
        const val RESUME_WINDOW_MS = 2 * 60 * 60 * 1000L
        const val MAX_RECONNECTS = 1
        const val VOICE = "Kore"
    }
}
