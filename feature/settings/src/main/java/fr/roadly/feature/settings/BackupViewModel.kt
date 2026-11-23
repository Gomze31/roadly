package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.backup.BackupError
import fr.roadly.core.data.backup.BackupRepository
import fr.roadly.core.data.backup.BackupResult
import fr.roadly.core.data.backup.ExportSummary
import fr.roadly.core.data.backup.ImportSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    data class Exported(val summary: ExportSummary) : BackupUi
    data class Imported(val summary: ImportSummary) : BackupUi
    data class ExportFailed(val error: BackupError) : BackupUi
    data class ImportFailed(val error: BackupError) : BackupUi
}

/** Réglages › Données › Sauvegarde : export et import des favoris et des trajets dans un fichier choisi par l'utilisateur. */
@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupRepository) : ViewModel() {
    private val _ui = MutableStateFlow<BackupUi>(BackupUi.Idle)
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    /** [open] ouvre en écriture le fichier que l'utilisateur vient de créer ; [onFailure] permet de retirer un fichier resté vide. */
    fun export(open: () -> OutputStream?, onFailure: () -> Unit = {}) {
        if (_ui.value == BackupUi.Working) return
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            _ui.value = when (val r = backup.export(open)) {
                is BackupResult.Done -> BackupUi.Exported(r.value)
                is BackupResult.Failed -> { onFailure(); BackupUi.ExportFailed(r.error) }
            }
        }
    }

    /** [open] ouvre en lecture le fichier que l'utilisateur vient de choisir. */
    fun import(open: () -> InputStream?) {
        if (_ui.value == BackupUi.Working) return
        _ui.value = BackupUi.Working
        viewModelScope.launch {
            _ui.value = when (val r = backup.import(open)) {
                is BackupResult.Done -> BackupUi.Imported(r.value)
                is BackupResult.Failed -> BackupUi.ImportFailed(r.error)
            }
        }
    }
}
