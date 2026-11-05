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
    /**
     * Zones signalées comme fausses alertes par l'utilisateur (identifiant d'équipement) : plus jamais annoncées ni dessinées sur ce
     * téléphone, jusqu'à « Tout réafficher ». Plafonnées à [MAX_HIDDEN] (les plus anciennes sortent).
     */
    val hiddenZones: Set<String> = emptySet(),
    /**
     * Équipements signalés par des conducteurs (MapAtlas) absents de la liste officielle : désactivés par défaut, alerte visuelle
     * seulement (confiance 45). La corroboration et le sens indicatif des équipements officiels restent toujours actifs.
     */
    val communityZones: Boolean = false,
) {
    /** Un équipement de la base est-il retenu par ces réglages (zone masquée, source communautaire non activée) ? */
    fun accepts(radar: fr.roadly.core.radar.Radar): Boolean =
        radar.id !in hiddenZones && (communityZones || radar.source != fr.roadly.core.radar.RadarSourceIds.MAPATLAS)

    val enabledKinds: Set<RadarKind> get() = families.flatMapTo(HashSet()) { it.kinds }

    companion object {
        val PREWARN_CHOICES = listOf(0, 250, 500, 1000)
        const val MAX_HIDDEN = 500
    }
}

interface RadarSettingsRepository {
    val settings: Flow<RadarSettings>
    suspend fun setVoice(value: Boolean)
    /** Seules les valeurs de [RadarSettings.PREWARN_CHOICES] sont acceptées. */
    suspend fun setPrewarnM(value: Int)
    suspend fun setAnnounceExit(value: Boolean)
    suspend fun setFamily(family: RadarFamily, enabled: Boolean)
    /** Fausse alerte : la zone [key] n'est plus annoncée ni dessinée. */
    suspend fun hideZone(key: String)
    suspend fun clearHiddenZones()
    suspend fun setCommunityZones(value: Boolean)
}

@Singleton
class DataStoreRadarSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : RadarSettingsRepository {
    private val kVoice = booleanPreferencesKey("radar.voice")
    private val kPrewarn = intPreferencesKey("radar.prewarn.m")
    private val kExit = booleanPreferencesKey("radar.announce.exit")
    private val kDisabled = stringPreferencesKey("radar.families.disabled")
    /** Ordre d'ajout conservé (liste séparée par des virgules) pour retirer les plus anciennes au-delà du plafond. */
    private val kHidden = stringPreferencesKey("radar.hidden.zones")
    private val kCommunity = booleanPreferencesKey("radar.community.zones")

    override val settings: Flow<RadarSettings> = store.data.map { p ->
        val disabled = p[kDisabled].orEmpty().split(',').mapNotNull { n -> RadarFamily.entries.firstOrNull { it.name == n } }.toSet()
        RadarSettings(
            voice = p[kVoice] ?: true,
            prewarnM = p[kPrewarn]?.takeIf { it in RadarSettings.PREWARN_CHOICES } ?: 0,
            announceExit = p[kExit] ?: false,
            families = RadarFamily.entries.toSet() - disabled,
            hiddenZones = HiddenZones.decode(p[kHidden]).toSet(),
            communityZones = p[kCommunity] ?: false,
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
    override suspend fun hideZone(key: String) { store.edit { p -> p[kHidden] = HiddenZones.encode(HiddenZones.add(HiddenZones.decode(p[kHidden]), key)) } }
    override suspend fun clearHiddenZones() { store.edit { it.remove(kHidden) } }
    override suspend fun setCommunityZones(value: Boolean) { store.edit { it[kCommunity] = value } }
}

/** Liste ordonnée des zones masquées, sans doublon, plafonnée à [RadarSettings.MAX_HIDDEN]. */
object HiddenZones {
    fun decode(raw: String?): List<String> = raw.orEmpty().split(',').filter { it.isNotBlank() }
    fun encode(keys: List<String>): String = keys.joinToString(",")
    fun add(keys: List<String>, key: String): List<String> {
        val clean = key.replace(",", "").trim()
        if (clean.isEmpty()) return keys
        return (keys.filter { it != clean } + clean).takeLast(RadarSettings.MAX_HIDDEN)
    }
}

/** Réglages par défaut, immuables (tests, contextes sans stockage). */
object DefaultRadarSettingsRepository : RadarSettingsRepository {
    override val settings: Flow<RadarSettings> = MutableStateFlow(RadarSettings())
    override suspend fun setVoice(value: Boolean) = Unit
    override suspend fun setPrewarnM(value: Int) = Unit
    override suspend fun setAnnounceExit(value: Boolean) = Unit
    override suspend fun setFamily(family: RadarFamily, enabled: Boolean) = Unit
    override suspend fun hideZone(key: String) = Unit
    override suspend fun clearHiddenZones() = Unit
    override suspend fun setCommunityZones(value: Boolean) = Unit
}
