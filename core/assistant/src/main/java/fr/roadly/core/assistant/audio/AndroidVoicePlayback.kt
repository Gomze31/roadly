package fr.roadly.core.assistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** `AudioTrack` en flux, `USAGE_ASSISTANT` : distinct du canal de guidage, qui reste prioritaire. */
@Singleton
class AndroidVoicePlayback @Inject constructor() : AudioOutput {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()
    @Volatile private var paused = false
    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    private var thread: Thread? = null

    override fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        paused = false
        running = true
        t.play()
        thread = Thread({
            while (running) {
                if (paused) { Thread.sleep(20); continue }
                val chunk = queue.poll(50, TimeUnit.MILLISECONDS)
                if (chunk == null) { _playing.value = false; _level.value = 0f; continue }
                _playing.value = true
                _level.value = Pcm.rms(chunk, chunk.size)
                t.write(chunk, 0, chunk.size)
            }
        }, "roadly-ai-voice").also { it.start() }
    }

    override fun enqueue(pcm: ByteArray) { queue.offer(pcm); _playing.value = true }
    override fun pause() { paused = true; track?.pause() }
    override fun resume() { paused = false; track?.play() }
    override fun flush() {
        queue.clear()
        track?.let { it.pause(); it.flush(); if (!paused) it.play() }
        _playing.value = false
        _level.value = 0f
    }
    override fun stop() {
        running = false
        queue.clear()
        thread?.join(300)
        thread = null
        track?.let { it.pause(); it.flush(); it.release() }
        track = null
        _playing.value = false
        _level.value = 0f
    }

    private companion object { const val RATE = 24_000 }
}
