package fr.roadly.core.model

/**
 * Position projetée sur l'itinéraire actif, calculée par `RouteTracker` (core:navigation).
 * Distances en mètres, durées en secondes. [segmentIndex] = segment p_i → p_{i+1} de `Route.geometry`.
 */
data class RouteProgress(
    val routeId: String,
    val segmentIndex: Int,
    val distanceAlongM: Double,
    val offRouteM: Double,
    val nextManeuverIndex: Int,
    val distanceToNextManeuverM: Double,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,
    val snapped: GeoPoint,
    val bearingDeg: Float?,
    /** `true` si la position est extrapolée le long du tracé (perte de fix GPS) plutôt que mesurée. */
    val extrapolated: Boolean = false,
)
