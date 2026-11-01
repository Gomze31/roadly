package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.AppVersion
import fr.roadly.core.data.update.AppUpdateController
import fr.roadly.core.data.update.UpdateSettings
import fr.roadly.core.data.update.UpdateState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdatesUi(
    val current: AppVersion = AppVersion(0, ""),
    val state: UpdateState = UpdateState.Idle,
    val repository: String = AppUpdates.DEFAULT_REPOSITORY,
    val hasToken: Boolean = false,
    val autoCheck: Boolean = true,
    val lastCheckAtMs: Long? = null,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val controller: AppUpdateController,
    private val settings: UpdateSettings,
    private val version: AppVersion,
) : ViewModel() {
    val ui: StateFlow<UpdatesUi> = combine(controller.state, settings.repository, settings.token, settings.autoCheck, settings.lastCheckAtMs) { st, repo, token, auto, last ->
        UpdatesUi(version, st, repo, !token.isNullOrBlank(), auto, last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUi(current = version))

    fun check() { viewModelScope.launch { controller.check() } }
    fun install() {
        viewModelScope.launch {
            when (val s = controller.state.value) {
                is UpdateState.Available -> controller.downloadAndInstall(s.update)
                is UpdateState.Failed -> s.update?.let { controller.downloadAndInstall(it) } ?: controller.check()
                is UpdateState.ReadyToInstall, is UpdateState.NeedsPermission -> controller.install()
                else -> Unit
            }
        }
    }
    /** Retour des réglages Android « sources inconnues » : relance l'installation si l'autorisation est accordée. */
    fun onPermissionScreenClosed() { viewModelScope.launch { if (controller.state.value is UpdateState.NeedsPermission) controller.install() } }
    /** Vide = garder le dépôt actuel ; valeur invalide refusée à la vérification. */
    fun saveSource(repository: String, token: String?) {
        viewModelScope.launch {
            if (repository.isNotBlank()) settings.setRepository(repository)
            if (token != null) settings.setToken(token)
        }
    }
    fun clearToken() { viewModelScope.launch { settings.setToken(null) } }
    fun setAutoCheck(value: Boolean) { viewModelScope.launch { settings.setAutoCheck(value) } }
}
