package fr.roadly.feature.route

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [fromLat] / [fromLon] / [fromName] : point de départ choisi (bouton « inverser ») ; absents = position actuelle.
 * [destIsMyPosition] : la destination est la position de l'utilisateur au moment de l'inversion (une nouvelle inversion repart de la position réelle).
 */
@Serializable data class RouteRoute(
    val destLat: Double,
    val destLon: Double,
    val destName: String,
    val fromLat: Double? = null,
    val fromLon: Double? = null,
    val fromName: String? = null,
    val destIsMyPosition: Boolean = false,
)

/**
 * [onStartNavigation] reçoit `begin` (démarre la session, false si rien à guider) ; l'app y ajoute permission et navigation.
 * [onAddStop] ouvre la recherche en mode « choisir un arrêt » ; le lieu revient par `PICKED_STOP_KEY` du
 * `savedStateHandle` de l'entrée de pile (distinct de celui du ViewModel), consommé ici.
 */
fun NavGraphBuilder.routeScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartNavigation: (begin: () -> Boolean) -> Unit = { begin -> begin() },
    onAddStop: () -> Unit = {},
    onSwap: (RouteRoute) -> Unit = {},
    onSearchFuel: () -> Unit = {},
) {
    composable<RouteRoute> { entry ->
        val vm: RouteViewModel = hiltViewModel()
        val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_STOP_KEY, null).collectAsStateWithLifecycle()
        LaunchedEffect(picked) {
            val raw = picked ?: return@LaunchedEffect
            entry.savedStateHandle[PICKED_STOP_KEY] = null
            runCatching { Json.decodeFromString(Place.serializer(), raw) }.getOrNull()?.let { vm.addStop(it) }
        }
        RouteScreen(isDark = isDark, onBack = onBack, onOpenSettings = onOpenSettings, onStartNavigation = onStartNavigation, onAddStop = onAddStop, onSwap = onSwap, onSearchFuel = onSearchFuel, vm = vm)
    }
}
