package fr.roadly.feature.search

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable

@Serializable object SearchRoute

fun NavGraphBuilder.searchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit) {
    composable<SearchRoute> { SearchScreen(onBack = onBack, onNavigateToRoute = onNavigateToRoute) }
}
