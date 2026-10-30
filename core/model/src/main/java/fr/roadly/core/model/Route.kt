package fr.roadly.core.model

enum class RouteCosting { FASTEST, SHORTEST, NO_TOLL }

enum class ManeuverType {
    DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, MERGE, RAMP, EXIT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER,
}

data class Maneuver(
    val type: ManeuverType,
    val instruction: String,
    val streetName: String?,
    val lengthMeters: Double,
    val durationSeconds: Double,
    val beginShapeIndex: Int,
    val roundaboutExit: Int?,
)

data class Route(
    val id: String,
    val costing: RouteCosting,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometry: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val hasToll: Boolean,
    val providerId: String,
)

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val waypoints: List<GeoPoint> = emptyList(),
    val costing: RouteCosting,
    val vehicle: VehicleProfile? = null,
    val departAtEpochMs: Long? = null,
)
