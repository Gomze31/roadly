package fr.roadly.core.model

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class GeoPoint(val lat: Double, val lon: Double) {
    /** Distance orthodromique en mètres (formule de haversine). */
    fun distanceTo(other: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

@Serializable
data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    companion object {
        fun of(points: List<GeoPoint>): BoundingBox? {
            if (points.isEmpty()) return null
            return BoundingBox(
                south = points.minOf { it.lat }, west = points.minOf { it.lon },
                north = points.maxOf { it.lat }, east = points.maxOf { it.lon },
            )
        }
    }
}
