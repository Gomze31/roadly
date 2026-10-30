package fr.roadly.core.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `TextToSpeech` en `Locale.FRANCE`, focus audio transitoire avec atténuation des autres sources
 * (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), file d'annonces, volume par annonce. Moteur créé au premier usage.
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
    private val pending = ArrayDeque<Pair<String, Boolean>>()
    private val inFlight = AtomicInteger(0)
    private val ids = AtomicLong(0)
    private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var unavailable = false
    @Volatile private var volume = 1f

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished()
        override fun onError(utteranceId: String?, errorCode: Int) = finished()
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
    }

    override fun speak(text: String, flush: Boolean) {
        if (unavailable || text.isBlank()) return
        val tts = ensureEngine()
        synchronized(lock) {
            if (!ready) {
                if (flush) pending.clear()
                pending.addLast(text to flush)
                return
            }
        }
        say(tts, text, flush)
    }

    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) / 100f }

    override fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            main.postDelayed({ tone.release() }, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bip indisponible", e)
        }
    }

    override fun stop() {
        synchronized(lock) { pending.clear() }
        engine?.stop()
        inFlight.set(0)
        audio.abandonAudioFocusRequest(focusRequest)
    }

    private fun ensureEngine(): TextToSpeech = synchronized(lock) {
        engine ?: TextToSpeech(ctx) { status -> onInit(status) }.also {
            it.setAudioAttributes(attributes)
            it.setOnUtteranceProgressListener(listener)
            engine = it
        }
    }

    private fun onInit(status: Int) {
        // Certains moteurs rappellent onInit(ERROR) de façon synchrone dans le constructeur (engine encore null).
        val tts = synchronized(lock) { engine }
        val lang = if (status == TextToSpeech.SUCCESS && tts != null) tts.setLanguage(Locale.FRANCE) else TextToSpeech.LANG_NOT_SUPPORTED
        if (tts == null || lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Voix française indisponible (statut $status, langue $lang)")
            unavailable = true
            synchronized(lock) { pending.clear() }
            return
        }
        val queued = synchronized(lock) { ready = true; pending.toList().also { pending.clear() } }
        queued.forEach { (text, flush) -> say(tts, text, flush) }
    }

    private fun say(tts: TextToSpeech, text: String, flush: Boolean) {
        if (inFlight.getAndIncrement() == 0) audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        val id = "roadly-${ids.incrementAndGet()}"
        if (tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) finished()
    }

    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    private companion object { const val TAG = "RoadlySpeech" }
}
