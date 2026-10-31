package fr.roadly.core.navigation

import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripPoint
import fr.roadly.core.routing.FuelCostCalculator
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Enregistreur de trajet piloté par la course : début, fixes, fin (arrivée ou arrêt). */
interface TripRecorder {
    fun begin(destinationName: String, startedAtMs: Long)
    fun onFix(fix: LocationFix, nowMs: Long)
    /** Clôt le trajet en cours ; ignoré s'il est trop court ([MIN_DISTANCE_M]) ou s'il n'y a pas de trajet. */
    suspend fun end(arrived: Boolean, nowMs: Long)

    companion object { const val MIN_DISTANCE_M = 200.0; const val SAMPLE_M = 20.0 }
}

class NoopTripRecorder @Inject constructor() : TripRecorder {
    override fun begin(destinationName: String, startedAtMs: Long) = Unit
    override fun onFix(fix: LocationFix, nowMs: Long) = Unit
    override suspend fun end(arrived: Boolean, nowMs: Long) = Unit
}

/** Stockage local uniquement (Room) ; points échantillonnés tous les ≥ 20 m ; coût d'après le profil véhicule du moment. */
@Singleton
class RoomTripRecorder @Inject constructor(
    private val trips: TripRepository,
    private val vehicles: VehicleProfileRepository,
) : TripRecorder {
    private var destination: String? = null
    private var startedAt = 0L
    private val points = ArrayList<TripPoint>()
    private var distanceM = 0.0
    private var maxSpeed = 0.0
    private var last: GeoPoint? = null

    @Synchronized override fun begin(destinationName: String, startedAtMs: Long) {
        destination = destinationName; startedAt = startedAtMs
        points.clear(); distanceM = 0.0; maxSpeed = 0.0; last = null
    }

    @Synchronized override fun onFix(fix: LocationFix, nowMs: Long) {
        if (destination == null) return
        val prev = last
        val d = prev?.distanceTo(fix.point) ?: Double.MAX_VALUE
        if (prev != null && d < TripRecorder.SAMPLE_M) return
        if (prev != null) distanceM += d
        last = fix.point
        val v = fix.speedMps?.let { it * 3.6 }
        if (v != null && v > maxSpeed) maxSpeed = v
        points += TripPoint(nowMs - startedAt, fix.point, v)
    }

    override suspend fun end(arrived: Boolean, nowMs: Long) {
        val trip = synchronized(this) {
            val dest = destination ?: return
            destination = null
            if (distanceM < TripRecorder.MIN_DISTANCE_M) return
            val cost = runCatching { vehicles.profile }.getOrNull()
            Trip(0, startedAt, nowMs, dest, distanceM, (nowMs - startedAt) / 1000, maxSpeed, null, arrived, points.toList()) to cost
        }
        val profile = trip.second?.first()
        val cost = profile?.let { FuelCostCalculator.estimate(trip.first.distanceM, it)?.fuelEur }
        trips.save(trip.first.copy(costEur = cost))
    }
}
