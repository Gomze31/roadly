package fr.roadly.core.navigation

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechFocusTest {
    @Test fun `normal audio mode lets guidance speak`() {
        assertFalse(SpeechFocus.inCall(AudioManager.MODE_NORMAL))
    }

    @Test fun `ringing, telephony call and voice-over-IP call silence guidance`() {
        for (mode in listOf(AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_CALL_SCREENING)) {
            assertTrue("mode $mode", SpeechFocus.inCall(mode))
        }
    }

    @Test fun `the hard-coded values are the platform constants`() {
        // Les constantes sont recopiées pour tester la règle sans Android : ce test échoue si elles divergent de l'API.
        assertTrue(SpeechFocus.inCall(AudioManager.MODE_CALL_REDIRECT))
        assertTrue(SpeechFocus.inCall(AudioManager.MODE_COMMUNICATION_REDIRECT))
        assertFalse(SpeechFocus.inCall(AudioManager.MODE_INVALID))
        assertFalse(SpeechFocus.inCall(AudioManager.MODE_CURRENT))
    }
}
