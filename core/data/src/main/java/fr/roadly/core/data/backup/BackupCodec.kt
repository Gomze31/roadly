package fr.roadly.core.data.backup

import fr.roadly.core.model.PlaceKind
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Sauvegarde des favoris et des trajets : un document JSON versionné, lisible, que l'utilisateur range où il veut. Elle sert
 * à ne pas tout perdre lors d'une réinstallation (`allowBackup="false"` : Android ne sauvegarde rien de Roadly).
 * Jamais de secret (jeton GitHub, clés de la communauté) ni de réglage dans ce document.
 */
@Serializable
data class BackupDocument(
    /** Toujours [BackupCodec.FORMAT] : distingue une sauvegarde Roadly d'un autre fichier JSON. Obligatoire à la lecture. */
    val format: String,
    /** Version du format, obligatoire ; un document d'une version plus récente que [BackupCodec.FORMAT_VERSION] est refusé. */
    val formatVersion: Int,
    val exportedAtMs: Long = 0,
    /** Version de Roadly qui a écrit le document (information). */
    val appVersion: String? = null,
    val favorites: List<BackupFavorite> = emptyList(),
    val trips: List<BackupTrip> = emptyList(),
)

/** Un favori, tel qu'il est enregistré (`favorite_places`). */
@Serializable
data class BackupFavorite(
    val id: String,
    val name: String,
    val address: String? = null,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val source: String,
    val createdAt: Long,
    val isHome: Boolean = false,
    val isWork: Boolean = false,
)

/** Un trajet, tel qu'il est enregistré (`trips`), sans son identifiant local ni sa fiche trottinette (propres à l'appareil). */
@Serializable
data class BackupTrip(
    val startedAt: Long,
    val endedAt: Long,
    val destinationName: String,
    val distanceM: Double,
    val durationS: Long,
    val maxSpeedKmh: Double,
    val costEur: Double? = null,
    val arrived: Boolean,
    /** Tracé `t,lat,lon,v;…` ; vide si le trajet a été exporté sans son tracé. */
    val points: String = "",
    val travelMode: String? = null,
    val ascentM: Double? = null,
    val descentM: Double? = null,
    val energyModelWh: Double? = null,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
)

enum class BackupError {
    /** Document ou liste au-delà des tailles acceptées. */
    TOO_LARGE,
    /** JSON illisible, ou qui n'est pas une sauvegarde Roadly. */
    NOT_A_BACKUP,
    /** Sauvegarde écrite par une version plus récente de Roadly. */
    NEWER_FORMAT,
    /** Fichier impossible à lire ou à écrire. */
    IO,
}

sealed interface BackupResult<out T> {
    data class Done<T>(val value: T) : BackupResult<T>
    data class Failed(val error: BackupError) : BackupResult<Nothing>
}

/** Document lu et contrôlé : lignes valides, et nombre de lignes écartées parce qu'invalides. */
data class DecodedBackup(val document: BackupDocument, val rejectedRows: Int)

object BackupCodec {
    const val FORMAT = "roadly-backup"
    const val FORMAT_VERSION = 1

    /** Taille maximale d'un document, à l'écriture comme à la lecture : 32 Mio. */
    const val MAX_DOCUMENT_BYTES = 32 * 1024 * 1024
    const val MAX_FAVORITES = 5_000
    const val MAX_TRIPS = 20_000
    /** Tracé d'un trajet : 2 millions de caractères (au-delà, la base ne sait pas relire la ligne). */
    const val MAX_TRACK_CHARS = 2_000_000
    /** Nom, adresse, destination. */
    const val MAX_TEXT_CHARS = 500
    /** Identifiant, catégorie, source, mode. */
    const val MAX_KEY_CHARS = 200

