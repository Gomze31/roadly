package fr.roadly.core.model

/** Événements de la route à venir portés par l'itinéraire (Horizon). */
enum class HazardKind {
    /** Virage serré détecté sur la géométrie du tracé. */
    CURVE,
    /** Zone de vigilance (France : jamais un point exact, une zone de plusieurs centaines de mètres à quelques kilomètres). */
    VIGILANCE_ZONE,
    /** Gare de péage à barrières (OSM `barrier=toll_booth`, via Valhalla) : point du tracé. */
    TOLL_BOOTH,
    /** Feu tricolore (OSM `highway=traffic_signals`, via Valhalla) : point du tracé. */
    TRAFFIC_LIGHT,
}

/**
 * Un événement entre [startAlongM] et [endAlongM] (mètres depuis le départ du tracé).
 * [speedLimitKmh] : limitation annoncée dans la zone si la source la fournit ; [angleDeg] : amplitude d'un virage.
 */
data class RouteHazard(
    val kind: HazardKind,
    val startAlongM: Double,
    val endAlongM: Double,
    val speedLimitKmh: Int? = null,
    val angleDeg: Double? = null,
    /** Zone de danger : identifiant stable (équipement de référence) pour la machine à états des alertes. */
    val key: String? = null,
    /** Zone de danger : score de confiance 0–100 des données ; null = non évalué (traité comme fiable). */
    val confidence: Int? = null,
    /** Virage : true = vers la droite, false = vers la gauche ; null = sans objet. */
    val rightHand: Boolean? = null,
) {
    fun contains(alongM: Double): Boolean = alongM in startAlongM..endAlongM
}
