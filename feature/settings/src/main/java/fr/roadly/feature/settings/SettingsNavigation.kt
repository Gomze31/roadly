package fr.roadly.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable object SettingsRoute

/** [onOpenTrips] : historique des trajets et Replay (feature:trips). */
fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onOpenTrips: () -> Unit = {}, onOpenProfile: () -> Unit = {}) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack, onOpenTrips = onOpenTrips, onOpenProfile = onOpenProfile) }
}
