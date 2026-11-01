package fr.roadly.core.assistant.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** `VOICE_COMMUNICATION` : traitement voix du constructeur ; annulation d'écho et réduction de bruit si disponibles. */
@Singleton
class AndroidMicCapture @Inject constructor(@param:ApplicationContext private val ctx: Context) : AudioInput {
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    /** Dernière session de capture ; le micro n'est tenu que tant que sa boucle tourne. */
    private var current: Capture? = null

    @Synchronized
    @SuppressLint("MissingPermission")
    override fun start(onFrame: (ByteArray) -> Unit): Boolean {
        current?.let { old ->
            if (old.running) return true
            // Session précédente encore en cours de libération : on attend sa fin avant d'ouvrir un nouveau micro.
            if (!old.awaitEnd(JOIN_MS)) return false
            current = null
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME_BYTES * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return false }
        val effects = mutableListOf<AudioEffect>()
        // Micro occupé : startRecording peut lever ou laisser l'enregistrement arrêté ; rien n'est alors tenu.
        val recording = runCatching {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.let { effects += it.apply { enabled = true } }
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.let { effects += it.apply { enabled = true } }
            record.startRecording()
            record.recordingState == AudioRecord.RECORDSTATE_RECORDING
        }.getOrDefault(false)
        if (!recording) {
            effects.forEach { runCatching { it.release() } }
            runCatching { record.stop() }
            record.release()
            return false
        }
        val capture = Capture(record, effects, onFrame)
        current = capture
        capture.start()
        return true
    }

    /** Arrête la capture et attend (borné) que le micro soit rendu. */
    @Synchronized
    override fun stop() {
        val c = current ?: return
        c.requestStop()
        if (c.awaitEnd(JOIN_MS)) current = null
    }

    /** Une session de capture : son propre drapeau et son propre thread, libérés dans un `finally`. */
    private inner class Capture(
        private val record: AudioRecord,
        private val effects: List<AudioEffect>,
        private val onFrame: (ByteArray) -> Unit,
    ) {
        @Volatile var running = true
            private set
        private val thread = Thread({ loop() }, "roadly-ai-mic")

        fun start() = thread.start()

        fun requestStop() { running = false }

        fun awaitEnd(ms: Long): Boolean {
            thread.join(ms)
            return !thread.isAlive
        }

        private fun loop() {
            val buf = ByteArray(FRAME_BYTES)
            try {
                while (running) {
                    val n = record.read(buf, 0, buf.size)
                    // Erreur (ex. ERROR_DEAD_OBJECT) : on sort plutôt que de tourner à vide.
                    if (n < 0) break
                    if (n > 0) { _level.value = Pcm.rms(buf, n); onFrame(buf.copyOf(n)) }
                }
            } catch (e: Exception) {
                // Une erreur de l'assistant ne doit jamais faire tomber le process (le GPS doit rester utilisable).
                Log.w(LOG_TAG, "Capture interrompue", e)
            } finally {
                running = false
                runCatching { record.stop() }
                effects.forEach { runCatching { it.release() } }
                record.release()
                _level.value = 0f
            }
        }
    }

    private companion object {
        const val RATE = 16_000
        /** 40 ms de PCM16 mono. */
        const val FRAME_BYTES = RATE * 2 * 40 / 1000
        /** Attente maximale de la libération du micro lors d'un arrêt ou d'un redémarrage. */
        const val JOIN_MS = 500L
        const val LOG_TAG = "RoadlyAI"
    }
}
