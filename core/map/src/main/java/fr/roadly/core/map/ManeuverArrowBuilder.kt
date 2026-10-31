package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flèche de manœuvre dessinée sur le tracé à l'approche d'une intersection (spec guidage 3D §3) :
 * [BEFORE_M] en amont du point de manœuvre, [AFTER_M] en aval, bornée aux extrémités du tracé.
 * GeoJSON : LineString `kind=arrow` puis Point `kind=head` avec le cap du dernier segment.
 */
object ManeuverArrowBuilder {
    const val BEFORE_M = 80.0
    const val AFTER_M = 40.0
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun build(geometry: List<GeoPoint>, maneuverShapeIndex: Int): String {
        if (geometry.size < 2 || maneuverShapeIndex !in geometry.indices) return EMPTY
        val before = walk(geometry, maneuverShapeIndex, -1, BEFORE_M).asReversed()
        val after = walk(geometry, maneuverShapeIndex, +1, AFTER_M)
        val pts = (before + geometry[maneuverShapeIndex] + after).distinct()
        if (pts.size < 2) return EMPTY
        val bearing = bearingDeg(pts[pts.size - 2], pts.last())
        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("kind", "arrow") }
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        put("coordinates", JsonArray(pts.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) }))
                    }
                })
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("kind", "head"); put("bearing", bearing) }
                    putJsonObject("geometry") {
                        put("type", "Point")
                        put("coordinates", JsonArray(listOf(JsonPrimitive(pts.last().lon), JsonPrimitive(pts.last().lat))))
                    }
                })
            }
        }.toString()
    }

    /** Points rencontrés en partant de [from] dans le sens [step] (±1) jusqu'à [budgetM] ; le dernier est interpolé. */
    private fun walk(geometry: List<GeoPoint>, from: Int, step: Int, budgetM: Double): List<GeoPoint> {
        val out = mutableListOf<GeoPoint>()
        var remaining = budgetM
        var i = from
        while (remaining > 0 && i + step in geometry.indices) {
            val a = geometry[i]; val b = geometry[i + step]
            val d = a.distanceTo(b)
            if (d <= remaining) { out += b; remaining -= d; i += step } else {
                val t = remaining / d
                out += GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                remaining = 0.0
            }
        }
        return out
    }

    /** Cap initial de a vers b en degrés (0 = nord, 90 = est). */
    internal fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat); val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
