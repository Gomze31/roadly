package fr.roadly.core.data.repository

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.data.premium.BillingProvider
import fr.roadly.core.data.premium.DefaultEntitlementsRepository
import fr.roadly.core.data.premium.EntitlementsRepository
import fr.roadly.core.data.premium.NoBillingProvider
import fr.roadly.core.data.config.DataStoreProviderConfigRepository
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.connectivity.AndroidConnectivityObserver
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.community.CommunityConfigRepository
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.DataStoreCommunityConfigRepository
import fr.roadly.core.data.community.SupabaseCommunityRepository
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
    @Binds abstract fun communityConfig(impl: DataStoreCommunityConfigRepository): CommunityConfigRepository
    @Binds abstract fun community(impl: SupabaseCommunityRepository): CommunityRepository
    @Binds abstract fun trips(impl: RoomTripRepository): TripRepository
    @Binds abstract fun mobility(impl: DataStoreMobilityPreferencesRepository): MobilityPreferencesRepository
    @Binds abstract fun scooterProfiles(impl: RoomScooterProfileRepository): ScooterProfileRepository
    @Binds abstract fun billing(impl: NoBillingProvider): BillingProvider
    @Binds abstract fun entitlements(impl: DefaultEntitlementsRepository): EntitlementsRepository
    @Binds abstract fun driveMemory(impl: DataStoreDriveMemoryRepository): DriveMemoryRepository
    @Binds abstract fun radarSettings(impl: DataStoreRadarSettingsRepository): RadarSettingsRepository
    @Binds abstract fun zfe(impl: fr.roadly.core.data.zfe.ZfeRepository): fr.roadly.core.data.zfe.ZfeSource
    @Binds abstract fun traffic(impl: fr.roadly.core.data.traffic.TrafficRepository): fr.roadly.core.data.traffic.TrafficSource
}
