package fr.roadly.core.assistant.tools

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationSession

/** Outils exposés au modèle ; ajouter un outil = une liaison ici. Aucun outil ne touche aux alertes, à la voix GPS ni aux réglages. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantToolsModule {
    @Binds @IntoSet abstract fun status(t: GetNavigationStatusTool): AssistantTool
    @Binds @IntoSet abstract fun nextInstruction(t: GetNextInstructionTool): AssistantTool
    @Binds @IntoSet abstract fun distance(t: GetDistanceRemainingTool): AssistantTool
    @Binds @IntoSet abstract fun arrival(t: GetArrivalTimeTool): AssistantTool
    @Binds @IntoSet abstract fun stop(t: StopNavigationTool): AssistantTool
    @Binds @IntoSet abstract fun recalculate(t: RecalculateRouteTool): AssistantTool
    @Binds @IntoSet abstract fun end(t: EndConversationTool): AssistantTool
    @Binds @IntoSet abstract fun alternatives(t: FindAlternativeRoutesTool): AssistantTool
    @Binds @IntoSet abstract fun selectRoute(t: SelectRouteTool): AssistantTool
    @Binds @IntoSet abstract fun arrivalTime(t: SetArrivalTimeTool): AssistantTool
    @Binds @IntoSet abstract fun addStop(t: AddStopTool): AssistantTool
    @Binds @IntoSet abstract fun removeStop(t: RemoveStopTool): AssistantTool
    @Binds @IntoSet abstract fun stops(t: GetStopsTool): AssistantTool
    @Binds @IntoSet abstract fun fuelPrices(t: GetFuelPricesTool): AssistantTool
    @Binds @IntoSet abstract fun cheapestFuel(t: FindCheapestFuelOnRouteTool): AssistantTool
    @Binds @IntoSet abstract fun tripCost(t: GetEstimatedTripCostTool): AssistantTool
    @Binds @IntoSet abstract fun traffic(t: ShowTrafficTool): AssistantTool
    @Binds @IntoSet abstract fun report(t: ReportIncidentTool): AssistantTool
    @Binds @IntoSet abstract fun confirmIncident(t: ConfirmIncidentTool): AssistantTool
    @Binds @IntoSet abstract fun vehicle(t: GetVehicleProfileTool): AssistantTool
    @Binds @IntoSet abstract fun changeVehicle(t: ChangeVehicleProfileTool): AssistantTool

    companion object {
        @Provides @IntoSet
        fun startNavigation(r: DestinationResolver, g: GuidanceActions, n: AppNavigator): AssistantTool =
            DestinationTool("startNavigation", "Démarre le guidage vers une destination, après confirmation.", "Démarrer le guidage vers", r, g, n)

        @Provides @IntoSet
        fun changeDestination(r: DestinationResolver, g: GuidanceActions, n: AppNavigator): AssistantTool =
            DestinationTool("changeDestination", "Remplace la destination du guidage en cours, après confirmation.", "Changer la destination pour", r, g, n)

        @Provides @IntoSet
        fun avoidTolls(p: UserPreferencesRepository, g: GuidanceActions, s: NavigationSession): AssistantTool =
            AvoidOptionTool("avoidTolls", "Évite (ou autorise de nouveau) les péages ; en guidage, recalcule et compare les durées.", { prefs, on -> prefs.copy(avoidTolls = on) }, p, g, s)

        @Provides @IntoSet
        fun avoidHighways(p: UserPreferencesRepository, g: GuidanceActions, s: NavigationSession): AssistantTool =
            AvoidOptionTool("avoidHighways", "Évite (ou autorise de nouveau) les autoroutes ; en guidage, recalcule et compare les durées.", { prefs, on -> prefs.copy(avoidHighways = on) }, p, g, s)

        @Provides @IntoSet
        fun gasStations(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showGasStations", "Stations-service proches (OpenStreetMap). Pour les prix : getFuelPrices ou findCheapestFuelOnRoute.", listOf(PoiCategory.FUEL), "Stations proches", s, n, l, nav, c, pl)

        @Provides @IntoSet
        fun parking(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showParking", "Parkings proches (OpenStreetMap ; tarifs et places libres inconnus).", listOf(PoiCategory.PARKING), "Parkings proches", s, n, l, nav, c, pl)

        @Provides @IntoSet
        fun charging(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showChargingStations", "Bornes de recharge proches (OpenStreetMap ; disponibilité inconnue).", listOf(PoiCategory.CHARGING), "Bornes proches", s, n, l, nav, c, pl)

        @Provides @IntoSet
        fun services(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showNearbyServices", "Aires de service et restaurants proches (OpenStreetMap).", listOf(PoiCategory.REST_AREA, PoiCategory.RESTAURANT), "Services proches", s, n, l, nav, c, pl)
    }
}
