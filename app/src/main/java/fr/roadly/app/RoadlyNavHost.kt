package fr.roadly.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import fr.roadly.feature.home.HomeRoute
import fr.roadly.feature.home.homeScreen
import fr.roadly.feature.navigation.NavigationRoute
import fr.roadly.feature.navigation.navigationScreen
import fr.roadly.feature.route.RouteRoute
import fr.roadly.feature.route.routeScreen
import fr.roadly.feature.search.SearchRoute
import fr.roadly.feature.search.searchScreen
import fr.roadly.feature.settings.SettingsRoute
import fr.roadly.feature.settings.settingsScreen

/** Graphe de navigation typé : Accueil → Recherche → Itinéraire → Guidage, Réglages. */
@Composable
fun RoadlyNavHost(
    isDark: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    withNotificationPermission: (() -> Unit) -> Unit,
    openNavigationRequested: Boolean,
    onOpenNavigationHandled: () -> Unit,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        homeScreen(
            isDark = isDark,
            onSearch = { nav.navigate(SearchRoute()) },
            onSettings = { nav.navigate(SettingsRoute) },
            onNavigateToRoute = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) },
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenAppSettings = onOpenAppSettings,
            onResumeGuidance = { nav.navigate(NavigationRoute) { launchSingleTop = true } },
            onSearchCategory = { cat -> nav.navigate(SearchRoute(category = cat.name)) },
        )
        searchScreen(
            onBack = { nav.popBackStack() },
            // Changement de destination pendant un guidage : on garde l'écran de guidage sous l'itinéraire (Retour y revient),
            // sinon popUpTo(HomeRoute) : Retour depuis l'itinéraire revient à l'accueil.
            onNavigateToRoute = { p ->
                val guidanceInStack = nav.currentBackStack.value.any { it.destination.hasRoute(NavigationRoute::class) }
                nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) {
                    if (guidanceInStack) popUpTo(NavigationRoute) { inclusive = false } else popUpTo(HomeRoute)
                }
            },
        )
        routeScreen(
            isDark = isDark,
            onBack = { nav.popBackStack() },
            onOpenSettings = { nav.navigate(SettingsRoute) },
            onStartNavigation = { begin ->
                // API 33+ : POST_NOTIFICATIONS demandée avant de démarrer le service (spec §2) ; refus = notification masquée seulement.
                withNotificationPermission {
                    if (begin()) nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                }
            },
        )
        settingsScreen(onBack = { nav.popBackStack() })
        navigationScreen(
            isDark = isDark,
            onExit = { if (!nav.popBackStack(HomeRoute, inclusive = false)) nav.navigate(HomeRoute) },
            onChangeDestination = { nav.navigate(SearchRoute()) },
        )
    }
    // Après NavHost : le graphe est posé avant de traiter une ouverture demandée par la notification.
    LaunchedEffect(openNavigationRequested) {
        if (openNavigationRequested) {
            nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
            onOpenNavigationHandled()
        }
    }
}
