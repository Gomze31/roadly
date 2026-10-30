package fr.roadly.core.data.vigilance

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.db.VigilanceZoneDao
import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.BoundingBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Jeu de données officiel : « Radars automatiques », Sécurité routière, data.gouv.fr (Licence Ouverte 2.0). */
object VigilanceSource {
    const val CSV_URL = "https://static.data.gouv.fr/resources/radars-automatiques/20181025-141231/radars.csv"
    const val DATASET_URL = "https://www.data.gouv.fr/fr/datasets/radars-automatiques/"
    /** Millésime du fichier publié (le fichier n'est plus mis à jour par l'éditeur depuis cette date). */
    const val DATASET_VINTAGE = "octobre 2018"
    /** Rafraîchissement automatique au-delà de 30 jours. */
    const val STALE_AFTER_MS = 30L * 24 * 3600 * 1000
}

interface VigilanceRepository {
    val count: Flow<Int>
    val updatedAtMs: Flow<Long?>
    suspend fun zonesIn(box: BoundingBox): List<VigilanceZoneEntity>
    /** Télécharge et remplace la base locale ; `Success(n)` = nombre de zones chargées. */
    suspend fun refresh(): Outcome<Int>
    /** Rafraîchit seulement si la base est vide ou plus vieille que [VigilanceSource.STALE_AFTER_MS]. */
    suspend fun refreshIfStale(nowMs: Long): Outcome<Int>?
}

@Singleton
class RoomVigilanceRepository @Inject constructor(
    private val dao: VigilanceZoneDao,
    private val api: ApiClient,
) : VigilanceRepository {
    override val count: Flow<Int> = dao.observeCount()
    override val updatedAtMs: Flow<Long?> = dao.observeUpdatedAt()

    override suspend fun zonesIn(box: BoundingBox): List<VigilanceZoneEntity> = dao.inBox(box.south, box.west, box.north, box.east)

    override suspend fun refresh(): Outcome<Int> {
        val url = VigilanceSource.CSV_URL.toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("vigilance"))
        return when (val r = api.get(url)) {
            is Outcome.Failure -> r
            is Outcome.Success -> {
                val rows = VigilanceCsv.parse(r.value, System.currentTimeMillis())
                if (rows.isEmpty()) return Outcome.Failure(AppError.Parse("vigilance"))
                dao.replaceAll(rows)
                Outcome.Success(rows.size)
            }
        }
    }

    override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? {
        val updated = dao.observeUpdatedAt().first()
        val stale = updated == null || nowMs - updated > VigilanceSource.STALE_AFTER_MS
        return if (stale) refresh() else null
    }
}
