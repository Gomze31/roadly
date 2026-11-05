package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.radar.RadarKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Familles d'équipements activables dans les réglages (regroupent les catégories officielles). */
enum class RadarFamily(val kinds: Set<RadarKind>) {
    SPEED(setOf(RadarKind.FIXED, RadarKind.DISCRIMINATING, RadarKind.NEW_GENERATION, RadarKind.URBAN, RadarKind.UNKNOWN)),
    SECTION(setOf(RadarKind.SECTION)),
    RED_LIGHT(setOf(RadarKind.RED_LIGHT)),
    LEVEL_CROSSING(setOf(RadarKind.LEVEL_CROSSING)),
}

/**
 * Réglages Radar Intelligence. L'activation générale et le bip restent les réglages « vigilance » existants
 * ([UserPreferencesRepository.vigilanceEnabled], [UserPreferencesRepository.vigilanceSound]).
 */
data class RadarSettings(
    /** Annonce vocale à l'entrée de zone (au-delà du seuil de confiance). */
    val voice: Boolean = true,
    /** Préavis avant l'entrée (m) ; 0 = aucun. */
    val prewarnM: Int = 0,
    /** « Fin de zone de danger » annoncée à la sortie. */
    val announceExit: Boolean = false,
    val families: Set<RadarFamily> = RadarFamily.entries.toSet(),
) {
    val enabledKinds: Set<RadarKind> get() = families.flatMapTo(HashSet()) { it.kinds }

    companion object {
        val PREWARN_CHOICES = listOf(0, 250, 500, 1000)
    }
}

interface RadarSettingsRepository {
    val settings: Flow<RadarSettings>
    suspend fun setVoice(value: Boolean)
    /** Seules les valeurs de [RadarSettings.PREWARN_CHOICES] sont acceptées. */
    suspend fun setPrewarnM(value: Int)
    suspend fun setAnnounceExit(value: Boolean)
    suspend fun setFamily(family: RadarFamily, enabled: Boolean)
}

@Singleton
class DataStoreRadarSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : RadarSettingsRepository {
    private val kVoice = booleanPreferencesKey("radar.voice")
    private val kPrewarn = intPreferencesKey("radar.prewarn.m")
    private val kExit = booleanPreferencesKey("radar.announce.exit")
    private val kDisabled = stringPreferencesKey("radar.families.disabled")

    override val settings: Flow<RadarSettings> = store.data.map { p ->
        val disabled = p[kDisabled].orEmpty().split(',').mapNotNull { n -> RadarFamily.entries.firstOrNull { it.name == n } }.toSet()
        RadarSettings(
            voice = p[kVoice] ?: true,
            prewarnM = p[kPrewarn]?.takeIf { it in RadarSettings.PREWARN_CHOICES } ?: 0,
            announceExit = p[kExit] ?: false,
            families = RadarFamily.entries.toSet() - disabled,
        )
    }

    override suspend fun setVoice(value: Boolean) { store.edit { it[kVoice] = value } }
    override suspend fun setPrewarnM(value: Int) { if (value in RadarSettings.PREWARN_CHOICES) store.edit { it[kPrewarn] = value } }
    override suspend fun setAnnounceExit(value: Boolean) { store.edit { it[kExit] = value } }
    override suspend fun setFamily(family: RadarFamily, enabled: Boolean) {
        store.edit { p ->
            val disabled = p[kDisabled].orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet()
            if (enabled) disabled.remove(family.name) else disabled.add(family.name)
            p[kDisabled] = disabled.joinToString(",")
        }
    }
}

/** Réglages par défaut, immuables (tests, contextes sans stockage). */
object DefaultRadarSettingsRepository : RadarSettingsRepository {
    override val settings: Flow<RadarSettings> = MutableStateFlow(RadarSettings())
    override suspend fun setVoice(value: Boolean) = Unit
    override suspend fun setPrewarnM(value: Int) = Unit
    override suspend fun setAnnounceExit(value: Boolean) = Unit
    override suspend fun setFamily(family: RadarFamily, enabled: Boolean) = Unit
}