    /** Document de la version courante du format. */
    fun document(exportedAtMs: Long, appVersion: String?, favorites: List<BackupFavorite>, trips: List<BackupTrip>) =
        BackupDocument(FORMAT, FORMAT_VERSION, exportedAtMs, appVersion, favorites, trips)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    /** Document encodé, ou null s'il dépasse [maxBytes] (ou contient un nombre non représentable). */
    @OptIn(ExperimentalSerializationApi::class)
    fun encode(document: BackupDocument, maxBytes: Int = MAX_DOCUMENT_BYTES): ByteArray? {
        val out = BoundedOutput(maxBytes)
        return try {
            json.encodeToStream(BackupDocument.serializer(), document, out)
            out.toByteArray()
        } catch (e: TooLargeException) {
            null
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
    }

    /** Lit au plus [maxBytes] octets de [input], puis contrôle le document. */
    fun decode(input: InputStream, maxBytes: Int = MAX_DOCUMENT_BYTES): BackupResult<DecodedBackup> {
        val bytes = try { readBounded(input, maxBytes) } catch (e: IOException) { return BackupResult.Failed(BackupError.IO) }
            ?: return BackupResult.Failed(BackupError.TOO_LARGE)
        return decode(bytes)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun decode(bytes: ByteArray): BackupResult<DecodedBackup> {
        if (bytes.size > MAX_DOCUMENT_BYTES) return BackupResult.Failed(BackupError.TOO_LARGE)
        val document = try {
            json.decodeFromStream(BackupDocument.serializer(), ByteArrayInputStream(bytes))
        } catch (e: kotlinx.serialization.SerializationException) {
            return BackupResult.Failed(BackupError.NOT_A_BACKUP)
        } catch (e: IllegalArgumentException) {
            return BackupResult.Failed(BackupError.NOT_A_BACKUP)
        }
        if (document.format != FORMAT || document.formatVersion < 1) return BackupResult.Failed(BackupError.NOT_A_BACKUP)
        if (document.formatVersion > FORMAT_VERSION) return BackupResult.Failed(BackupError.NEWER_FORMAT)
        if (document.favorites.size > MAX_FAVORITES || document.trips.size > MAX_TRIPS) return BackupResult.Failed(BackupError.TOO_LARGE)
        val favorites = document.favorites.filter(::isValid)
        val trips = document.trips.filter(::isValid)
        val rejected = document.favorites.size - favorites.size + document.trips.size - trips.size
        return BackupResult.Done(DecodedBackup(document.copy(favorites = favorites, trips = trips), rejected))
    }

    /** Favori utilisable : champs bornés, coordonnées réelles, catégorie connue de cette version. */
    fun isValid(f: BackupFavorite): Boolean =
        f.id.isNotBlank() && f.id.length <= MAX_KEY_CHARS && f.name.length <= MAX_TEXT_CHARS && (f.address?.length ?: 0) <= MAX_TEXT_CHARS &&
            f.source.length <= MAX_KEY_CHARS && PlaceKind.entries.any { it.name == f.kind } &&
            f.lat.isFinite() && f.lat in -90.0..90.0 && f.lon.isFinite() && f.lon in -180.0..180.0 && f.createdAt >= 0

    /** Trajet utilisable : champs bornés, durées et distances réelles, niveaux de batterie en pourcentage. */
    fun isValid(t: BackupTrip): Boolean =
        t.startedAt >= 0 && t.endedAt >= t.startedAt && t.destinationName.length <= MAX_TEXT_CHARS && t.points.length <= MAX_TRACK_CHARS &&
            t.distanceM.isFinite() && t.distanceM >= 0 && t.durationS >= 0 && t.maxSpeedKmh.isFinite() && t.maxSpeedKmh >= 0 &&
            (t.costEur?.isFinite() ?: true) && (t.travelMode?.length ?: 0) <= MAX_KEY_CHARS &&
            (t.ascentM?.isFinite() ?: true) && (t.descentM?.isFinite() ?: true) && (t.energyModelWh?.isFinite() ?: true) &&
            (t.batteryStartPct?.let { it in 0..100 } ?: true) && (t.batteryEndPct?.let { it in 0..100 } ?: true)

    /** Contenu de [input] s'il tient dans [maxBytes] octets, sinon null ; rien n'est lu au-delà de la limite (à un tampon près). */
    fun readBounded(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private class TooLargeException : IOException("sauvegarde trop volumineuse")

    private class BoundedOutput(private val max: Int) : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        override fun write(b: Int) { if (buffer.size() + 1 > max) throw TooLargeException(); buffer.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { if (buffer.size() + len > max) throw TooLargeException(); buffer.write(b, off, len) }
        fun toByteArray(): ByteArray = buffer.toByteArray()
    }
}
