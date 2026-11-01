package fr.roadly.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.update.AvailableUpdate
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.update.AppUpdateController
import fr.roadly.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Résout les demandes externes qui dépendent des données locales (domicile, travail) ; vérifie les mises à jour au lancement. */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
    private val updates: AppUpdateController,
) : ViewModel() {
    suspend fun home(): Place? = favorites.observeHome().first()
    suspend fun work(): Place? = favorites.observeWork().first()

    private val _prompt = MutableStateFlow<AvailableUpdate?>(null)
    /** Nouvelle version à proposer (vérification automatique au plus toutes les 12 h, version ignorée exclue). */
    val updatePrompt: StateFlow<AvailableUpdate?> = _prompt

    init {
        viewModelScope.launch {
            updates.checkIfDue()
            _prompt.value = updates.promptable()
        }
    }

    fun installUpdate(update: AvailableUpdate) {
        _prompt.value = null
        viewModelScope.launch { updates.downloadAndInstall(update) }
    }

    fun later(update: AvailableUpdate) {
        _prompt.value = null
        viewModelScope.launch { updates.skip(update) }
    }

    fun dismiss() { _prompt.value = null }
}
