package fr.roadly.core.navigation

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Moteur simulé de Robolectric, plus deux pannes qu'il ne sait pas jouer : un moteur qui refuse l'énoncé (service du moteur
 * mort) et un moteur qui l'accepte sans jamais rappeler (ni début ni fin).
 */
@Implements(TextToSpeech::class)
class ScriptedTts : ShadowTextToSpeech() {
    enum class Mode { NORMAL, REFUSE, MUTE }

    @Implementation
    override fun speak(text: CharSequence, queueMode: Int, params: Bundle, utteranceId: String): Int = when (mode) {
        Mode.REFUSE -> TextToSpeech.ERROR
        Mode.MUTE -> { accepted += text.toString(); TextToSpeech.SUCCESS }
        Mode.NORMAL -> super.speak(text, queueMode, params, utteranceId)
    }

    companion object {
        @Volatile var mode = Mode.NORMAL
        val accepted = mutableListOf<String>()
    }
}

/**
 * TST-2 : chemins d'échec de la voix, sur le vrai [AndroidSpeechService] sous Robolectric (API 34). Dans chaque cas le guidage ne
 * doit ni planter ni rester « en train de parler », et la voix doit revenir dès que la cause disparaît.
 *
 * Ce que ces tests ne montrent PAS : le son réellement produit, le comportement d'un vrai moteur (Google, constructeur) ni la
 * politique de focus d'un vrai téléphone. Le focus perdu en cours d'annonce n'est pas couvert ici.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ScriptedTts::class])
class AndroidSpeechServiceTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val service = AndroidSpeechService(app)

    @Before fun setUp() {
        ScriptedTts.mode = ScriptedTts.Mode.NORMAL
        ScriptedTts.accepted.clear()
    }

    private fun idle() = ShadowLooper.idleMainLooper()
    private fun engine(): TextToSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance()
    private fun french() = ShadowTextToSpeech.addLanguageAvailability(Locale.FRANCE)
    /** Rappel d'initialisation du moteur, que Robolectric ne fait pas de lui-même. */
    private fun init(tts: TextToSpeech = engine(), status: Int = TextToSpeech.SUCCESS) {
        shadowOf(tts).onInitListener.onInit(status)
        idle()
    }
    private fun spoken(tts: TextToSpeech = engine()): List<String> = shadowOf(tts).spokenTextList

    @Test fun `an announcement made before the engine is ready is said once it is, then the audio focus is given back`() {
        french()
        service.speak("Tournez à droite", flush = false)
        assertTrue(service.speaking.value)
        assertTrue(spoken().isEmpty())
        init()
        assertEquals(listOf(SpeechText.normalize("Tournez à droite")), spoken())
        assertFalse(service.speaking.value)
        assertTrue(service.voiceAvailable.value)
        assertNotNull(shadowOf(audio).lastAudioFocusRequest)
        assertNotNull(shadowOf(audio).lastAbandonedAudioFocusRequest)
    }

    // ---- Moteur indisponible ----

    @Test fun `engine initialisation error - voice reported unavailable, nothing left pending, no new engine at each announcement`() {
        french()
        service.speak("Tournez à droite", flush = false)
        val first = engine()
        init(status = TextToSpeech.ERROR)
        assertFalse(service.voiceAvailable.value)
        assertFalse("rien ne reste « en train de parler »", service.speaking.value)
        assertTrue(shadowOf(first).isShutdown)
        service.speak("Tournez à gauche", flush = false)
        idle()
        assertSame("pas de moteur recréé à chaque annonce", first, engine())
        assertFalse(service.speaking.value)
        assertTrue(spoken(first).isEmpty())
    }

    @Test fun `no French voice data - voice reported unavailable and the announcement is dropped`() {
        service.speak("Tournez à droite", flush = false)
        init()
        assertFalse(service.voiceAvailable.value)
        assertFalse(service.speaking.value)
        assertTrue(shadowOf(engine()).isShutdown)
        assertTrue(spoken().isEmpty())
    }

    @Test fun `after a release the voice is tried again - French installed meanwhile, the next guidance speaks`() {
        service.speak("Tournez à droite", flush = false)
        val first = engine()
        init()
        assertFalse(service.voiceAvailable.value)

        service.release()
        idle()
        assertTrue(service.voiceAvailable.value)
        french()
        service.speak("Tournez à gauche", flush = false)
        assertNotSame(first, engine())
        init()
        assertEquals(listOf(SpeechText.normalize("Tournez à gauche")), spoken())
    }

    @Test fun `preferred engine without French - the default engine takes over and the pending announcement is kept`() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.google.android.tts"
            applicationInfo = ApplicationInfo().apply { packageName = "com.google.android.tts"; name = "tts" }
        })
        service.speak("Tournez à droite", flush = false)
        val preferred = engine()
        init(preferred)
        val fallback = engine()
        assertNotSame("second essai avec le moteur par défaut", preferred, fallback)
        assertTrue(shadowOf(preferred).isShutdown)
        assertTrue("pas encore déclarée indisponible", service.voiceAvailable.value)
        assertTrue("l'annonce attend le second moteur", service.speaking.value)

        french()
        init(fallback)
        assertEquals(listOf(SpeechText.normalize("Tournez à droite")), spoken(fallback))
        assertFalse(service.speaking.value)
    }

    // ---- Focus audio et appel ----

    @Test fun `audio focus denied - the announcement is dropped, not delayed, and the next one is said`() {
        french()
        shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        service.speak("Tournez à droite", flush = false)
        init()
        assertTrue(spoken().isEmpty())
        assertFalse(service.speaking.value)

        shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        service.speak("Tournez à gauche", flush = false)
        idle()
        assertEquals(listOf(SpeechText.normalize("Tournez à gauche")), spoken())
    }

    @Test fun `phone call in progress - nothing is said, the beep is silent, and the voice resumes after the call`() {
        french()
        audio.mode = AudioManager.MODE_IN_CALL
        service.speak("Tournez à droite", flush = false)
        init()
        service.beep()
        idle()
        assertTrue(spoken().isEmpty())
        assertFalse(service.speaking.value)

        audio.mode = AudioManager.MODE_NORMAL
        service.speak("Tournez à gauche", flush = false)
        idle()
        assertEquals(listOf(SpeechText.normalize("Tournez à gauche")), spoken())
    }

    // ---- Moteur en panne en cours de route ----

    @Test fun `engine that refuses an utterance (dead engine service) is dropped and a new one speaks the next announcement`() {
        french()
        ScriptedTts.mode = ScriptedTts.Mode.REFUSE
        service.speak("Tournez à droite", flush = false)
        val dead = engine()
        init()
        assertTrue(shadowOf(dead).isShutdown)
        assertFalse(service.speaking.value)
        assertTrue("une panne du moteur n'est pas une voix absente", service.voiceAvailable.value)

        ScriptedTts.mode = ScriptedTts.Mode.NORMAL
        service.speak("Tournez à gauche", flush = false)
        assertNotSame(dead, engine())
        init()
        assertEquals(listOf(SpeechText.normalize("Tournez à gauche")), spoken())
        assertFalse(service.speaking.value)
    }

    @Test fun `engine that never calls back - the utterance is closed by the guard delay and the next announcement goes out`() {
        french()
        ScriptedTts.mode = ScriptedTts.Mode.MUTE
        val first = "Tournez à droite"
        service.speak(first, flush = false)
        init()
        service.speak("Puis à gauche", flush = false)
        idle()
        assertEquals(listOf(SpeechText.normalize(first)), ScriptedTts.accepted)
        assertTrue(service.speaking.value)

        val guard = GuidanceConstants.SPEECH_UTTERANCE_TIMEOUT_MS + first.length * GuidanceConstants.SPEECH_MS_PER_CHAR
        ShadowLooper.idleMainLooper(guard - 100, TimeUnit.MILLISECONDS)
        assertEquals("pas avant le délai de garde", 1, ScriptedTts.accepted.size)
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
        assertEquals(listOf(SpeechText.normalize(first), SpeechText.normalize("Puis à gauche")), ScriptedTts.accepted)

        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS)
        assertFalse("la session ne reste pas « en train de parler »", service.speaking.value)
    }
}
