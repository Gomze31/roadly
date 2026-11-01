package fr.roadly.core.navigation

import fr.roadly.core.navigation.testing.FakeSpeechService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSpeechServiceTest {
    @Test fun `speaking is observable and driven by the test`() {
        val s = FakeSpeechService()
        assertFalse(s.speaking.value)
        s.setSpeaking(true)
        assertTrue(s.speaking.value)
        s.stop()
        assertFalse("stop() coupe la parole en cours", s.speaking.value)
    }
}
