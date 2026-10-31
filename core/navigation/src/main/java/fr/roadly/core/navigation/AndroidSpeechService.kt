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
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `TextToSpeech` en `Locale.FRANCE`, focus audio transitoire avec atténuation des autres sources
 * (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), file d'annonces priorisée (B5 : un URGENT coupe et vide la file, LOW / NORMAL
 * périment après 8 s), volume par annonce. Une seule annonce est confiée au moteur à la fois. Moteur créé au premier usage.
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
    /** Annonces en attente (moteur pas prêt ou occupé). */
    private val queue = SpeechQueue()
    /** Énoncés confiés au moteur (un seul, deux le temps d'une interruption) ; le focus audio est tenu tant qu'il n'est pas vide. */
    private val active = mutableSetOf<String>()
    private val ids = AtomicLong(0)
    private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var unavailable = false
    @Volatile private var volume = 1f
    @Volatile private var muted = false

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL)

    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) {
        if (unavailable || isSilenced() || text.isBlank()) return
        val tts = ensureEngine() ?: return
        val start = synchronized(lock) {
            val interrupt = queue.offer(SpeechQueue.Item(text, priority, SystemClock.elapsedRealtime()), flush)
            // Moteur pas prêt : onInit videra la file ; moteur occupé sans interruption : finished() prendra la suite.
            if (!ready || !(interrupt || active.isEmpty())) return
            nextStart(SystemClock.elapsedRealtime(), flush = interrupt)
        } ?: return
        say(tts, start)
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, 100) / 100f
        if (volume == 0f) stop()
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        if (muted) stop()
    }

    /** Sourdine explicite ou volume à zéro : rien n'est mis en file ni prononcé, et le focus n'est pas demandé. */
    private fun isSilenced(): Boolean = muted || volume == 0f

    override fun beep() {
        if (isSilenced()) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            main.postDelayed({ tone.release() }, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bip indisponible", e)
        }
    }

    override fun stop() {
        val tts = synchronized(lock) {
            queue.clear()
            active.clear()
            engine
        }
        tts?.stop()
        audio.abandonAudioFocusRequest(focusRequest)
    }

    override fun release() {
        stop()
        val tts = synchronized(lock) {
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
            }
            tts?.shutdown()
            return
        }
        // Annonces mises en file avant que le moteur soit prêt : la première encore valable part, les suivantes via finished().
        val first = synchronized(lock) { ready = true; if (active.isEmpty()) nextStart(SystemClock.elapsedRealtime(), flush = false) else null }
        first?.let { say(tts, it) }
    }

    private class Start(val text: String, val flush: Boolean, val id: String, val requestFocus: Boolean)

    /** Tête de file encore valable, identifiant réservé dans [active] (appelé sous [lock]) ; null si rien à dire. */
    private fun nextStart(nowMs: Long, flush: Boolean): Start? {
        val item = queue.poll(nowMs) ?: return null
        val id = "roadly-${ids.incrementAndGet()}"
        val requestFocus = active.isEmpty()
        active += id
        return Start(item.text, flush, id, requestFocus)
    }

    private fun say(tts: TextToSpeech, s: Start) {
        if (isSilenced()) { finished(s.id); return }
        if (s.requestFocus) audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (tts.speak(s.text, if (s.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, s.id) != TextToSpeech.SUCCESS) finished(s.id)
    }

    /**
     * Fin d'un énoncé (terminé, en erreur ou interrompu) : enchaîne sur la prochaine annonce encore valable, sinon rend le focus.
     * Les identifiants inconnus (déjà purgés par [stop]) sont ignorés.
     */
    private fun finished(utteranceId: String?) {
        val (tts, next) = synchronized(lock) {
            if (utteranceId == null || !active.remove(utteranceId) || active.isNotEmpty()) return
            engine to (if (ready) nextStart(SystemClock.elapsedRealtime(), flush = false) else null)
        }
        if (tts != null && next != null) say(tts, next) else audio.abandonAudioFocusRequest(focusRequest)
    }

    private companion object { const val TAG = "RoadlySpeech" }
}
