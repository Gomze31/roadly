package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint

/**
 * C3 : tronçon parcouru en deux morceaux — préfixe p_0 … p_segmentIndex, reconstruit seulement quand l'index ou le tracé change,
 * et queue p_segmentIndex → position projetée (2 points), reconstruite à chaque position. Le GeoJSON envoyé par position est borné.
 */
class TraversedLine {
    /** [prefix] = null s'il est inchangé depuis l'appel précédent ; [tail] toujours fourni. */
    data class Update(val prefix: String?, val tail: String)

    private var geometry: List<GeoPoint>? = null
    private var segmentIndex = Int.MIN_VALUE

    fun update(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): Update {
        val prefixChanged = geometry !== this.geometry || segmentIndex != this.segmentIndex
        this.geometry = geometry
        this.segmentIndex = segmentIndex
        val prefix = if (prefixChanged) TraversedFeatureBuilder.build(geometry, segmentIndex, null) else null
        return Update(prefix, tail(geometry, segmentIndex, snapped))
    }

    private fun tail(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): String {
        if (snapped == null || segmentIndex < 0 || geometry.isEmpty()) return TraversedFeatureBuilder.EMPTY
        val start = geometry[segmentIndex.coerceAtMost(geometry.lastIndex)]
        return TraversedFeatureBuilder.build(listOf(start), 0, snapped)
    }
}
