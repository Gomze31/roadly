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
}
