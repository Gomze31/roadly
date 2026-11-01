package fr.roadly.core.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinateur vocal (E1, `NavigationVoiceCoordinator` de la spécification). Un seul moteur `TextToSpeech` (`Locale.FRANCE`) pour deux canaux :
 * - le guidage ;
 * - l'assistant.
 *
 * Les règles sont dans [SpeechQueue] / [VoiceArbiter] (testées sur JVM) : urgence GPS > guidage > assistant, assistant repris 300 ms après le
 * guidage, péremption à 8 s.
 *
 * Audio :
 * - focus transitoire avec atténuation des médias (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`) ;
 * - le focus est rendu sur le fil principal quand plus rien n'est en cours ni en file, quel que soit le canal.
 *
 * Moteur :
 * - une seule annonce confiée au moteur à la fois ;
 * - tous les `tts.speak` passent par le fil principal, dans l'ordre de réservation, et un démarrage d'une époque périmée est jeté ;
 * - délai de garde par énoncé ([GuidanceConstants.SPEECH_UTTERANCE_TIMEOUT_MS]) ;
 * - [release] est différée tant que l'assistant parle.
 *
 * Traces `RoadlySpeech` (« Dit », « Coupé », « Fini ») : vérification sur appareil.
 */
@Singleton
class AndroidSpeechService @Inject constructor(@param:ApplicationContext private val ctx: Context) : SpeechService {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    /** Annonces en attente, par canal (moteur pas prêt, occupé, ou assistant dans son délai de reprise). */
    private val queue = SpeechQueue()
    /** Énoncé confié au moteur (un seul) et son annonce ; le focus audio est tenu tant qu'il existe ou que la file n'est pas vide. */
    private var activeId: String? = null
    private var activeItem: SpeechQueue.Item? = null
    /** Délais de garde armés, par énoncé (sous [lock]) ; annulés au rappel de fin réel ou à la coupure. */
    private val timeouts = mutableMapOf<String, Runnable>()
    /** Incrémenté à chaque coupure sous [lock] : un démarrage réservé à une époque antérieure est périmé. */
    private var interruptEpoch = 0L
    /** Canaux en sourdine (sous [lock]) : la sourdine de la session ne touche que le guidage. */
    private val mutedChannels = mutableSetOf<VoiceChannel>()
    /** Libération demandée pendant un message de l'assistant (sous [lock]) : faite quand il se tait, au plus tard après 10 s. */
    private var releasePending = false
    private val ids = AtomicLong(0)
    private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var unavailable = false
    @Volatile private var volume = 1f
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    /** Relance programmée : fin du délai de reprise avant un message de l'assistant. */
    private val drainTask = Runnable { drain() }
    /** Libération différée forcée : l'assistant n'a pas fini en [VoiceArbiter.RELEASE_DEFER_MAX_MS]. */
    private val forcedRelease = Runnable { releaseNow() }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL, VoiceChannel.GUIDANCE)

    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) = speak(text, flush, priority, VoiceChannel.GUIDANCE)

    override fun speak(text: String, flush: Boolean, priority: SpeechPriority, channel: VoiceChannel) {
        if (unavailable || isSilenced(channel) || text.isBlank()) return
        val tts = ensureEngine() ?: return
        var cut = false
        val start = synchronized(lock) {
            // Le guidage reprend la parole : une libération différée (fin de course pendant un message de l'assistant) est annulée.
            if (channel == VoiceChannel.GUIDANCE && releasePending) { releasePending = false; main.removeCallbacks(forcedRelease) }
            val now = SystemClock.elapsedRealtime()
            cut = queue.offer(SpeechQueue.Item(text, priority, now, channel), flush, activeItem)
            if (cut) {
                interruptEpoch++
                activeItem?.let { Log.d(TAG, "Coupé : ${it.channel} ${it.priority}") }
                dropActive()
            }
            refreshSpeaking()
            // Moteur pas prêt : onInit videra la file ; moteur occupé sans coupure : finished() prendra la suite.
            if (!ready || activeId != null) null else nextStart(now, flush = cut)
        }
        when {
            start != null -> say(tts, start)
            // Coupure sans relève immédiate (assistant dans son délai de reprise) : l'énoncé coupé s'arrête quand même.
            cut -> tts.stop()
        }
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, 100) / 100f
        if (volume == 0f) stop()
    }

    override fun setMuted(muted: Boolean) {
        synchronized(lock) { if (muted) mutedChannels += VoiceChannel.entries else mutedChannels.clear() }
        if (muted) stop()
    }

    override fun setMuted(muted: Boolean, channel: VoiceChannel) {
        synchronized(lock) { if (muted) mutedChannels += channel else mutedChannels -= channel }
        if (muted) stop(channel)
    }

    /** Volume à zéro ou [channel] en sourdine : rien n'est mis en file ni prononcé sur ce canal, et le focus n'est pas demandé. */
    private fun isSilenced(channel: VoiceChannel): Boolean = volume == 0f || synchronized(lock) { channel in mutedChannels }

    override fun beep() {
        if (isSilenced(VoiceChannel.GUIDANCE)) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            main.postDelayed({ tone.release() }, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bip indisponible", e)
        }
    }

    override fun stop() = stop(null)

    override fun stop(channel: VoiceChannel?) {
        val tts = synchronized(lock) {
            queue.clear(channel)
            val current = activeItem
            val cut = current != null && (channel == null || current.channel == channel)
            if (cut) {
                queue.onFinished(current!!.channel, SystemClock.elapsedRealtime())
                interruptEpoch++
                dropActive()
            }
            if (channel == null) main.removeCallbacks(drainTask)
            refreshSpeaking()
            engine.takeIf { cut }
        }
        tts?.stop()
        // Un message de l'autre canal attendait : il repart (relance) ; sinon le focus est rendu, sur le fil principal.
        main.post { drain() }
    }

    override fun release() {
        val defer = synchronized(lock) { queue.hasAssistantWork(activeItem).also { releasePending = it } }
        if (!defer) { releaseNow(); return }
        // Fin de course pendant un message de l'assistant : le guidage se tait, le message finit, le moteur est libéré ensuite.
        stop(VoiceChannel.GUIDANCE)
        main.removeCallbacks(forcedRelease)
        main.postDelayed(forcedRelease, VoiceArbiter.RELEASE_DEFER_MAX_MS)
    }

    private fun releaseNow() {
        main.removeCallbacks(forcedRelease)
        stop()
        val tts = synchronized(lock) {
            releasePending = false
            ready = false
            unavailable = false
            engine.also { engine = null }
        }
        tts?.shutdown()
    }

    /** Crée le moteur au premier usage ; `null` si l'initialisation a déjà échoué de façon synchrone. */
    private fun ensureEngine(): TextToSpeech? = synchronized(lock) {
        engine ?: TextToSpeech(ctx) { status -> onInit(status) }.let {
            if (unavailable) {
                it.shutdown()
                null
            } else {
                it.setAudioAttributes(attributes)
                it.setOnUtteranceProgressListener(listener)
                engine = it
                it
            }
        }
    }

    private fun onInit(status: Int) {
        // Certains moteurs rappellent onInit(ERROR) de façon synchrone dans le constructeur (engine encore null).
        val tts = synchronized(lock) { engine }
        val lang = if (status == TextToSpeech.SUCCESS && tts != null) tts.setLanguage(Locale.FRANCE) else TextToSpeech.LANG_NOT_SUPPORTED
        if (tts == null || lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Voix française indisponible (statut $status, langue $lang)")
            unavailable = true
            synchronized(lock) {
                queue.clear()
                engine = null
                refreshSpeaking()
            }
            tts?.shutdown()
            return
        }
        // Annonces mises en file avant que le moteur soit prêt : la première encore valable part, les suivantes via finished().
        val first = synchronized(lock) { ready = true; if (activeId == null) nextStart(SystemClock.elapsedRealtime(), flush = false) else null }
        first?.let { say(tts, it) }
    }

    private class Start(val text: String, val flush: Boolean, val id: String, val epoch: Long, val channel: VoiceChannel, val priority: SpeechPriority)

    /**
     * Sous [lock] : réserve la prochaine annonce valable (guidage d'abord) dans [activeId] / [activeItem] ; null si rien à dire maintenant.
     * Un message de l'assistant dans son délai de reprise programme une relance ([drainTask]).
     */
    private fun nextStart(nowMs: Long, flush: Boolean): Start? {
        main.removeCallbacks(drainTask)
        val item = queue.poll(nowMs)
        if (item == null) {
            queue.readyAtMs(nowMs)?.let { at -> main.postDelayed(drainTask, (at - nowMs).coerceAtLeast(0L)) }
            refreshSpeaking()
            return null
        }
        val id = "roadly-${ids.incrementAndGet()}"
        activeId = id
        activeItem = item
        refreshSpeaking()
        return Start(item.text, flush, id, interruptEpoch, item.channel, item.priority)
    }

    /** Sous [lock] : oublie l'énoncé en cours (coupé) ; ses rappels tardifs seront des identifiants inconnus, ignorés. */
    private fun dropActive() {
        activeId?.let(::cancelTimeout)
        activeId = null
        activeItem = null
    }

    /** Sous [lock]. */
    private fun refreshSpeaking() {
        _speaking.value = activeId != null || !queue.isEmpty
    }

    /** Confie [s] au moteur sur le fil principal : les `tts.speak` partent dans l'ordre de réservation, un URGENT ne peut être doublé. */
    private fun say(tts: TextToSpeech, s: Start) {
        main.post { sayOnMain(tts, s) }
    }

    private fun sayOnMain(tts: TextToSpeech, s: Start) {
        val current = synchronized(lock) {
            // Époque périmée (une coupure est passée depuis la réservation) ou énoncé déjà purgé (stop) : rien à dire.
            (s.epoch == interruptEpoch && s.id == activeId).also { if (it) armTimeout(s) }
        }
        if (!current || isSilenced(s.channel)) { finished(s.id); return }
        // Redemandé à chaque énoncé (sans effet s'il est déjà tenu) : le démarrage qui l'aurait demandé a pu être jeté comme périmé.
        audio.requestAudioFocus(focusRequest)
        Log.d(TAG, "Dit : ${s.channel} ${s.priority}")
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (tts.speak(s.text, if (s.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, s.id) != TextToSpeech.SUCCESS) finished(s.id)
    }

    /** Délai de garde de [s] : 3 s + 100 ms par caractère, puis l'énoncé est réputé fini (appelé sous [lock]). */
    private fun armTimeout(s: Start) {
        val r = Runnable { finished(s.id) }
        timeouts[s.id] = r
        main.postDelayed(r, GuidanceConstants.SPEECH_UTTERANCE_TIMEOUT_MS + s.text.length * GuidanceConstants.SPEECH_MS_PER_CHAR)
    }

    /** Appelé sous [lock]. */
    private fun cancelTimeout(id: String) {
        timeouts.remove(id)?.let { main.removeCallbacks(it) }
    }

    /**
     * Fin d'un énoncé (terminé, en erreur, interrompu ou délai de garde écoulé) : enchaîne sur la prochaine annonce encore valable, sinon
     * [settle]. Idempotent : un identifiant inconnu (déjà fini, purgé par [stop] ou retiré par une coupure) est ignoré.
     */
    private fun finished(utteranceId: String?) {
        val (tts, next) = synchronized(lock) {
            if (utteranceId == null) return
            cancelTimeout(utteranceId)
            if (utteranceId != activeId) return
            val now = SystemClock.elapsedRealtime()
            activeItem?.let { queue.onFinished(it.channel, now); Log.d(TAG, "Fini : ${it.channel}") }
            activeId = null
            activeItem = null
            engine to (if (ready) nextStart(now, flush = false) else null)
        }
        if (tts != null && next != null) say(tts, next) else settle()
    }

    /**
     * Relance programmée ou après un arrêt, sur le fil principal : la prochaine annonce part, sinon [settle]. Moteur pas prêt (en création,
     * ou libéré juste après un arrêt) : rien ne part, mais le focus est quand même rendu si plus rien n'attend.
     */
    private fun drain() {
        val (tts, next) = synchronized(lock) {
            if (activeId != null) return
            engine to (if (ready) nextStart(SystemClock.elapsedRealtime(), flush = false) else null)
        }
        if (tts != null && next != null) say(tts, next) else settle()
    }

    /**
     * Sur le fil principal (défaut relevé à l'étape C : le focus était rendu depuis le fil du moteur) :
     * - plus rien en cours ni en file → focus rendu, la musique revient ;
     * - une libération différée attend et l'assistant s'est tu → elle est faite.
     */
    private fun settle() {
        main.post {
            val (idle, release) = synchronized(lock) {
                (activeId == null && queue.isEmpty) to (releasePending && !queue.hasAssistantWork(activeItem))
            }
            if (idle) audio.abandonAudioFocusRequest(focusRequest)
            if (release) releaseNow()
        }
    }

    private companion object { const val TAG = "RoadlySpeech" }
}
