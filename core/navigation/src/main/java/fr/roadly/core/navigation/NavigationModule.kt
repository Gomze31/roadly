package fr.roadly.core.navigation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Liaisons Hilt du guidage ; `NavigationSession` est injectable directement, `RoutingProvider` vient de `core:routing`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {
    /** S'auto-désactive sur l'instance publique FOSSGIS (limitation « — »). */
    @Binds abstract fun speedLimits(impl: ValhallaTraceSpeedLimitProvider): SpeedLimitProvider
    @Binds abstract fun speech(impl: AndroidSpeechService): SpeechService
    @Binds abstract fun serviceController(impl: ForegroundServiceController): NavigationServiceController
    @Binds abstract fun clock(impl: SystemNavigationClock): NavigationClock
    @Binds abstract fun vigilance(impl: RepositoryVigilanceZoneProvider): VigilanceZoneProvider
    @Binds abstract fun rain(impl: OpenMeteoRainProvider): RainProvider
    @Binds abstract fun tripRecorder(impl: RoomTripRecorder): TripRecorder
    @Binds abstract fun temperature(impl: OpenMeteoTemperatureProvider): TemperatureProvider
    /** 1.0 : limitations et portiques du tracé, une requête `trace_attributes` par tracé (y compris sur l'instance publique). */
    @Binds abstract fun routeAttributes(impl: ValhallaRouteAttributesProvider): RouteAttributesProvider
    /** 1.0 : route sous la position en conduite libre (map matching espacé, voir `RoadQueryPolicy`). */
    /** Vue 3D de carrefour : une petite requête `trace_attributes` par carrefour retenu. */
    @Binds abstract fun junctionTopology(impl: fr.roadly.core.navigation.junction.ValhallaJunctionTopologyProvider): fr.roadly.core.navigation.junction.JunctionTopologyProvider
    @Binds abstract fun roadSnapshots(impl: ValhallaRoadSnapshotProvider): RoadSnapshotProvider
    /** Ralentisseurs OSM des voies lentes du tracé, une requête Overpass par tracé. */
    @Binds abstract fun speedBumps(impl: OverpassSpeedBumpProvider): SpeedBumpProvider
}
