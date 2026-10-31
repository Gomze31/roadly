package fr.roadly.core.routing

import fr.roadly.core.model.GeoPoint

/** Ordre des arrêts sans serveur : plus proche voisin à vol d'oiseau depuis l'origine (repli du mode tournée). */
object StopOrdering {
    const val MAX_STOPS = 10

    fun nearestNeighbour(origin: GeoPoint, stops: List<GeoPoint>): List<Int> {
        val remaining = stops.indices.toMutableList()
        val order = ArrayList<Int>(stops.size)
        var at = origin
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { at.distanceTo(stops[it]) }!!
            remaining -= next
            order += next
            at = stops[next]
        }
        return order
    }

    /** Déplace l'élément [from] en position [to] (listes d'arrêts réordonnées par l'utilisateur). */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }
}
