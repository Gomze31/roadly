package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint

/**
 * Emplacement de l'étiquette « Choisir » d'un itinéraire alternatif : le point de son tracé le plus éloigné
 * des autres tracés (là où il se distingue visuellement), en ignorant les extrémités communes.
 */
object RouteLabelPlacement {
    /** Pas d'échantillonnage du tracé (points) : borne le coût sur les longs itinéraires. */
    private const val MAX_SAMPLES = 120

    fun labelPoint(route: List<GeoPoint>, others: List<List<GeoPoint>>): GeoPoint? {
        if (route.isEmpty()) return null
        if (others.all { it.isEmpty() }) return route[route.size / 2]
        val step = maxOf(1, route.size / MAX_SAMPLES)
        val othersSampled = others.map { o -> o.filterIndexed { i, _ -> i % maxOf(1, o.size / MAX_SAMPLES) == 0 || i == o.lastIndex } }
        var best: GeoPoint? = null
        var bestDist = -1.0
        var i = 0
        while (i < route.size) {
            val p = route[i]
            val d = othersSampled.minOf { o -> if (o.isEmpty()) Double.MAX_VALUE else o.minOf { it.distanceTo(p) } }
            if (d > bestDist) { bestDist = d; best = p }
            i += step
        }
        return best
    }
}
