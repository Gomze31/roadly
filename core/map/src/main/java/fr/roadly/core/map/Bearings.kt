package fr.roadly.core.map

/** Calculs sur les caps (degrés, 0 = nord, sens horaire), toujours par le plus court chemin. */
internal object Bearings {
    /** Cap ramené dans [0, 360[. */
    fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Écart signé le plus court de [from] vers [to], dans ]-180, 180]. */
    fun delta(from: Double, to: Double): Double {
        val d = normalize(to - from)
        return if (d > 180.0) d - 360.0 else d
    }

    /** Cap à la fraction [t] de [from] vers [to] (0 → from, 1 → to), sans tour complet. */
    fun lerp(from: Double, to: Double, t: Double): Double = normalize(from + delta(from, to) * t)
}
