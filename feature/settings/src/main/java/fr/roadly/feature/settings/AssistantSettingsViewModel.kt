package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantKeyVerifier
import fr.roadly.core.assistant.settings.AssistantSettings
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.AssistantUsage
import fr.roadly.core.assistant.settings.KeySource
import fr.roadly.core.assistant.settings.UsageMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Running : KeyCheck
    data object Ok : KeyCheck
    data class Failed(val error: AssistantError) : KeyCheck
}

/** Réglages → Roadly AI : activation, consentement, clé (jamais affichée), plafond, usage du jour. */
@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    private val repo: AssistantSettingsRepository,
    private val keys: ApiKeyProvider,
    private val verifier: AssistantKeyVerifier,
) : ViewModel() {
    val settings: StateFlow<AssistantSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings())
    val todayUsage: StateFlow<AssistantUsage> = repo.usage.map { u ->
        if (u.day == UsageMeter.day(System.currentTimeMillis(), ZoneId.systemDefault())) u else AssistantUsage()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUsage())
    private val _source = MutableStateFlow(KeySource.NONE)
    val source: StateFlow<KeySource> = _source.asStateFlow()
    private val _verify = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val verify: StateFlow<KeyCheck> = _verify.asStateFlow()

    init { viewModelScope.launch { _source.value = keys.source() } }

    fun setEnabled(value: Boolean) { viewModelScope.launch { repo.setEnabled(value) } }
    fun setConsent(value: Boolean) { viewModelScope.launch { repo.setConsent(value) } }
    fun setCap(minutes: Int) { viewModelScope.launch { repo.setDailyCap(minutes) } }

    fun saveKey(key: String) {
        viewModelScope.launch {
            // Une erreur Keystore ne doit pas faire tomber l'écran : la clé n'est simplement pas enregistrée.
            runCatching { keys.save(key) }
            _source.value = keys.source()
            check()
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            keys.clearOverride()
            _source.value = keys.source()
            _verify.value = KeyCheck.Idle
        }
    }

    fun verifyNow() { viewModelScope.launch { check() } }

    private suspend fun check() {
        _verify.value = KeyCheck.Running
        val key = keys.key()
        _verify.value = if (key == null) KeyCheck.Failed(AssistantError.NO_KEY) else verifier.verify(key)?.let { KeyCheck.Failed(it) } ?: KeyCheck.Ok
    }
}
