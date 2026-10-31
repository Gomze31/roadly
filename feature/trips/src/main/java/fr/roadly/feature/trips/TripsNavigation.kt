package fr.roadly.feature.trips

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import fr.roadly.core.model.Place
import kotlinx.serialization.Serializable

@Serializable object TripsRoute
@Serializable data class ReplayRoute(val tripId: Long)

fun NavGraphBuilder.tripsScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit) {
    composable<TripsRoute> { TripsScreen(onBack = onBack, onOpenTrip = onOpenTrip) }
}

fun NavGraphBuilder.replayScreen(isDark: Boolean, onBack: () -> Unit, onNavigateTo: (Place) -> Unit) {
    composable<ReplayRoute> { ReplayScreen(isDark = isDark, onBack = onBack, onNavigateTo = onNavigateTo) }
}
