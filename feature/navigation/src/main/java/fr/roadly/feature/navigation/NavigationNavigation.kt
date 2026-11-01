package fr.roadly.feature.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable object NavigationRoute

/** Clé par laquelle la recherche (mode arrêt) renvoie un lieu dans le `savedStateHandle` de l'entrée de pile. */
const val NAV_PICKED_STOP_KEY = "pickedStop"

/** [onAddStop] ouvre la recherche en mode « choisir un arrêt » ; le lieu revient par [NAV_PICKED_STOP_KEY] et relance le guidage via cet arrêt. */
fun NavGraphBuilder.navigationScreen(
    isDark: Boolean,
    onExit: () -> Unit,
    onChangeDestination: () -> Unit,
    onAddStop: () -> Unit = {},
    assistantButton: @Composable () -> Unit = {},
    assistantPanel: @Composable () -> Unit = {},
) {
    composable<NavigationRoute> { entry ->
        val vm: NavigationViewModel = hiltViewModel()
        val picked by entry.savedStateHandle.getStateFlow<String?>(NAV_PICKED_STOP_KEY, null).collectAsStateWithLifecycle()
        LaunchedEffect(picked) {
            val raw = picked ?: return@LaunchedEffect
            entry.savedStateHandle[NAV_PICKED_STOP_KEY] = null
            runCatching { Json.decodeFromString(Place.serializer(), raw) }.getOrNull()?.let { vm.addStop(it) }
        }
        NavigationScreen(
            isDark = isDark, onExit = onExit, onChangeDestination = onChangeDestination, onAddStop = onAddStop,
            assistantButton = assistantButton, assistantPanel = assistantPanel, vm = vm,
        )
    }
}
