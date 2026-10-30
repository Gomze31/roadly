package fr.roadly.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable

/** [category] = nom d'une `PoiCategory` pour ouvrir directement une recherche « à proximité » (puces de l'accueil). */
@Serializable data class SearchRoute(val category: String? = null)

fun NavGraphBuilder.searchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit) {
    composable<SearchRoute> { SearchScreen(onBack = onBack, onNavigateToRoute = onNavigateToRoute) }
}
