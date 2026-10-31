package fr.roadly.core.data.repository

import fr.roadly.core.data.db.TripDao
import fr.roadly.core.data.db.TripEntity
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripPoint
import fr.roadly.core.model.TripStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface TripRepository {
    val trips: Flow<List<Trip>>
    suspend fun byId(id: Long): Trip?
    suspend fun save(trip: Trip): Long
    suspend fun delete(id: Long)
    suspend fun clear()
}

@Singleton
class RoomTripRepository @Inject constructor(private val dao: TripDao) : TripRepository {
    override val trips: Flow<List<Trip>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    override suspend fun byId(id: Long): Trip? = dao.byId(id)?.toModel()
    override suspend fun save(trip: Trip): Long = dao.insert(trip.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun clear() = dao.clear()
}

/** Points sérialisés en `t,lat,lon,v;…` : compact, lisible, sans dépendance JSON dans la base. */
object TripPointsCodec {
    fun encode(points: List<TripPoint>): String = points.joinToString(";") { p ->
        String.format(Locale.ROOT, "%d,%.5f,%.5f,%s", p.tMs, p.point.lat, p.point.lon, p.speedKmh?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "")
    }

    fun decode(csv: String): List<TripPoint> = csv.split(';').mapNotNull { row ->
        val c = row.split(',')
        if (c.size < 3) return@mapNotNull null
        val t = c[0].toLongOrNull() ?: return@mapNotNull null
        val lat = c[1].toDoubleOrNull() ?: return@mapNotNull null
        val lon = c[2].toDoubleOrNull() ?: return@mapNotNull null
        TripPoint(t, GeoPoint(lat, lon), c.getOrNull(3)?.toDoubleOrNull())
    }
}

/** Totaux des trajets commencés après [sinceEpochMs]. */
fun List<Trip>.statsSince(sinceEpochMs: Long): TripStats {
    val sel = filter { it.startedAtEpochMs >= sinceEpochMs }
    return TripStats(sel.size, sel.sumOf { it.distanceM }, sel.sumOf { it.durationS }, sel.sumOf { it.costEur ?: 0.0 })
}

internal fun TripEntity.toModel() = Trip(id, startedAt, endedAt, destinationName, distanceM, durationS, maxSpeedKmh, costEur, arrived, TripPointsCodec.decode(pointsCsv))
internal fun Trip.toEntity() = TripEntity(id, startedAtEpochMs, endedAtEpochMs, destinationName, distanceM, durationS, maxSpeedKmh, costEur, arrived, TripPointsCodec.encode(points))
