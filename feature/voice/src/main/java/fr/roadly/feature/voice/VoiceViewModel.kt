package fr.roadly.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Dernier échange affiché : phrase entendue, réponse ; [pending] pendant l'exécution. */
data class VoiceExchange(val heard: String, val reply: String?, val ok: Boolean = true, val pending: Boolean = false)

@HiltViewModel
class VoiceViewModel @Inject constructor(private val executor: VoiceCommandExecutor) : ViewModel() {
    private val _exchange = MutableStateFlow<VoiceExchange?>(null)
    val exchange: StateFlow<VoiceExchange?> = _exchange
    private var job: Job? = null

    fun onText(text: String) {
        job?.cancel()
        job = viewModelScope.launch {
            _exchange.value = VoiceExchange(text, null, pending = true)
            val reply = executor.handle(text)
            _exchange.value = VoiceExchange(text, reply.text, reply.ok)
            delay(SHOW_MS)
            _exchange.value = null
        }
    }

    /** Reconnaissance vocale absente du téléphone. */
    fun onUnavailable() {
        job?.cancel()
        job = viewModelScope.launch {
            _exchange.value = VoiceExchange("", "La reconnaissance vocale n'est pas disponible sur ce téléphone.", ok = false)
            delay(SHOW_MS)
            _exchange.value = null
        }
    }

    fun dismiss() { job?.cancel(); _exchange.value = null }

    companion object { const val SHOW_MS = 7_000L }
}
