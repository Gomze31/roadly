package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.premium.BillingProvider
import fr.roadly.core.data.premium.EntitlementsRepository
import fr.roadly.core.data.premium.StoreState
import fr.roadly.core.model.Entitlements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUiState(
    val entitlements: Entitlements = Entitlements.NO_STORE,
    val store: StoreState? = null,
    val busy: Boolean = false,
    val lastError: AppError? = null,
)

/** Roadly+ : droits courants, état réel de la boutique, achat / restauration quand une boutique est intégrée. */
@HiltViewModel
class PremiumViewModel @Inject constructor(entitlementsRepo: EntitlementsRepository, private val billing: BillingProvider) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<PremiumUiState> = combine(entitlementsRepo.entitlements, billing.store, busy, error) { e, s, b, err ->
        PremiumUiState(e, s, b, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PremiumUiState())

    fun purchase() = run { billing.purchase() }
    fun restore() = run { billing.restore() }

    private fun run(op: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            busy.value = true; error.value = null
            when (val r = op()) { is Outcome.Failure -> error.value = r.error; is Outcome.Success -> Unit }
            busy.value = false
        }
    }
}
