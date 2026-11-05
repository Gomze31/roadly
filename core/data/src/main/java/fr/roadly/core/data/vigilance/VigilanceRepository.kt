package fr.roadly.core.data.vigilance

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.db.RadarVersionEntity
import fr.roadly.core.data.db.VigilanceZoneDao
import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.radar.Radar
import fr.roadly.core.radar.RadarDiff
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Jeu historique (2018) : conservé comme source d'enrichissement et pour l'affichage de l'ancienne base. */
object VigilanceSource {
    const val CSV_URL = RadarSourceUrls.ENRICH_2018_CSV
    const val DATASET_URL = RadarSourceUrls.FIXED_DATASET_PAGE
    /** Vérification de la source (requête légère à l'API data.gouv.fr) au plus une fois par semaine ; jeu publié chaque année. */
    const val STALE_AFTER_MS = 7L * 24 * 3600 * 1000
}

/** Base radar locale : toutes les alertes se calculent hors connexion à partir de cette base. */
interface VigilanceRepository {
    val count: Flow<Int>
    val updatedAtMs: Flow<Long?>
    suspend fun zonesIn(box: BoundingBox): List<VigilanceZoneEntity>
    /** Télécharge (si la source a changé ou si [VigilanceRepository.refresh] est forcé) ; `Success(n)` = équipements actifs. */
    suspend fun refresh(): Outcome<Int>
    /** Vérifie la source si la dernière vérification réussie date de plus de [VigilanceSource.STALE_AFTER_MS]. */
    suspend fun refreshIfStale(nowMs: Long): Outcome<Int>?

    /** Équipements actifs dans la boîte, en modèle radar (provenance, sens, confiance). */
    suspend fun radarsIn(box: BoundingBox): List<Radar> = zonesIn(box).map(RadarMapping::toRadar)
    /** Journal des synchronisations, du plus récent au plus ancien. */
    val versions: Flow<List<RadarVersion>> get() = flowOf(emptyList())
}

@Singleton
class RoomVigilanceRepository @Inject constructor(
    private val dao: VigilanceZoneDao,
    private val api: ApiClient,
) : VigilanceRepository {
    override val count: Flow<Int> = dao.observeCount()
    override val updatedAtMs: Flow<Long?> = dao.observeUpdatedAt()
    override val versions: Flow<List<RadarVersion>> = dao.observeVersions(10).map { list -> list.map { it.toModel() } }
    private val mutex = Mutex()

    private val store = object : RadarStore {
        override suspend fun synced(): List<Radar> = dao.synced().map(RadarMapping::toRadar)
        override suspend fun lastSuccess(): RadarVersion? = dao.lastSuccess()?.toModel()
        override suspend fun apply(radars: List<Radar>, removedIds: List<String>, version: RadarVersion) =
            dao.applyVersion(radars.map(RadarMapping::toEntity), removedIds, version.fetchedAtMs, version.toEntity())
        override suspend fun log(version: RadarVersion) = dao.logVersion(version.toEntity())
    }

    private val sync = RadarSync(
        fetch = { url -> url.toHttpUrlOrNull()?.let { api.get(it) } ?: Outcome.Failure(AppError.ProviderUnavailable("radars")) },
        store = store,
    )

    override suspend fun zonesIn(box: BoundingBox): List<VigilanceZoneEntity> = dao.inBox(box.south, box.west, box.north, box.east)

    override suspend fun refresh(): Outcome<Int> = mutex.withLock { sync.sync(force = true).map() }

    override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? = mutex.withLock {
        val last = dao.lastSuccess()
        if (last != null && nowMs - last.fetchedAt <= VigilanceSource.STALE_AFTER_MS) null else sync.sync(force = false).map()
    }

    private fun Outcome<RadarVersion>.map(): Outcome<Int> = when (this) {
        is Outcome.Success -> Outcome.Success(value.total)
        is Outcome.Failure -> this
    }
}

internal fun RadarVersionEntity.toModel() = RadarVersion(
    fetchedAt, runCatching { RadarSyncStatus.valueOf(status) }.getOrDefault(RadarSyncStatus.FAILED), sourceUrl, sourceModified, total,
    RadarDiff(added, removed, moved, limitChanged), enriched, withDirection, highPrecision, rejected, message, enrichment = enrichment,
)

internal fun RadarVersion.toEntity() = RadarVersionEntity(
    fetchedAt = fetchedAtMs, status = status.name, sourceUrl = sourceUrl, sourceModified = sourceModified, total = total,
    added = diff.added, removed = diff.removed, moved = diff.moved, limitChanged = diff.limitChanged, enriched = enriched,
    withDirection = withDirection, highPrecision = highPrecision, rejected = rejected, message = message, enrichment = enrichment,
)
