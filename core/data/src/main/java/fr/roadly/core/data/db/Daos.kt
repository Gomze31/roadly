package fr.roadly.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_places ORDER BY createdAt DESC") fun observeAll(): Flow<List<FavoritePlaceEntity>>
    @Query("SELECT * FROM favorite_places WHERE isHome = 1 LIMIT 1") fun observeHome(): Flow<FavoritePlaceEntity?>
    @Query("SELECT * FROM favorite_places WHERE isWork = 1 LIMIT 1") fun observeWork(): Flow<FavoritePlaceEntity?>
    @Query("SELECT * FROM favorite_places WHERE id = :id") suspend fun byId(id: String): FavoritePlaceEntity?
    @Upsert suspend fun upsert(entity: FavoritePlaceEntity)
    @Query("DELETE FROM favorite_places WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE favorite_places SET isHome = 0") suspend fun clearHome()
    @Query("UPDATE favorite_places SET isWork = 0") suspend fun clearWork()
    @Query("DELETE FROM favorite_places") suspend fun clear()

    @Transaction
    suspend fun setHome(entity: FavoritePlaceEntity) {
        val existing = byId(entity.id)
        clearHome()
        upsert(entity.copy(isHome = true, isWork = existing?.isWork ?: false))
    }

    @Transaction
    suspend fun setWork(entity: FavoritePlaceEntity) {
        val existing = byId(entity.id)
        clearWork()
        upsert(entity.copy(isWork = true, isHome = existing?.isHome ?: false))
    }
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 50") fun observe(): Flow<List<RecentSearchEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RecentSearchEntity)
    @Query("DELETE FROM recent_searches WHERE placeId = :placeId") suspend fun deleteByPlace(placeId: String)
    @Query("DELETE FROM recent_searches WHERE rowId NOT IN (SELECT rowId FROM recent_searches ORDER BY searchedAt DESC LIMIT 50)") suspend fun trim()
    @Query("DELETE FROM recent_searches") suspend fun clear()

    @Transaction
    suspend fun record(entity: RecentSearchEntity) { deleteByPlace(entity.placeId); insert(entity); trim() }
}

@Dao
interface VehicleProfileDao {
    @Query("SELECT * FROM vehicle_profile WHERE id = 1") fun observe(): Flow<VehicleProfileEntity?>
    @Upsert suspend fun upsert(entity: VehicleProfileEntity)
    @Query("DELETE FROM vehicle_profile") suspend fun clear()
}

@Dao
interface VigilanceZoneDao {
    /** Équipements actifs (les retirés restent en base pour l'historique, jamais alertés). */
    @Query("SELECT * FROM vigilance_zones WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east AND (status IS NULL OR status = 'ACTIVE')")
    suspend fun inBox(south: Double, west: Double, north: Double, east: Double): List<VigilanceZoneEntity>
    @Query("SELECT COUNT(*) FROM vigilance_zones WHERE status IS NULL OR status = 'ACTIVE'") fun observeCount(): Flow<Int>
    /** Lignes issues d'une synchronisation (source renseignée), y compris retirées. */
    @Query("SELECT * FROM vigilance_zones WHERE source IS NOT NULL") suspend fun synced(): List<VigilanceZoneEntity>
    @Query("DELETE FROM vigilance_zones WHERE source IS NULL") suspend fun deleteLegacy()
    @Query("UPDATE vigilance_zones SET status = 'REMOVED', updatedAt = :at WHERE id IN (:ids)") suspend fun markRemoved(ids: List<String>, at: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVersion(v: RadarVersionEntity)
    @Query("SELECT * FROM radar_versions ORDER BY fetchedAt DESC LIMIT :limit") fun observeVersions(limit: Int): Flow<List<RadarVersionEntity>>
    @Query("SELECT * FROM radar_versions WHERE status IN ('APPLIED', 'UNCHANGED') ORDER BY fetchedAt DESC LIMIT 1") suspend fun lastSuccess(): RadarVersionEntity?
    @Query("SELECT * FROM radar_versions WHERE status = 'APPLIED' ORDER BY fetchedAt DESC LIMIT 1") suspend fun lastApplied(): RadarVersionEntity?
    @Query("DELETE FROM radar_versions WHERE id NOT IN (SELECT id FROM radar_versions ORDER BY fetchedAt DESC LIMIT :keep)") suspend fun pruneVersions(keep: Int)

    /** Nouvelle version : lignes héritées supprimées, équipements écrits, disparus marqués retirés, journal mis à jour — atomique. */
    @Transaction
    suspend fun applyVersion(rows: List<VigilanceZoneEntity>, removedIds: List<String>, at: Long, version: RadarVersionEntity) {
        deleteLegacy()
        rows.chunked(500).forEach { insertAll(it) }
        removedIds.chunked(500).forEach { markRemoved(it, at) }
        insertVersion(version)
        pruneVersions(30)
    }

    /** Journal seul (synchronisation inchangée, rejetée ou en échec). */
    @Transaction
    suspend fun logVersion(version: RadarVersionEntity) { insertVersion(version); pruneVersions(30) }
    @Query("SELECT MAX(updatedAt) FROM vigilance_zones") fun observeUpdatedAt(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<VigilanceZoneEntity>)
    @Query("DELETE FROM vigilance_zones") suspend fun clear()

    @Transaction
    suspend fun replaceAll(rows: List<VigilanceZoneEntity>) { clear(); rows.chunked(500).forEach { insertAll(it) } }
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startedAt DESC") fun observeAll(): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips WHERE id = :id") suspend fun byId(id: Long): TripEntity?
    @Insert suspend fun insert(trip: TripEntity): Long
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM trips") suspend fun clear()
    @Query("UPDATE trips SET batteryEndPct = :pct WHERE id = :id") suspend fun setBatteryEnd(id: Long, pct: Int?)
}

@Dao
interface ScooterProfileDao {
    @Query("SELECT * FROM scooter_profiles ORDER BY id") fun observeAll(): Flow<List<ScooterProfileEntity>>
    @Query("SELECT COUNT(*) FROM scooter_profiles") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entity: ScooterProfileEntity): Long
    @Query("DELETE FROM scooter_profiles WHERE id = :id") suspend fun delete(id: Long)
}
