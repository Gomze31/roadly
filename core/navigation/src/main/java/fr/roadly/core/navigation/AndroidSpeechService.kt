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
 * Tous les appels `tts.speak` passent par le fil principal ([main]), dans l'ordre de leur réservation : un enchaînement réservé avant
 * une interruption (époque [interruptEpoch] périmée) est jeté au lieu d'être dit après le NOW. Chaque énoncé a un délai de garde
 * ([GuidanceConstants.SPEECH_UTTERANCE_TIMEOUT_MS]) : un rappel de fin perdu ne bloque ni la file ni le focus audio.
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
    /** Énoncés confiés au moteur (un seul : une interruption retire les énoncés coupés) ; le focus audio est tenu tant qu'il n'est pas vide. */
    private val active = mutableSetOf<String>()
    /** Délais de garde armés, par énoncé (sous [lock]) ; annulés au rappel de fin réel. */
    private val timeouts = mutableMapOf<String, Runnable>()
    /** Incrémenté à chaque interruption (URGENT ou flush) sous [lock] : un démarrage réservé à une époque antérieure est périmé. */
    private var interruptEpoch = 0L
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
            if (interrupt) interruptEpoch++
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
            cancelTimeouts()
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

    private class Start(val text: String, val flush: Boolean, val id: String, val epoch: Long)

    /**
     * Tête de file encore valable, identifiant réservé dans [active] (appelé sous [lock]) ; null si rien à dire.
     * Interruption ([flush]) : les énoncés coupés sont retirés de [active] tout de suite (QUEUE_FLUSH les arrête ; leurs rappels tardifs
     * sont alors des identifiants inconnus, ignorés) — la file ne dépend plus d'un `onStop` qui pourrait ne jamais venir.
     */
    private fun nextStart(nowMs: Long, flush: Boolean): Start? {
        val item = queue.poll(nowMs) ?: return null
        val id = "roadly-${ids.incrementAndGet()}"
        if (flush) {
            active.forEach(::cancelTimeout)
            active.clear()
        }
        active += id
        return Start(item.text, flush, id, interruptEpoch)
    }

    /** Confie [s] au moteur sur le fil principal : les `tts.speak` partent dans l'ordre de réservation, un URGENT ne peut être doublé. */
    private fun say(tts: TextToSpeech, s: Start) {
        main.post { sayOnMain(tts, s) }
    }

    private fun sayOnMain(tts: TextToSpeech, s: Start) {
        val current = synchronized(lock) {
            // Époque périmée (un URGENT est passé depuis la réservation) ou énoncé déjà purgé (stop, interruption) : rien à dire.
            (s.epoch == interruptEpoch && s.id in active).also { if (it) armTimeout(s) }
        }
        if (!current || isSilenced()) { finished(s.id); return }
        // Redemandé à chaque énoncé (sans effet s'il est déjà tenu) : le démarrage qui l'aurait demandé a pu être jeté comme périmé.
        audio.requestAudioFocus(focusRequest)
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

    /** Appelé sous [lock]. */
    private fun cancelTimeouts() {
        timeouts.values.forEach { main.removeCallbacks(it) }
        timeouts.clear()
    }

    /**
     * Fin d'un énoncé (terminé, en erreur, interrompu ou délai de garde écoulé) : enchaîne sur la prochaine annonce encore valable, sinon
     * rend le focus. Idempotent : un identifiant inconnu (déjà fini, purgé par [stop] ou retiré par une interruption) est ignoré.
     */
    private fun finished(utteranceId: String?) {
        val (tts, next) = synchronized(lock) {
            if (utteranceId == null) return
            cancelTimeout(utteranceId)
            if (!active.remove(utteranceId) || active.isNotEmpty()) return
            engine to (if (ready) nextStart(SystemClock.elapsedRealtime(), flush = false) else null)
        }
        if (tts != null && next != null) say(tts, next) else audio.abandonAudioFocusRequest(focusRequest)
    }

    private companion object { const val TAG = "RoadlySpeech" }
}
