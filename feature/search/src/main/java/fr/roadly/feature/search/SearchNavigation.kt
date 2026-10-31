package fr.roadly.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable

/**
 * [category] = nom d'une `PoiCategory` pour ouvrir directement une recherche « à proximité » (puces de l'accueil).
 * [pick] = mode « choisir un arrêt » : le lieu choisi est rendu à l'écran appelant via [onPick] au lieu d'ouvrir la fiche.
 */
@Serializable data class SearchRoute(val category: String? = null, val pick: Boolean = false)

fun NavGraphBuilder.searchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit, onPick: (Place) -> Unit = {}) {
    composable<SearchRoute> { entry ->
        val pick = entry.arguments?.getBoolean("pick") == true
        SearchScreen(onBack = onBack, onNavigateToRoute = onNavigateToRoute, onPick = if (pick) onPick else null)
    }
}
