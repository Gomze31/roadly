package fr.roadly.core.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.data.config.DataStoreProviderConfigRepository
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.connectivity.AndroidConnectivityObserver
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.data.fuel.OpenDataFuelStationProvider
import fr.roadly.core.data.vigilance.RoomVigilanceRepository
import fr.roadly.core.data.vigilance.VigilanceRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun favorites(impl: RoomFavoritesRepository): FavoritesRepository
    @Binds abstract fun recents(impl: RoomRecentSearchRepository): RecentSearchRepository
    @Binds abstract fun vehicle(impl: RoomVehicleProfileRepository): VehicleProfileRepository
    @Binds abstract fun prefs(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository
    @Binds abstract fun providerConfig(impl: DataStoreProviderConfigRepository): ProviderConfigRepository
    @Binds abstract fun connectivity(impl: AndroidConnectivityObserver): ConnectivityObserver
    @Binds abstract fun vigilance(impl: RoomVigilanceRepository): VigilanceRepository
    @Binds abstract fun fuel(impl: OpenDataFuelStationProvider): FuelStationProvider
}
