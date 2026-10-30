package fr.roadly.core.routing

import fr.roadly.core.model.GeoPoint

/** Décodage polyline (algorithme Google) ; Valhalla encode avec une précision 1e6. */
object Polyline6 {
    fun decode(encoded: String, precision: Double = 1e6): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(encoded.length / 4 + 1)
        var index = 0; var lat = 0; var lon = 0
        while (index < encoded.length) {
            var result = 0; var shift = 0; var b: Int
            do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            result = 0; shift = 0
            do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out += GeoPoint(lat / precision, lon / precision)
        }
        return out
    }
}
