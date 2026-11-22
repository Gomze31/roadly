package fr.roadly.core.navigation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.TripPoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import javax.inject.Inject

/** Trajet en cours tel qu'il est sauvegardé en roulant : de quoi en faire un trajet enregistré si le processus meurt (NAV-1). */
data class TripSnapshot(val destinationName: String, val startedAtMs: Long, val context: TripContext, val points: List<TripPoint>)

/**
 * Sauvegarde du trajet en cours hors de la mémoire du processus (NAV-1). Une seule sauvegarde à la fois : celle du trajet en cours.
 * Aucune méthode ne lève d'exception : une sauvegarde impossible ne doit jamais toucher au guidage.
 */
interface TripCheckpointStore {
    fun write(snapshot: TripSnapshot)
    /** Rend la sauvegarde présente et l'efface ; null s'il n'y en a pas ou si elle est illisible (elle est alors effacée aussi). */
    fun take(): TripSnapshot?
    fun delete()
}

/** Pas de sauvegarde (tests, enregistreurs construits à la main). */
object NoTripCheckpointStore : TripCheckpointStore {
    override fun write(snapshot: TripSnapshot) = Unit
    override fun take(): TripSnapshot? = null
    override fun delete() = Unit
}

/**
 * Sauvegarde dans un petit fichier du dossier privé de l'application (pas dans la base : son schéma ne change pas).
 * - Écriture atomique : fichier temporaire puis renommage ; un lecteur voit l'ancienne sauvegarde entière ou la nouvelle entière.
 * - Format binaire : en-tête, longueur, contenu, somme de contrôle (CRC-32). Un fichier tronqué, d'une autre version ou abîmé est
 *   refusé à la lecture et effacé.
 * - Pas de `fsync` : le cas visé est la mort du processus (les données écrites restent dans le cache du système), pas la coupure
 *   d'alimentation, qu'un fichier incomplet détecté par la somme de contrôle suffit à rendre inoffensive.
 * Taille : 32 octets par point (un point tous les 20 m au moins), soit environ 160 ko pour 100 km.
 */
class FileTripCheckpointStore(private val dir: File) : TripCheckpointStore {
    @Inject constructor(@ApplicationContext ctx: Context) : this(ctx.filesDir)

    private val file get() = File(dir, FILE_NAME)
    private val temp get() = File(dir, "$FILE_NAME.tmp")

    @Synchronized override fun write(snapshot: TripSnapshot) {
        try {
            dir.mkdirs()
            temp.writeBytes(encode(snapshot))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            // Stockage plein ou indisponible : la sauvegarde précédente (si elle existe) reste en place.
            runCatching { temp.delete() }
        }
    }

    @Synchronized override fun take(): TripSnapshot? {
        val snapshot = try {
            if (file.isFile && file.length() in 1..MAX_FILE_BYTES) decode(file.readBytes()) else null
        } catch (e: Exception) {
            null
        }
        delete()
        return snapshot
    }

    @Synchronized override fun delete() {
        runCatching { file.delete() }
        runCatching { temp.delete() }
    }

    internal companion object {
        const val FILE_NAME = "trip-checkpoint.bin"
        private const val MAGIC = 0x52544350 // « RTCP »
        private const val VERSION = 1
        private const val POINT_BYTES = 32
        private const val MAX_NAME_CHARS = 200
        /** Au-delà (≈ 6 000 km de trajet), le fichier n'est pas une sauvegarde de Roadly : il n'est pas chargé en mémoire. */
        private const val MAX_FILE_BYTES = 10L * 1024 * 1024

        fun encode(s: TripSnapshot): ByteArray {
            val body = ByteArrayOutputStream(64 + s.points.size * POINT_BYTES)
            DataOutputStream(body).use { o ->
                o.writeUTF(s.destinationName.take(MAX_NAME_CHARS))
                o.writeLong(s.startedAtMs)
                o.writeUTF(s.context.travelMode.name)
                o.writeLong(s.context.scooterProfileId ?: NONE)
                o.writeInt(s.context.batteryStartPct ?: NONE.toInt())
                o.writeInt(s.points.size)
                for (p in s.points) {
                    o.writeLong(p.tMs); o.writeDouble(p.point.lat); o.writeDouble(p.point.lon)
                    o.writeDouble(p.speedKmh ?: Double.NaN)
                }
            }
            val payload = body.toByteArray()
            val out = ByteArrayOutputStream(payload.size + 20)
            DataOutputStream(out).use { o ->
                o.writeInt(MAGIC); o.writeInt(VERSION); o.writeInt(payload.size)
                o.write(payload)
                o.writeLong(CRC32().apply { update(payload) }.value)
            }
            return out.toByteArray()
        }

        /** Lève une exception si [bytes] n'est pas une sauvegarde entière et intacte de cette version. */
        fun decode(bytes: ByteArray): TripSnapshot {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val size = input.readInt()
            require(size >= 0 && size == bytes.size - 20)
            val payload = ByteArray(size).also { input.readFully(it) }
            require(input.readLong() == CRC32().apply { update(payload) }.value)
            val i = DataInputStream(ByteArrayInputStream(payload))
            val name = i.readUTF()
            val startedAt = i.readLong()
            val mode = TravelMode.valueOf(i.readUTF())
            val profile = i.readLong().takeIf { it != NONE }
            val battery = i.readInt().takeIf { it != NONE.toInt() }
            val count = i.readInt()
            require(count >= 0 && count.toLong() * POINT_BYTES == i.available().toLong())
            val points = ArrayList<TripPoint>(count)
            repeat(count) {
                val t = i.readLong(); val lat = i.readDouble(); val lon = i.readDouble(); val v = i.readDouble()
                require(lat in -90.0..90.0 && lon in -180.0..180.0)
                points += TripPoint(t, GeoPoint(lat, lon), if (v.isNaN()) null else v)
            }
            return TripSnapshot(name, startedAt, TripContext(mode, profile, battery), points)
        }

        private const val NONE = -1L
    }
}
