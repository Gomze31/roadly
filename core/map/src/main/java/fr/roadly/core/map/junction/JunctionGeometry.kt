package fr.roadly.core.map.junction

import fr.roadly.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

/** Point plan local (m) : x vers l'est, y vers le nord. */
data class V2(val x: Double, val y: Double) {
    operator fun plus(o: V2) = V2(x + o.x, y + o.y)
    operator fun minus(o: V2) = V2(x - o.x, y - o.y)
    operator fun times(k: Double) = V2(x * k, y * k)
    fun length() = hypot(x, y)
    fun normalized(): V2 { val l = length(); return if (l < 1e-9) V2(0.0, 0.0) else V2(x / l, y / l) }
    /** Normale à droite du sens de marche (x, y) → (y, −x). */
    fun rightNormal() = V2(y, -x)
    fun dist(o: V2) = hypot(x - o.x, y - o.y)
    fun dot(o: V2) = x * o.x + y * o.y

    companion object {
        /** Vecteur unitaire du cap [deg] (0 = nord, 90 = est). */
        fun ofHeading(deg: Double): V2 { val r = Math.toRadians(deg); return V2(kotlin.math.sin(r), cos(r)) }
    }
}

/** Projection locale équirectangulaire autour de [origin] : suffisante (< 0,1 %) sur le rayon d'un carrefour (≤ 2 km). */
class LocalFrame(val origin: GeoPoint) {
    private val kx = M_PER_DEG * cos(Math.toRadians(origin.lat))
    fun toLocal(p: GeoPoint) = V2((p.lon - origin.lon) * kx, (p.lat - origin.lat) * M_PER_DEG)
    fun toGeo(v: V2) = GeoPoint(origin.lat + v.y / M_PER_DEG, origin.lon + v.x / kx)

    companion object { const val M_PER_DEG = 111_320.0 }
}

/** Polyligne locale avec abscisses curvilignes. */
class Polyline2(val pts: List<V2>) {
    val s: DoubleArray = DoubleArray(pts.size).also { for (i in 1 until pts.size) it[i] = it[i - 1] + pts[i].dist(pts[i - 1]) }
    val length: Double get() = if (s.isEmpty()) 0.0 else s.last()

    private fun segAt(d: Double): Int {
        if (pts.size < 2) return 0
        var lo = 0; var hi = pts.size - 2
        while (lo < hi) { val mid = (lo + hi + 1) ushr 1; if (s[mid] <= d) lo = mid else hi = mid - 1 }
        return lo
    }

    fun pointAt(d: Double): V2 {
        if (pts.isEmpty()) return V2(0.0, 0.0)
        if (pts.size == 1) return pts[0]
        val dd = d.coerceIn(0.0, length)
        val i = segAt(dd)
        val len = s[i + 1] - s[i]
        val t = if (len < 1e-9) 0.0 else (dd - s[i]) / len
        return pts[i] + (pts[i + 1] - pts[i]) * t
    }

    /** Tangente unitaire à l'abscisse [d] (moyenne sur ±[window] m pour lisser les coudes). */
    fun tangentAt(d: Double, window: Double = 4.0): V2 {
        if (pts.size < 2) return V2(0.0, 1.0)
        val a = pointAt(d - window); val b = pointAt(d + window)
        val t = (b - a).normalized()
        return if (t.length() < 0.5) (pts[segAt(d) + 1] - pts[segAt(d)]).normalized() else t
    }

    /** Projection de [p] : abscisse et distance. */
    fun project(p: V2): Pair<Double, Double> {
        var bestS = 0.0; var bestD = Double.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val ab = pts[i + 1] - a
            val len2 = ab.dot(ab)
            val t = if (len2 < 1e-12) 0.0 else ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
            val q = a + ab * t
            val dist = q.dist(p)
            if (dist < bestD) { bestD = dist; bestS = s[i] + t * sqrt(len2) }
        }
        return bestS to bestD
    }

    /** Points tous les ≤ [step] m (les sommets d'origine sont conservés). */
    fun densified(step: Double): List<V2> {
        if (pts.size < 2) return pts
        val out = ArrayList<V2>(pts.size * 2)
        out += pts[0]
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val n = kotlin.math.ceil(a.dist(b) / step).toInt().coerceAtLeast(1)
            for (k in 1..n) out += a + (b - a) * (k.toDouble() / n)
        }
        return out
    }
}

/** Distance d'un point à une polyligne (m). */
internal fun distanceToPolyline(p: V2, line: List<V2>): Double {
    if (line.isEmpty()) return Double.MAX_VALUE
    if (line.size == 1) return p.dist(line[0])
    var best = Double.MAX_VALUE
    for (i in 0 until line.size - 1) {
        val a = line[i]; val ab = line[i + 1] - a
        val len2 = ab.dot(ab)
        val t = if (len2 < 1e-12) 0.0 else ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
        best = minOf(best, (a + ab * t).dist(p))
    }
    return best
}

/** Cercle ajusté (méthode de Kåsa, moindres carrés algébriques) ; null si mal conditionné. */
internal fun fitCircle(points: List<V2>): Pair<V2, Double>? {
    if (points.size < 3) return null
    val mx = points.sumOf { it.x } / points.size
    val my = points.sumOf { it.y } / points.size
    var suu = 0.0; var svv = 0.0; var suv = 0.0; var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
    for (p in points) {
        val u = p.x - mx; val v = p.y - my
        suu += u * u; svv += v * v; suv += u * v
        suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
    }
    val det = suu * svv - suv * suv
    if (kotlin.math.abs(det) < 1e-6) return null
    val b1 = 0.5 * (suuu + suvv); val b2 = 0.5 * (svvv + svuu)
    val uc = (b1 * svv - b2 * suv) / det
    val vc = (suu * b2 - suv * b1) / det
    val r = sqrt(uc * uc + vc * vc + (suu + svv) / points.size)
    return V2(uc + mx, vc + my) to r
}

/** Cap (°, 0 = nord) du vecteur [v]. */
internal fun headingOf(v: V2): Double = ((Math.toDegrees(atan2(v.x, v.y)) % 360.0) + 360.0) % 360.0

internal const val TAU = 2 * PI

/** Interpolation douce 0..1. */
internal fun smoothstep(x: Double): Double { val t = x.coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
