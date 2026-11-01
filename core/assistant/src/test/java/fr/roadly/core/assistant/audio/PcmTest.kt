package fr.roadly.core.assistant.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {
    private fun pcm(vararg samples: Int) = ByteArray(samples.size * 2).also { b ->
        samples.forEachIndexed { i, s -> b[2 * i] = (s and 0xFF).toByte(); b[2 * i + 1] = (s shr 8).toByte() }
    }

    @Test fun `silence is zero and full scale is close to one`() {
        assertEquals(0f, Pcm.rms(pcm(0, 0, 0, 0), 8), 1e-6f)
        assertTrue(Pcm.rms(pcm(32767, -32768, 32767, -32768), 8) > 0.99f)
    }

    @Test fun `only the first length bytes count and odd lengths are safe`() {
        assertEquals(0f, Pcm.rms(pcm(0, 32767), 2), 1e-6f)
        assertEquals(0f, Pcm.rms(pcm(0, 32767), 1), 1e-6f)
    }
}
