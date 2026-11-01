package fr.roadly.core.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focus transitoire pendant la session (la musique se met en pause). Une annonce GPS (`MAY_DUCK`) arrive en
 * `LOSS_TRANSIENT_CAN_DUCK` : ignorée ici, c'est `SpeechService.speaking` qui met l'assistant en pause.
 */
@Singleton
class AndroidAssistantAudioFocus @Inject constructor(@param:ApplicationContext ctx: Context) : AudioFocus {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun acquire(onChange: (FocusChange) -> Unit): Boolean {
        release()
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> onChange(FocusChange.LOST)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(FocusChange.PAUSE)
                    AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.RESUME)
                }
            }
            .build()
        request = r
        return audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun release() {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
    }
}
