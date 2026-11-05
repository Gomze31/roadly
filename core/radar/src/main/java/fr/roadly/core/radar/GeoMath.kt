package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** Géométrie locale (projection équirectangulaire, erreur < 0,5 % sous 50 km) et index spatial en grille. */
object GeoMath {
    const val M_PER_DEG_LAT = 111_320.0

    /** Cap (°, 0 = nord, sens horaire) de [from] vers [to]. */
    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val k = cos(Math.toRadians(from.lat))
        return (Math.toDegrees(atan2((to.lon - from.lon) * k, to.lat - from.lat)) + 360.0) % 360.0
    }

    /** Écart angulaire absolu 0..180. */
    fun angleDiff(a: Double, b: Double): Double = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)

    /** Position de [p] dans le repère de [origin] orienté selon [headingDeg] : (en avant, latéral à droite) en mètres. */
    fun alongCross(origin: GeoPoint, headingDeg: Double, p: GeoPoint): Pair<Double, Double> {
        val k = cos(Math.toRadians(origin.lat))
        val x = (p.lon - origin.lon) * k * M_PER_DEG_LAT
        val y = (p.lat - origin.lat) * M_PER_DEG_LAT
        val h = Math.toRadians(headingDeg)
        val along = x * kotlin.math.sin(h) + y * cos(h)
        val cross = x * cos(h) - y * kotlin.math.sin(h)
        return along to cross
    }

    /** Point à [meters] de [from] selon le cap [headingDeg]. */
    fun offset(from: GeoPoint, headingDeg: Double, meters: Double): GeoPoint {
        val h = Math.toRadians(headingDeg)
        val dLat = meters * cos(h) / M_PER_DEG_LAT
        val dLon = meters * kotlin.math.sin(h) / (M_PER_DEG_LAT * cos(Math.toRadians(from.lat)))
        return GeoPoint(from.lat + dLat, from.lon + dLon)
    }
}

/** Polyligne avec abscisses curvilignes et projection (tracé d'itinéraire). */
class Polyline(val points: List<GeoPoint>) {
    val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + points[i - 1].distanceTo(points[i])
    }
    val totalM: Double get() = if (cumulative.isEmpty()) 0.0 else cumulative.last()

    /** Abscisse, écart perpendiculaire et cap du segment le plus proche. */
    data class Projection(val alongM: Double, val offsetM: Double, val segmentBearingDeg: Double)

    fun project(p: GeoPoint): Projection? {
        if (points.size < 2) return null
        var best: Projection? = null
        val k = cos(Math.toRadians(p.lat))
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val ax = (a.lon - p.lon) * k; val ay = a.lat - p.lat
            val bx = (b.lon - p.lon) * k; val by = b.lat - p.lat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val off = sqrt(px * px + py * py) * GeoMath.M_PER_DEG_LAT
            if (best == null || off < best.offsetM) {
                best = Projection(cumulative[i] + t * (cumulative[i + 1] - cumulative[i]), off, GeoMath.bearing(a, b))
            }
        }
        return best
    }
}

/** Index en grille (cellules de [cellDeg]°) : requêtes de voisinage en O(cellules) au lieu d'un parcours complet. */
class RadarIndex(radars: List<Radar>, private val cellDeg: Double = 0.02) {
    private val cells = HashMap<Long, MutableList<Radar>>()
    private val byId = radars.associateBy { it.id }
    val size: Int = radars.size

    fun byId(id: String): Radar? = byId[id]

    init { radars.forEach { cells.getOrPut(key(cell(it.point.lat), cell(it.point.lon))) { ArrayList(2) }.add(it) } }

    private fun cell(v: Double) = floor(v / cellDeg).toInt()
    private fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or (j.toLong() and 0xffffffffL)

    /** Radars à moins de [radiusM] de [center]. */
    fun near(center: GeoPoint, radiusM: Double): List<Radar> {
        val dLat = radiusM / GeoMath.M_PER_DEG_LAT
        val dLon = radiusM / (GeoMath.M_PER_DEG_LAT * cos(Math.toRadians(center.lat)).coerceAtLeast(0.01))
        val out = ArrayList<Radar>()
        for (i in cell(center.lat - dLat)..cell(center.lat + dLat)) for (j in cell(center.lon - dLon)..cell(center.lon + dLon)) {
            cells[key(i, j)]?.forEach { if (center.distanceTo(it.point) <= radiusM) out += it }
        }
        return out
    }
}
