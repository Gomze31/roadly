package fr.roadly.core.assistant.audio

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/** Micro 16 kHz mono PCM16 ; [start] faux si permission absente ou micro occupé. */
interface AudioInput {
    val level: StateFlow<Float>
    fun start(onFrame: (ByteArray) -> Unit): Boolean
    fun stop()
}

/** Voix de l'assistant 24 kHz mono PCM16 ; [playing] vrai tant qu'il reste du son en file ou en cours. */
interface AudioOutput {
    val level: StateFlow<Float>
    val playing: StateFlow<Boolean>
    fun start()
    fun enqueue(pcm: ByteArray)
    /** Suspend la lecture en gardant la file (annonce GPS, appel). */
    fun pause()
    fun resume()
    /** Vide la file (interruption par l'utilisateur). */
    fun flush()
    fun stop()
}

enum class FocusChange { PAUSE, RESUME, LOST }

interface AudioFocus {
    fun acquire(onChange: (FocusChange) -> Unit): Boolean
    fun release()
}

object Pcm {
    /** Niveau RMS normalisé (0..1) des [length] premiers octets d'un tampon PCM16 little-endian. */
    fun rms(buf: ByteArray, length: Int): Float {
        val samples = length.coerceAtMost(buf.size) / 2
        if (samples == 0) return 0f
        var sum = 0.0
        for (i in 0 until samples) {
            val s = (buf[2 * i].toInt() and 0xFF) or (buf[2 * i + 1].toInt() shl 8)
            sum += s.toDouble() * s
        }
        return (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
