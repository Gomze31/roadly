package fr.roadly.core.routing

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Liaison Hilt : `RoutingProvider` par défaut = Valhalla (avec contournement des fermetures). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoutingModule {
    /** Valhalla, avec contournement automatique des routes fermées (Bison Futé). */
    @Binds abstract fun routing(impl: ClosureAwareRoutingProvider): RoutingProvider
    @Binds abstract fun scooterAnalyzer(impl: ValhallaScooterRouteAnalyzer): ScooterRouteAnalyzer
    @Binds abstract fun scooterPois(impl: OverpassScooterPoiProvider): ScooterPoiProvider
}
