package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint

/**
 * C3 : tronçon parcouru en deux morceaux — préfixe p_0 … p_prefixEnd, reconstruit par paquets de [TRAVERSED_CHUNK] segments
 * (ou sur un recul, ou sur un nouveau tracé), et queue p_prefixEnd … p_segmentIndex → position projetée (au plus
 * [TRAVERSED_CHUNK] + 1 points), reconstruite à chaque position. Le GeoJSON envoyé par position reste borné, et le préfixe
 * n'est plus renvoyé à chaque avancée d'un segment (sommets espacés de 10 à 50 m : presque chaque seconde sur route).
 */
class TraversedLine {
    /** [prefix] = null s'il est inchangé depuis l'appel précédent ; [tail] toujours fourni. */
    data class Update(val prefix: String?, val tail: String)

    private var geometry: List<GeoPoint>? = null
    /** Dernier sommet du préfixe envoyé ; [NONE] = rien de parcouru (préfixe vide). */
    private var prefixEnd = NONE

    fun update(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): Update {
        if (geometry.isEmpty() || segmentIndex < 0) {
            val changed = geometry !== this.geometry || prefixEnd != NONE
            this.geometry = geometry
            prefixEnd = NONE
            return Update(if (changed) TraversedFeatureBuilder.EMPTY else null, TraversedFeatureBuilder.EMPTY)
        }
        val index = segmentIndex.coerceAtMost(geometry.lastIndex)
        val rebuild = geometry !== this.geometry || prefixEnd == NONE || index < prefixEnd || index - prefixEnd >= TRAVERSED_CHUNK
        this.geometry = geometry
        if (rebuild) prefixEnd = index
        val prefix = if (rebuild) TraversedFeatureBuilder.build(geometry, index, null) else null
        val tail = TraversedFeatureBuilder.build(geometry.subList(prefixEnd, index + 1), index - prefixEnd, snapped)
        return Update(prefix, tail)
    }

    companion object {
        /** Nombre de segments parcourus avant de reconstruire le préfixe ; la queue en porte au plus autant (plus la position). */
        const val TRAVERSED_CHUNK = 32
        private const val NONE = -1
    }
}
