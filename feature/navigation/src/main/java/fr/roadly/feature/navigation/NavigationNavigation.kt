package fr.roadly.feature.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** L'itinéraire est porté par NavigationSession, pas par un argument de navigation (spec §3). */
@Serializable object NavigationRoute

fun NavGraphBuilder.navigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit) {
    composable<NavigationRoute> { NavigationScreen(isDark = isDark, onExit = onExit, onChangeDestination = onChangeDestination) }
}
