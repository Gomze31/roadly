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
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    override fun start(onFrame: (ByteArray) -> Unit): Boolean {
        if (running) return true
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME_BYTES * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return false }
        val effects = listOfNotNull<AudioEffect>(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null,
        )
        running = true
        record.startRecording()
        Thread({
            val buf = ByteArray(FRAME_BYTES)
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) { _level.value = Pcm.rms(buf, n); onFrame(buf.copyOf(n)) }
            }
            record.stop()
            effects.forEach { it.release() }
            record.release()
            _level.value = 0f
        }, "roadly-ai-mic").start()
        return true
    }

    override fun stop() { running = false }

    private companion object {
        const val RATE = 16_000
        /** 40 ms de PCM16 mono. */
        const val FRAME_BYTES = RATE * 2 * 40 / 1000
    }
}
