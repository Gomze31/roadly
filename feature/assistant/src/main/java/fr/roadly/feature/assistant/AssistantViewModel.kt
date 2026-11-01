package fr.roadly.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.assistant.AssistantController
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.tools.AssistantCards
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pont entre l'écran et la session Roadly AI (singleton) : un ViewModel par écran, un seul contrôleur. */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val controller: AssistantController,
    private val settings: AssistantSettingsRepository,
    cards: AssistantCards,
) : ViewModel() {
    val state = controller.state
    val micLevel = controller.micLevel
    val voiceLevel = controller.voiceLevel
    val card = cards.card
    /** null tant que les réglages ne sont pas lus (pas de dialogue de consentement prématuré). */
    val consentGranted: StateFlow<Boolean?> = settings.settings.map { s -> s.consentGranted as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onMic() = controller.toggle()
    fun grantConsentAndStart() { viewModelScope.launch { settings.setConsent(true); controller.start() } }
    fun confirm() = controller.confirmFromUi()
    fun cancel() = controller.cancelFromUi()
    fun onCardRow(placeId: String) = controller.addStopFromCard(placeId)
}
