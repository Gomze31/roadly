package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.TravelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Niveau de batterie saisi par l'utilisateur ([atEpochMs] = moment de la saisie) : Roadly n'est pas connecté à la trottinette. */
data class BatteryReading(val pct: Int, val atEpochMs: Long)

/**
 * Mode de déplacement et réglages trottinette. Clés DataStore préfixées `mobility.` / `scooter.` :
 * elles ne touchent jamais aux préférences d'itinéraire voiture / moto ([UserPreferencesRepository.routePreferences]).
 */
interface MobilityPreferencesRepository {
    /** Dernier mode choisi ; null = jamais choisi (déduire du profil véhicule, `TravelMode.derivedFrom`). */
    val travelMode: Flow<TravelMode?>
    suspend fun setTravelMode(mode: TravelMode)
    val scooterRoutePreferences: Flow<ScooterRoutePreferences>
    suspend fun setScooterRoutePreferences(value: ScooterRoutePreferences)
    val activeScooterProfileId: Flow<Long?>
    suspend fun setActiveScooterProfileId(id: Long?)
    val scooterBattery: Flow<BatteryReading?>
    suspend fun setScooterBattery(reading: BatteryReading?)
    /** Enrichissement de l'analyse par les tags OSM (Overpass, une requête par itinéraire). */
    val scooterOsmEnrichment: Flow<Boolean>
    suspend fun setScooterOsmEnrichment(value: Boolean)
}

@Singleton
class DataStoreMobilityPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) : MobilityPreferencesRepository {
    private val kMode = stringPreferencesKey("mobility.travel.mode")
    private val kGoal = stringPreferencesKey("scooter.route.goal")
    private val kCycleways = booleanPreferencesKey("scooter.route.prefer.cycleways")
    private val kBusy = booleanPreferencesKey("scooter.route.avoid.busy")
    private val kUnpaved = booleanPreferencesKey("scooter.route.avoid.unpaved")
    private val kHills = booleanPreferencesKey("scooter.route.avoid.hills")
    private val kRough = booleanPreferencesKey("scooter.route.avoid.rough")
    private val kProfile = longPreferencesKey("scooter.profile.active")
    private val kBatteryPct = intPreferencesKey("scooter.battery.pct")
    private val kBatteryAt = longPreferencesKey("scooter.battery.at")
    private val kOsm = booleanPreferencesKey("scooter.osm.enrichment")

    override val travelMode: Flow<TravelMode?> = store.data.map { p -> p[kMode]?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() } }
    override suspend fun setTravelMode(mode: TravelMode) { store.edit { it[kMode] = mode.name } }

    override val scooterRoutePreferences: Flow<ScooterRoutePreferences> = store.data.map { p ->
        val d = ScooterRoutePreferences()
        ScooterRoutePreferences(
            goal = p[kGoal]?.let { runCatching { ScooterRouteGoal.valueOf(it) }.getOrNull() } ?: d.goal,
            preferCycleways = p[kCycleways] ?: d.preferCycleways,
            avoidBusyRoads = p[kBusy] ?: d.avoidBusyRoads,
            avoidUnpaved = p[kUnpaved] ?: d.avoidUnpaved,
            avoidSteepHills = p[kHills] ?: d.avoidSteepHills,
            avoidRoughSurfaces = p[kRough] ?: d.avoidRoughSurfaces,
        )
    }
    override suspend fun setScooterRoutePreferences(value: ScooterRoutePreferences) {
        store.edit {
            it[kGoal] = value.goal.name; it[kCycleways] = value.preferCycleways; it[kBusy] = value.avoidBusyRoads
            it[kUnpaved] = value.avoidUnpaved; it[kHills] = value.avoidSteepHills; it[kRough] = value.avoidRoughSurfaces
        }
    }

    override val activeScooterProfileId: Flow<Long?> = store.data.map { it[kProfile] }
    override suspend fun setActiveScooterProfileId(id: Long?) { store.edit { if (id == null) it.remove(kProfile) else it[kProfile] = id } }

    override val scooterBattery: Flow<BatteryReading?> = store.data.map { p ->
        val pct = p[kBatteryPct] ?: return@map null
        BatteryReading(pct.coerceIn(0, 100), p[kBatteryAt] ?: 0L)
    }
    override suspend fun setScooterBattery(reading: BatteryReading?) {
        store.edit {
            if (reading == null) { it.remove(kBatteryPct); it.remove(kBatteryAt) }
            else { it[kBatteryPct] = reading.pct.coerceIn(0, 100); it[kBatteryAt] = reading.atEpochMs }
        }
    }

    override val scooterOsmEnrichment: Flow<Boolean> = store.data.map { it[kOsm] ?: true }
    override suspend fun setScooterOsmEnrichment(value: Boolean) { store.edit { it[kOsm] = value } }
}
