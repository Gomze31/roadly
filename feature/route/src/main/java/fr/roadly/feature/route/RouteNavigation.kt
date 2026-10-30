package fr.roadly.feature.route

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data class RouteRoute(val destLat: Double, val destLon: Double, val destName: String)

/** [onStartNavigation] reçoit `begin` (démarre la session, false si rien à guider) ; l'app y ajoute permission et navigation. */
fun NavGraphBuilder.routeScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartNavigation: (begin: () -> Boolean) -> Unit = { begin -> begin() },
) {
    composable<RouteRoute> { RouteScreen(isDark = isDark, onBack = onBack, onOpenSettings = onOpenSettings, onStartNavigation = onStartNavigation) }
}
