package fr.roadly.core.data.backup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.common.update.AppVersion
import fr.roadly.core.data.db.FavoriteDao
import fr.roadly.core.data.db.FavoritePlaceEntity
import fr.roadly.core.data.db.TripDao
import fr.roadly.core.data.db.TripEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Singleton

/** Bilan d'un export : [tripsWithoutTrack] trajets écrits sans leur tracé (place), [omitted] lignes illisibles non écrites. */
data class ExportSummary(val favorites: Int, val trips: Int, val tripsWithoutTrack: Int = 0, val omitted: Int = 0)

/** Bilan d'un import : favoris ajoutés ou mis à jour, trajets ajoutés, trajets déjà présents, lignes invalides écartées. */
data class ImportSummary(val favorites: Int, val tripsAdded: Int, val tripsAlreadyPresent: Int, val rejectedRows: Int)

/** Export et import des favoris et des trajets dans un fichier choisi par l'utilisateur. */
interface BackupRepository {
    /** Écrit la sauvegarde dans le flux ouvert par [open] (null = fichier inaccessible). Rien n'est écrit en cas d'échec. */
    suspend fun export(open: () -> OutputStream?): BackupResult<ExportSummary>

    /**
     * Lit la sauvegarde du flux ouvert par [open] et l'ajoute aux données de l'appareil : rien n'est supprimé, un favori de même
     * identifiant est mis à jour, un trajet déjà présent n'est pas dupliqué. Un fichier refusé ne modifie rien.
     */
    suspend fun import(open: () -> InputStream?): BackupResult<ImportSummary>
}

class RoomBackupRepository(
    private val favorites: FavoriteDao,
    private val trips: TripDao,
    private val appVersion: String?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** Place accordée aux tracés : au-delà, les trajets les plus anciens sont écrits sans leur tracé. */
    private val trackBudgetChars: Long = TRACK_BUDGET_CHARS,
) : BackupRepository {
    companion object {
        /** 24 Mio de tracés au plus, pour que le document reste sous [BackupCodec.MAX_DOCUMENT_BYTES]. */
        const val TRACK_BUDGET_CHARS = 24L * 1024 * 1024
    }

    // Une erreur inattendue de la base (lecture ou écriture) est rendue comme un échec, jamais propagée à l'écran.
    override suspend fun export(open: () -> OutputStream?): BackupResult<ExportSummary> =
        try { doExport(open) } catch (e: CancellationException) { throw e } catch (e: Exception) { BackupResult.Failed(BackupError.IO) }

    override suspend fun import(open: () -> InputStream?): BackupResult<ImportSummary> =
        try { doImport(open) } catch (e: CancellationException) { throw e } catch (e: Exception) { BackupResult.Failed(BackupError.IO) }

    private suspend fun doExport(open: () -> OutputStream?): BackupResult<ExportSummary> {
        val storedFavorites = favorites.all()
        val favoriteRows = storedFavorites.map { it.toBackup() }.filter(BackupCodec::isValid)
        var omitted = storedFavorites.size - favoriteRows.size
        var withoutTrack = 0
        var budget = trackBudgetChars
        val tripRows = mutableListOf<BackupTrip>()
        // Du plus récent au plus ancien : si la place manque, ce sont les tracés les plus anciens qui sont laissés de côté.
        for (summary in trips.observeSummaries().first()) {
            // Tracé lu trajet par trajet ; une ligne illisible (tracé hors norme) est exportée sans lui plutôt que perdue.
            val full = try { trips.byId(summary.id) ?: continue } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            val track = full?.pointsCsv?.takeIf { it.length <= BackupCodec.MAX_TRACK_CHARS && it.length <= budget }
            if (track == null) withoutTrack++ else budget -= track.length
            val row = (full ?: summary).toBackup(track.orEmpty())
            if (BackupCodec.isValid(row)) tripRows += row else omitted++
        }
        val document = BackupCodec.document(nowMs(), appVersion, favoriteRows, tripRows)
        val bytes = BackupCodec.encode(document) ?: return BackupResult.Failed(BackupError.TOO_LARGE)
        return withContext(io) {
            try {
                val out = open() ?: return@withContext BackupResult.Failed(BackupError.IO)
                out.use { it.write(bytes); it.flush() }
                BackupResult.Done(ExportSummary(favoriteRows.size, tripRows.size, withoutTrack, omitted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupResult.Failed(BackupError.IO)
            }
        }
    }

    private suspend fun doImport(open: () -> InputStream?): BackupResult<ImportSummary> {
        val decoded = withContext(io) {
            try {
                val input = open() ?: return@withContext BackupResult.Failed(BackupError.IO)
                input.use { BackupCodec.decode(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupResult.Failed(BackupError.IO)
            }
        }
        val backup = when (decoded) {
            is BackupResult.Done -> decoded.value
            is BackupResult.Failed -> return decoded
        }
        favorites.importAll(backup.document.favorites.map { it.toEntity() })
        val added = trips.importAll(backup.document.trips.map { it.toEntity() })
        return BackupResult.Done(ImportSummary(backup.document.favorites.size, added, backup.document.trips.size - added, backup.rejectedRows))
    }
}

internal fun FavoritePlaceEntity.toBackup() = BackupFavorite(id, name, address, lat, lon, kind, source, createdAt, isHome, isWork)
internal fun BackupFavorite.toEntity() = FavoritePlaceEntity(id, name, address, lat, lon, kind, source, createdAt, isHome, isWork)

internal fun TripEntity.toBackup(track: String) = BackupTrip(
    startedAt, endedAt, destinationName, distanceM, durationS, maxSpeedKmh, costEur, arrived, track,
    travelMode, ascentM, descentM, energyModelWh, batteryStartPct, batteryEndPct,
)

/** La fiche trottinette n'est pas sauvegardée : son identifiant ne désignerait plus rien (ou une autre fiche) après réinstallation. */
internal fun BackupTrip.toEntity() = TripEntity(
    startedAt = startedAt, endedAt = endedAt, destinationName = destinationName, distanceM = distanceM, durationS = durationS, maxSpeedKmh = maxSpeedKmh,
    costEur = costEur, arrived = arrived, pointsCsv = points, travelMode = travelMode, ascentM = ascentM, descentM = descentM,
    energyModelWh = energyModelWh, batteryStartPct = batteryStartPct, batteryEndPct = batteryEndPct, scooterProfileId = null,
)

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides @Singleton
    fun backupRepository(favorites: FavoriteDao, trips: TripDao, version: AppVersion): BackupRepository = RoomBackupRepository(favorites, trips, version.name)
}
