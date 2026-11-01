package fr.roadly.core.scooter.geo

import fr.roadly.core.model.GeoPoint
import kotlin.math.roundToLong

/** Encodage polyline précision 6 (format Valhalla), pour envoyer un tracé à `/trace_attributes`. */
object Polyline6Encoder {
    fun encode(points: List<GeoPoint>): String {
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.lat * 1e6).roundToLong()
            val lon = (p.lon * 1e6).roundToLong()
            encodeValue(lat - lastLat, sb)
            encodeValue(lon - lastLon, sb)
            lastLat = lat; lastLon = lon
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }
}

/** Abscisses curvilignes d'une polyligne : distance depuis le départ de chaque sommet, et sommet → abscisse. */
class RouteLine(val points: List<GeoPoint>) {
    val cumulative: DoubleArray = DoubleArray(points.size).also { arr ->
        for (i in 1 until points.size) arr[i] = arr[i - 1] + points[i - 1].distanceTo(points[i])
    }
    val lengthM: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    fun alongAt(index: Int): Double = if (cumulative.isEmpty()) 0.0 else cumulative[index.coerceIn(0, cumulative.lastIndex)]

    /** Points de la polyligne entre deux abscisses (bornes interpolées), pour dessiner un segment. */
    fun slice(fromM: Double, toM: Double): List<GeoPoint> {
        if (points.size < 2 || toM <= fromM) return emptyList()
        val out = ArrayList<GeoPoint>()
        out += pointAt(fromM)
        for (i in points.indices) if (cumulative[i] > fromM && cumulative[i] < toM) out += points[i]
        out += pointAt(toM)
        return out
    }

    fun pointAt(alongM: Double): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        val d = alongM.coerceIn(0.0, lengthM)
        var i = cumulative.indexOfFirst { it >= d }
        if (i <= 0) return points.first()
        val a = points[i - 1]; val b = points[i]
        val seg = cumulative[i] - cumulative[i - 1]
        val t = if (seg > 0) (d - cumulative[i - 1]) / seg else 0.0
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }
}
