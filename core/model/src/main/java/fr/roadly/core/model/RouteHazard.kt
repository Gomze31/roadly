package fr.roadly.core.model

/** Événements de la route à venir portés par l'itinéraire (Horizon). */
enum class HazardKind {
    /** Virage serré détecté sur la géométrie du tracé. */
    CURVE,
    /** Zone de vigilance (France : jamais un point exact, une zone de plusieurs centaines de mètres à quelques kilomètres). */
    VIGILANCE_ZONE,
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
) {
    fun contains(alongM: Double): Boolean = alongM in startAlongM..endAlongM
}
