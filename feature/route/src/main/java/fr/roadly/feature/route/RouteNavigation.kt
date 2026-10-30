package fr.roadly.feature.route

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data class RouteRoute(val destLat: Double, val destLon: Double, val destName: String)

fun NavGraphBuilder.routeScreen(isDark: Boolean, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    composable<RouteRoute> { RouteScreen(isDark = isDark, onBack = onBack, onOpenSettings = onOpenSettings) }
}
