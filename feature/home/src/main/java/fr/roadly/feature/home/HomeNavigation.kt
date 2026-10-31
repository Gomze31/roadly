package fr.roadly.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable

/** Destination de l'écran d'accueil (carte). */
@Serializable object HomeRoute

fun NavGraphBuilder.homeScreen(
    isDark: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNavigateToRoute: (Place) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onResumeGuidance: () -> Unit,
    onSearchCategory: (PoiCategory) -> Unit,
    onOpenProfile: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(isDark, onSearch, onSettings, onNavigateToRoute, onRequestLocationPermission, onOpenAppSettings, onResumeGuidance, onSearchCategory, onOpenProfile)
    }
}
