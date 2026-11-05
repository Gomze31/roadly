package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passage sous un ou plusieurs portiques de péage en flux libre pendant une course (Valhalla `node.type = toll_gantry`,
 * donnée OSM `highway=toll_gantry`). [id] = instant du premier passage, unique par course.
 */
data class TollReminder(
    val id: Long,
    val passedAtEpochMs: Long,
    /** Routes concernées (`A 79`…), dans l'ordre de passage, sans doublon. */
    val roads: List<String>,
    /** Pays du premier portique (ISO 3166-1 alpha-2) ; null = inconnu. */
    val countryCode: String?,
    val gantries: Int,
) {
    /**
     * Échéance de paiement : en France, 72 h après le passage (A79, A13/A14 et autres autoroutes en flux libre).
     * Ailleurs les règles varient : aucune échéance n'est affichée.
     */
    val dueAtEpochMs: Long? get() = if (countryCode == "FR") passedAtEpochMs + FR_PAYMENT_DELAY_MS else null

    companion object {
        const val FR_PAYMENT_DELAY_MS = 72L * 3_600_000L
    }
}

/** Position mémorisée du véhicule ; [label] = destination de la course qui l'a enregistrée, ou null (« ici »). */
data class ParkedVehicle(val point: GeoPoint, val savedAtEpochMs: Long, val label: String?)

/** Mémoire locale liée à la conduite : rappels de péage en flux libre et position du véhicule garé. */
interface DriveMemoryRepository {
    /** Rappels non encore marqués « payé », du plus récent au plus ancien. */
    val tollReminders: Flow<List<TollReminder>>
    suspend fun addTollReminder(reminder: TollReminder)
    suspend fun dismissTollReminder(id: Long)

    val parkedVehicle: Flow<ParkedVehicle?>
    suspend fun setParkedVehicle(value: ParkedVehicle?)
}

/** Sans mémoire (tests, contextes sans stockage) : rien n'est retenu. */
object NoDriveMemoryRepository : DriveMemoryRepository {
    override val tollReminders: Flow<List<TollReminder>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun addTollReminder(reminder: TollReminder) = Unit
    override suspend fun dismissTollReminder(id: Long) = Unit
    override val parkedVehicle: Flow<ParkedVehicle?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun setParkedVehicle(value: ParkedVehicle?) = Unit
}

/** Stockage DataStore (JSON) ; une valeur illisible est traitée comme absente, jamais comme une erreur. */
@Singleton
class DataStoreDriveMemoryRepository @Inject constructor(private val store: DataStore<Preferences>) : DriveMemoryRepository {
    private val kTolls = stringPreferencesKey("drive.toll.reminders")
    private val kParked = stringPreferencesKey("drive.parked.vehicle")

    override val tollReminders: Flow<List<TollReminder>> = store.data.map { p -> DriveMemoryCodec.decodeTolls(p[kTolls]) }

    override suspend fun addTollReminder(reminder: TollReminder) {
        store.edit { p ->
            val list = DriveMemoryCodec.decodeTolls(p[kTolls]).filterNot { it.id == reminder.id }
            p[kTolls] = DriveMemoryCodec.encodeTolls((listOf(reminder) + list).take(MAX_REMINDERS))
        }
    }

    override suspend fun dismissTollReminder(id: Long) {
        store.edit { p -> p[kTolls] = DriveMemoryCodec.encodeTolls(DriveMemoryCodec.decodeTolls(p[kTolls]).filterNot { it.id == id }) }
    }

    override val parkedVehicle: Flow<ParkedVehicle?> = store.data.map { p -> DriveMemoryCodec.decodeParked(p[kParked]) }

    override suspend fun setParkedVehicle(value: ParkedVehicle?) {
        store.edit { p -> if (value == null) p.remove(kParked) else p[kParked] = DriveMemoryCodec.encodeParked(value) }
    }

    private companion object { const val MAX_REMINDERS = 20 }
}

/** Codec JSON des valeurs de [DriveMemoryRepository] (testé sur JVM). */
object DriveMemoryCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class TollDto(val id: Long, val at: Long, val roads: List<String> = emptyList(), val cc: String? = null, val n: Int = 1)
    @Serializable private data class ParkedDto(val lat: Double, val lon: Double, val at: Long, val label: String? = null)

    fun encodeTolls(list: List<TollReminder>): String =
        json.encodeToString(list.map { TollDto(it.id, it.passedAtEpochMs, it.roads, it.countryCode, it.gantries) })

    fun decodeTolls(text: String?): List<TollReminder> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<TollDto>>(text) }.getOrNull().orEmpty()
            .map { TollReminder(it.id, it.at, it.roads, it.cc, it.n) }
    }

    fun encodeParked(v: ParkedVehicle): String = json.encodeToString(ParkedDto(v.point.lat, v.point.lon, v.savedAtEpochMs, v.label))

    fun decodeParked(text: String?): ParkedVehicle? {
        if (text.isNullOrBlank()) return null
        val d = runCatching { json.decodeFromString<ParkedDto>(text) }.getOrNull() ?: return null
        if (d.lat !in -90.0..90.0 || d.lon !in -180.0..180.0) return null
        return ParkedVehicle(GeoPoint(d.lat, d.lon), d.at, d.label)
    }
}
