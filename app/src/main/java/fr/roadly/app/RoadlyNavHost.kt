package fr.roadly.app

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.navigation.compose.currentBackStackEntryAsState
import fr.roadly.feature.onboarding.OnboardingScreen
import androidx.compose.ui.Modifier
import fr.roadly.feature.voice.VoiceCommandButton
import fr.roadly.feature.voice.VoiceReplyOverlay
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import fr.roadly.core.navigation.NavRequest
import fr.roadly.feature.home.HomeRoute
import fr.roadly.feature.home.homeScreen
import fr.roadly.feature.navigation.FreeDriveRoute
import fr.roadly.feature.navigation.NavigationRoute
import fr.roadly.feature.navigation.freeDriveScreen
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
import fr.roadly.feature.settings.RadarSettingsRoute
import fr.roadly.feature.settings.radarSettingsScreen
import fr.roadly.feature.settings.PremiumRoute
import fr.roadly.feature.settings.SettingsRoute
import fr.roadly.feature.settings.SettingsPage
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
    externalRequest: ExternalRequest? = null,
    onExternalHandled: () -> Unit = {},
    launch: LaunchViewModel = hiltViewModel(),
    navRequests: Flow<NavRequest> = emptyFlow(),
) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by nav.currentBackStackEntryAsState()
    Box(Modifier.fillMaxSize()) {
    NavHost(navController = nav, startDestination = HomeRoute) {
        homeScreen(
            isDark = isDark,
            onSearch = { nav.navigate(SearchRoute()) },
            onSettings = { nav.navigate(SettingsRoute()) },
            onNavigateToRoute = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) },
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenAppSettings = onOpenAppSettings,
            onResumeGuidance = { nav.navigate(NavigationRoute) { launchSingleTop = true } },
            onSearchCategory = { cat -> nav.navigate(SearchRoute(category = cat.name)) },
            onOpenProfile = { nav.navigate(ProfileRoute) },
            assistantButton = { VoiceCommandButton() },
            assistantPanel = { VoiceReplyOverlay(Modifier.fillMaxWidth()) },
            // API 33+ : notification du service demandée comme pour le guidage ; un refus masque seulement la notification.
            onFreeDrive = { withNotificationPermission { nav.navigate(FreeDriveRoute) { launchSingleTop = true } } },
            // Dictée depuis la barre de recherche de l'accueil : recherche pré-remplie.
            onSearchText = { text -> nav.navigate(SearchRoute(query = text)) },
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
            // « Renseigner le véhicule » / coût carburant : directement la page du véhicule (Retour mène au sous-menu).
            onOpenSettings = { nav.navigate(SettingsRoute(section = SettingsPage.VEHICLE.name)) },
            onStartNavigation = { begin ->
                // API 33+ : POST_NOTIFICATIONS demandée avant de démarrer le service (spec §2) ; refus = notification masquée seulement.
                withNotificationPermission {
                    if (begin()) nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                }
            },
            onAddStop = { nav.navigate(SearchRoute(pick = true)) },
            // Inversion départ / arrivée : nouvel écran d'itinéraires à la place du courant.
            onSwap = { r -> nav.navigate(r) { popUpTo<RouteRoute> { inclusive = true } } },
            onSearchFuel = { nav.navigate(SearchRoute(category = fr.roadly.core.geocoding.PoiCategory.FUEL.name)) },
        )
        settingsScreen(onBack = { nav.popBackStack() }, onOpenTrips = { nav.navigate(TripsRoute) }, onOpenProfile = { nav.navigate(ProfileRoute) }, onOpenPremium = { nav.navigate(PremiumRoute) }, onOpenAssistance = { nav.navigate(AssistanceRoute) }, onOpenRadar = { nav.navigate(RadarSettingsRoute) })
        radarSettingsScreen(onBack = { nav.popBackStack() })
        premiumScreen(onBack = { nav.popBackStack() })
        assistanceScreen(onBack = { nav.popBackStack() })
        tripsScreen(onBack = { nav.popBackStack() }, onOpenTrip = { id -> nav.navigate(ReplayRoute(id)) })
        profileScreen(onBack = { nav.popBackStack() }, onOpenTrips = { nav.navigate(TripsRoute) })
        replayScreen(isDark = isDark, onBack = { nav.popBackStack() }, onNavigateTo = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) { popUpTo(HomeRoute) } })
        freeDriveScreen(isDark = isDark, onExit = { if (!nav.popBackStack(HomeRoute, inclusive = false)) nav.navigate(HomeRoute) })
        navigationScreen(
            isDark = isDark,
            onExit = { if (!nav.popBackStack(HomeRoute, inclusive = false)) nav.navigate(HomeRoute) },
            onChangeDestination = { nav.navigate(SearchRoute()) },
            onAddStop = { nav.navigate(SearchRoute(pick = true)) },
            assistantButton = { VoiceCommandButton() },
            assistantPanel = { VoiceReplyOverlay(Modifier.fillMaxWidth()) },
        )
    }
    // Accueil du premier lancement, posé sur la carte de l'écran d'accueil (jamais par-dessus un guidage ouvert par la notification).
    if (backStackEntry?.destination?.hasRoute(HomeRoute::class) == true) {
        OnboardingScreen(
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenAppSettings = onOpenAppSettings,
            withNotificationPermission = withNotificationPermission,
            onOpenProfile = { nav.navigate(ProfileRoute) { launchSingleTop = true } },
        )
    }
    }
    // Mise à jour disponible (release GitHub) : proposée au lancement ; le suivi détaillé est dans Réglages › Mises à jour.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { launch.onForeground() }
    val update by launch.updatePrompt.collectAsStateWithLifecycle()
    update?.let { u ->
        AlertDialog(
            onDismissRequest = launch::dismiss,
            title = { Text("Roadly ${u.manifest.versionName} disponible") },
            text = { Text((u.notes.takeIf { it.isNotBlank() }?.take(600) ?: "Nouvelle version publiée sur GitHub.") + "\n\nLe téléchargement se poursuit dans Réglages › Mises à jour ; Android demande ensuite de confirmer l'installation.") },
            confirmButton = { TextButton(onClick = { launch.installUpdate(u); nav.navigate(SettingsRoute(section = SettingsPage.UPDATES.name)) }) { Text("Mettre à jour") } },
            dismissButton = { TextButton(onClick = { launch.later(u) }) { Text("Ignorer cette version") } },
        )
    }
    // Après NavHost : le graphe est posé avant de traiter une ouverture demandée par la notification.
    LaunchedEffect(openNavigationRequested) {
        if (openNavigationRequested) {
            // Notification de la conduite libre : même action, écran de conduite libre.
            if (launch.freeDriveActive()) nav.navigate(FreeDriveRoute) { popUpTo(HomeRoute); launchSingleTop = true }
            else nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
            onOpenNavigationHandled()
        }
    }
    // Raccourcis, widget, intents geo: / google.navigation: : résolus ici, après le graphe. Domicile / travail non définis → recherche + message.
    LaunchedEffect(externalRequest) {
        val r = externalRequest ?: return@LaunchedEffect
        fun toRoute(p: Place) = nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) { popUpTo(HomeRoute); launchSingleTop = true }
        fun toSearch(query: String? = null, message: Int? = null) {
            message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            nav.navigate(SearchRoute(query = query)) { popUpTo(HomeRoute); launchSingleTop = true }
        }
        when (r) {
            ExternalRequest.Home -> launch.home()?.let(::toRoute) ?: toSearch(message = R.string.external_home_undefined)
            ExternalRequest.Work -> launch.work()?.let(::toRoute) ?: toSearch(message = R.string.external_work_undefined)
            is ExternalRequest.Search -> toSearch(query = r.query)
            is ExternalRequest.Destination -> nav.navigate(RouteRoute(r.lat, r.lon, r.label ?: context.getString(R.string.external_destination))) { popUpTo(HomeRoute); launchSingleTop = true }
        }
        onExternalHandled()
    }
    // Demandes de l'assistant (ouvrir la recherche filtrée, le guidage, les réglages).
    LaunchedEffect(navRequests) {
        navRequests.collect { r ->
            when (r) {
                is NavRequest.OpenSearch -> nav.navigate(SearchRoute(category = r.category))
                NavRequest.OpenGuidance -> nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                NavRequest.OpenSettings -> nav.navigate(SettingsRoute()) { launchSingleTop = true }
            }
        }
    }
}
