package fr.roadly.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** [section] : nom d'une [SettingsSection] à ouvrir directement (ex. « UPDATES ») ; null = accueil des réglages. */
@Serializable data class SettingsRoute(val section: String? = null)

/** [onOpenTrips] : historique des trajets et Replay (feature:trips). */
fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onOpenTrips: () -> Unit = {}, onOpenProfile: () -> Unit = {}, onOpenPremium: () -> Unit = {}, onOpenAssistance: () -> Unit = {}, onOpenRadar: () -> Unit = {}) {
    composable<SettingsRoute> { entry ->
        val route = entry.toRoute<SettingsRoute>()
        SettingsScreen(onBack = onBack, initialSection = route.section, onOpenTrips = onOpenTrips, onOpenProfile = onOpenProfile, onOpenPremium = onOpenPremium, onOpenAssistance = onOpenAssistance, onOpenRadar = onOpenRadar)
    }
}

@Serializable object PremiumRoute
@Serializable object AssistanceRoute
@Serializable object RadarSettingsRoute

fun NavGraphBuilder.radarSettingsScreen(onBack: () -> Unit) { composable<RadarSettingsRoute> { RadarSettingsScreen(onBack = onBack) } }

fun NavGraphBuilder.premiumScreen(onBack: () -> Unit) { composable<PremiumRoute> { PremiumScreen(onBack = onBack) } }
fun NavGraphBuilder.assistanceScreen(onBack: () -> Unit) { composable<AssistanceRoute> { AssistanceScreen(onBack = onBack) } }
