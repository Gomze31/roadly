package fr.roadly.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import fr.roadly.feature.assistant.AssistantMicButton
import fr.roadly.feature.assistant.AssistantOverlay
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import fr.roadly.core.navigation.NavRequest
import fr.roadly.feature.home.HomeRoute
import fr.roadly.feature.home.homeScreen
import fr.roadly.feature.navigation.NavigationRoute
import fr.roadly.feature.navigation.navigationScreen
import fr.roadly.core.model.Place
import fr.roadly.feature.route.PICKED_STOP_KEY
import fr.roadly.feature.route.RouteRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import fr.roadly.feature.route.routeScreen
import fr.roadly.feature.search.SearchRoute
import fr.roadly.feature.search.searchScreen
import fr.roadly.feature.settings.AssistanceRoute
import fr.roadly.feature.settings.PremiumRoute
import fr.roadly.feature.settings.SettingsRoute
import fr.roadly.feature.settings.assistanceScreen
import fr.roadly.feature.settings.premiumScreen
import fr.roadly.feature.settings.settingsScreen
import fr.roadly.feature.trips.ProfileRoute
import fr.roadly.feature.trips.ReplayRoute
import fr.roadly.feature.trips.profileScreen
import fr.roadly.feature.trips.TripsRoute
import fr.roadly.feature.trips.replayScreen
import fr.roadly.feature.trips.tripsScreen

/** Graphe de navigation typé : Accueil → Recherche → Itinéraire → Guidage, Réglages. */
@Composable
fun RoadlyNavHost(
    isDark: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    withNotificationPermission: (() -> Unit) -> Unit,
    openNavigationRequested: Boolean,
    onOpenNavigationHandled: () -> Unit,
    navRequests: Flow<NavRequest> = emptyFlow(),
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
            onOpenProfile = { nav.navigate(ProfileRoute) },
            assistantButton = { AssistantMicButton() },
            assistantPanel = { AssistantOverlay(Modifier.fillMaxWidth()) },
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
            // Mode « choisir un arrêt » : le lieu est rendu à l'écran appelant (itinéraires ou guidage) par son SavedStateHandle.
            onPick = { p ->
                nav.previousBackStackEntry?.savedStateHandle?.set(PICKED_STOP_KEY, Json.encodeToString(Place.serializer(), p))
                nav.popBackStack()
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
            onAddStop = { nav.navigate(SearchRoute(pick = true)) },
        )
        settingsScreen(onBack = { nav.popBackStack() }, onOpenTrips = { nav.navigate(TripsRoute) }, onOpenProfile = { nav.navigate(ProfileRoute) }, onOpenPremium = { nav.navigate(PremiumRoute) }, onOpenAssistance = { nav.navigate(AssistanceRoute) })
        premiumScreen(onBack = { nav.popBackStack() })
        assistanceScreen(onBack = { nav.popBackStack() })
        tripsScreen(onBack = { nav.popBackStack() }, onOpenTrip = { id -> nav.navigate(ReplayRoute(id)) })
        profileScreen(onBack = { nav.popBackStack() }, onOpenTrips = { nav.navigate(TripsRoute) })
        replayScreen(isDark = isDark, onBack = { nav.popBackStack() }, onNavigateTo = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) { popUpTo(HomeRoute) } })
        navigationScreen(
            isDark = isDark,
            onExit = { if (!nav.popBackStack(HomeRoute, inclusive = false)) nav.navigate(HomeRoute) },
            onChangeDestination = { nav.navigate(SearchRoute()) },
            onAddStop = { nav.navigate(SearchRoute(pick = true)) },
            assistantButton = { AssistantMicButton() },
            assistantPanel = { AssistantOverlay(Modifier.fillMaxWidth()) },
        )
    }
    // Après NavHost : le graphe est posé avant de traiter une ouverture demandée par la notification.
    LaunchedEffect(openNavigationRequested) {
        if (openNavigationRequested) {
            nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
            onOpenNavigationHandled()
        }
    }
    // Demandes de l'assistant (ouvrir la recherche filtrée, le guidage, les réglages).
    LaunchedEffect(navRequests) {
        navRequests.collect { r ->
            when (r) {
                is NavRequest.OpenSearch -> nav.navigate(SearchRoute(category = r.category))
                NavRequest.OpenGuidance -> nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                NavRequest.OpenSettings -> nav.navigate(SettingsRoute) { launchSingleTop = true }
            }
        }
    }
}
