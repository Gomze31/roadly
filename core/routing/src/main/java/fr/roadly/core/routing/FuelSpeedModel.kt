package fr.roadly.core.routing

import fr.roadly.core.model.FuelType
import fr.roadly.core.model.Route

/**
 * Consommation relative selon la vitesse moyenne d'un tronçon (1,0 = consommation nominale du véhicule). Forme usuelle : forte en
 * ville (arrêts, ralentis), minimale vers 70–90 km/h, croissante au-delà (résistance de l'air) ; ordre de grandeur publié par
 * l'ADEME : rouler à 110 au lieu de 130 km/h économise jusqu'à 20 % de carburant. Électrique : la récupération d'énergie rend
 * la ville sobre, la vitesse coûte plus. C'est une estimation pour comparer des tracés, pas une mesure.
 */
object FuelSpeedModel {
    private val COMBUSTION = listOf(20.0 to 1.45, 30.0 to 1.25, 50.0 to 1.05, 70.0 to 0.95, 90.0 to 0.95, 110.0 to 1.08, 130.0 to 1.30, 150.0 to 1.55)
    private val ELECTRIC = listOf(20.0 to 0.90, 50.0 to 0.85, 70.0 to 0.90, 90.0 to 1.0, 110.0 to 1.20, 130.0 to 1.45, 150.0 to 1.75)

    fun factor(kmh: Double, fuel: FuelType? = null): Double {
        val curve = if (fuel == FuelType.ELECTRIC) ELECTRIC else COMBUSTION
        if (kmh <= curve.first().first) return curve.first().second
        if (kmh >= curve.last().first) return curve.last().second
        val i = curve.indexOfFirst { it.first >= kmh }
        val (v0, f0) = curve[i - 1]; val (v1, f1) = curve[i]
        return f0 + (f1 - f0) * (kmh - v0) / (v1 - v0)
    }

    /**
     * Distance « à consommation nominale » (m) : chaque manœuvre Valhalla compte sa longueur × le facteur de sa vitesse moyenne
     * (longueur / durée). Sans durées exploitables, la distance réelle.
     */
    fun equivalentMeters(route: Route, fuel: FuelType? = null): Double {
        val parts = route.maneuvers.filter { it.lengthMeters > 0.0 && it.durationSeconds > 0.0 }
        val covered = parts.sumOf { it.lengthMeters }
        if (parts.isEmpty() || covered < route.distanceMeters * 0.5) return route.distanceMeters
        val weighted = parts.sumOf { it.lengthMeters * factor(it.lengthMeters / it.durationSeconds * 3.6, fuel) }
        return weighted + (route.distanceMeters - covered).coerceAtLeast(0.0)
    }
}
