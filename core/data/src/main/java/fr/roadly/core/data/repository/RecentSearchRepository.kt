package fr.roadly.core.data.repository

import fr.roadly.core.data.db.RecentSearchDao
import fr.roadly.core.data.db.RecentSearchEntity
import fr.roadly.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface RecentSearchRepository {
    fun observe(): Flow<List<Place>>
    suspend fun record(place: Place)
    suspend fun clear()
}

@Singleton
class RoomRecentSearchRepository @Inject constructor(private val dao: RecentSearchDao, private val json: Json) : RecentSearchRepository {
    override fun observe() = dao.observe().map { list -> list.mapNotNull { runCatching { json.decodeFromString(Place.serializer(), it.placeJson) }.getOrNull() } }
    override suspend fun record(place: Place) = dao.record(
        RecentSearchEntity(placeId = place.id, placeJson = json.encodeToString(Place.serializer(), place), searchedAt = System.currentTimeMillis()),
    )
    override suspend fun clear() = dao.clear()
}
