package fr.roadly.core.routing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Liaison Hilt : `RoutingProvider` par défaut = Valhalla. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoutingModule {
    @Binds abstract fun routing(impl: ValhallaRoutingProvider): RoutingProvider
    @Binds abstract fun scooterAnalyzer(impl: ValhallaScooterRouteAnalyzer): ScooterRouteAnalyzer
}
