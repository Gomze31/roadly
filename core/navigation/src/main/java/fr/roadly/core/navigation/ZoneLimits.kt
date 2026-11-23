package fr.roadly.core.navigation

import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.RouteHazard

/**
 * Limitation d'une zone de danger, mise en accord avec celle de la route.
 *
 * Constat en voiture (A61, sortie 16) : la zone annonçait 80 km/h alors que la route — et le panneau affiché par Roadly — étaient à 90.
 * La limitation d'une zone vient du jeu de données des équipements (parfois périmé, et la fusion de zones voisines garde la plus basse) ;
 * celle de la route vient du profil du tracé. Deux sources, deux valeurs à l'écran. Règle : quand la limitation de la route est connue
 * à l'endroit contrôlé, c'est elle que la zone affiche et annonce ; sinon la valeur de la source est gardée.
 */
object ZoneLimits {
    /**
     * Distance avant la fin de zone où la limitation de la route est lue. La traîne après le point contrôlé vaut au plus 450 m
     * (`RadarRegulation.TAIL_MAX_M`) : ce point est donc sur le tronçon contrôlé ou juste avant, jamais après.
     */
    const val CONTROL_BEFORE_END_M = 450.0

    /** Zones de [hazards] alignées sur le profil [attributes] ; sans profil, ou hors des tronçons connus, rien ne change. */
    fun reconcile(hazards: List<RouteHazard>, attributes: RouteAttributes?): List<RouteHazard> {
        if (attributes == null || attributes.speedLimits.isEmpty()) return hazards
        return hazards.map { h ->
            if (h.kind != HazardKind.VIGILANCE_ZONE) return@map h
            val at = (h.endAlongM - CONTROL_BEFORE_END_M).coerceIn(h.startAlongM, h.endAlongM)
            val road = attributes.limitAt(at) ?: return@map h
            if (road == h.speedLimitKmh) h else h.copy(speedLimitKmh = road)
        }
    }

    /** Sans itinéraire, dans la zone : la limitation de la route suivie (si connue) remplace celle de la source. */
    fun inside(zoneLimitKmh: Int?, roadLimitKmh: Int?): Int? = roadLimitKmh ?: zoneLimitKmh
}
