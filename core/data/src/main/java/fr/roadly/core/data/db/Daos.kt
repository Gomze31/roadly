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
    @Query("SELECT * FROM vigilance_zones WHERE lat BETWEEN :south AND :north AND lon BETWEEN :west AND :east")
    suspend fun inBox(south: Double, west: Double, north: Double, east: Double): List<VigilanceZoneEntity>
    @Query("SELECT COUNT(*) FROM vigilance_zones") fun observeCount(): Flow<Int>
    @Query("SELECT MAX(updatedAt) FROM vigilance_zones") fun observeUpdatedAt(): Flow<Long?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<VigilanceZoneEntity>)
    @Query("DELETE FROM vigilance_zones") suspend fun clear()

    @Transaction
    suspend fun replaceAll(rows: List<VigilanceZoneEntity>) { clear(); rows.chunked(500).forEach { insertAll(it) } }
}
