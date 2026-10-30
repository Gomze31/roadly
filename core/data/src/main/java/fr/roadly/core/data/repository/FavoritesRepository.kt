package fr.roadly.core.data.repository

import fr.roadly.core.data.db.FavoriteDao
import fr.roadly.core.data.db.FavoritePlaceEntity
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface FavoritesRepository {
    fun observeAll(): Flow<List<Place>>
    fun observeHome(): Flow<Place?>
    fun observeWork(): Flow<Place?>
    suspend fun isFavorite(id: String): Boolean
    suspend fun toggle(place: Place)
    suspend fun setHome(place: Place)
    suspend fun setWork(place: Place)
    suspend fun clear()
}

@Singleton
class RoomFavoritesRepository @Inject constructor(private val dao: FavoriteDao) : FavoritesRepository {
    override fun observeAll() = dao.observeAll().map { list -> list.map { it.toPlace() } }
    override fun observeHome() = dao.observeHome().map { it?.toPlace() }
    override fun observeWork() = dao.observeWork().map { it?.toPlace() }
    override suspend fun isFavorite(id: String) = dao.byId(id) != null
    override suspend fun toggle(place: Place) {
        val existing = dao.byId(place.id)
        when {
            existing == null -> dao.upsert(place.toEntity())
            !existing.isHome && !existing.isWork -> dao.delete(place.id)
            else -> Unit // domicile/travail : on ne retire pas via le favori
        }
    }
    override suspend fun setHome(place: Place) = dao.setHome(dao.byId(place.id) ?: place.toEntity())
    override suspend fun setWork(place: Place) = dao.setWork(dao.byId(place.id) ?: place.toEntity())
    override suspend fun clear() = dao.clear()
}

internal fun FavoritePlaceEntity.toPlace() = Place(id, name, address, GeoPoint(lat, lon), PlaceKind.valueOf(kind), source)
internal fun Place.toEntity() = FavoritePlaceEntity(id, name, address, point.lat, point.lon, kind.name, source, System.currentTimeMillis())
