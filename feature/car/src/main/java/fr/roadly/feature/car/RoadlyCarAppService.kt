package fr.roadly.feature.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.navigation.FreeDriveSession
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.VigilanceZoneProvider
import fr.roadly.core.data.repository.RadarSettingsRepository
import fr.roadly.core.routing.RouteAlternativesUseCase
import javax.inject.Inject

/**
 * Services partagés avec le téléphone : la voiture pilote la même session de guidage, la même conduite libre et les mêmes lieux
 * (un guidage lancé sur l'un s'affiche sur l'autre).
 */
class CarDependencies @Inject constructor(
    val navigation: NavigationSession,
    val guidance: GuidanceActions,
    val freeDrive: FreeDriveSession,
    val location: LocationRepository,
    val favorites: FavoritesRepository,
    val recents: RecentSearchRepository,
    val geocoder: GeocodingProvider,
    val alternatives: RouteAlternativesUseCase,
    val prefs: UserPreferencesRepository,
    val vehicles: VehicleProfileRepository,
    val config: ProviderConfigRepository,
    val clock: NavigationClock,
    val dangerZones: VigilanceZoneProvider,
    val radarSettings: RadarSettingsRepository,
)

/** Point d'entrée Android Auto (catégorie navigation). */
@AndroidEntryPoint
class RoadlyCarAppService : CarAppService() {
    @Inject lateinit var deps: CarDependencies

    /**
     * Builds débogables : tout hôte (unité principale de bureau, DHU). Sinon, seulement les hôtes connus (Android Auto, Android
     * Automotive) de la liste fournie par la bibliothèque.
     */
    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = RoadlyCarSession(deps)
}
