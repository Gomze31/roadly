package fr.roadly.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import fr.roadly.feature.home.HomeRoute
import fr.roadly.feature.home.homeScreen
import fr.roadly.feature.route.RouteRoute
import fr.roadly.feature.route.routeScreen
import fr.roadly.feature.search.SearchRoute
import fr.roadly.feature.search.searchScreen
import fr.roadly.feature.settings.SettingsRoute
import fr.roadly.feature.settings.settingsScreen

/** Graphe de navigation typé : Accueil → Recherche → Itinéraire, Réglages. */
@Composable
fun RoadlyNavHost(isDark: Boolean, onRequestLocationPermission: () -> Unit, onOpenAppSettings: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        homeScreen(
            isDark = isDark,
            onSearch = { nav.navigate(SearchRoute) },
            onSettings = { nav.navigate(SettingsRoute) },
            onNavigateToRoute = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) },
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenAppSettings = onOpenAppSettings,
        )
        searchScreen(
            onBack = { nav.popBackStack() },
            // popUpTo(HomeRoute) : Retour depuis l'itinéraire revient à l'accueil.
            onNavigateToRoute = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) { popUpTo(HomeRoute) } },
        )
        routeScreen(isDark = isDark, onBack = { nav.popBackStack() }, onOpenSettings = { nav.navigate(SettingsRoute) })
        settingsScreen(onBack = { nav.popBackStack() })
    }
}
