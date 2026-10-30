package fr.roadly.core.geocoding

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Liaisons Hilt : fournisseurs qualifiés, et `GeocodingProvider` par défaut = géocodeur composite. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GeocodingModule {
    @Binds @BanGeocoder abstract fun ban(impl: BanGeocodingProvider): GeocodingProvider
    @Binds @PhotonGeocoder abstract fun photon(impl: PhotonGeocodingProvider): GeocodingProvider
    @Binds abstract fun composite(impl: CompositeGeocoder): GeocodingProvider
}
