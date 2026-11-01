package fr.roadly.core.model

enum class RouteCosting { FASTEST, SHORTEST, NO_TOLL }

enum class ManeuverType {
    DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, MERGE, RAMP, EXIT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER,
}

/** Direction indiquée sur une voie, de gauche à droite (masques Valhalla `kTurnLane*` regroupés). */
enum class LaneDirection { LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN, MERGE_LEFT, MERGE_RIGHT }

/** Une voie à l'approche d'une manœuvre ; [valid] = la voie permet d'effectuer la manœuvre. */
data class Lane(val directions: Set<LaneDirection>, val valid: Boolean)

data class Maneuver(
    val type: ManeuverType,
    val instruction: String,
    val streetName: String?,
    val lengthMeters: Double,
    val durationSeconds: Double,
    val beginShapeIndex: Int,
    val roundaboutExit: Int?,
    /** `verbal_pre_transition_instruction` Valhalla (en français) : prononcée juste avant la manœuvre. */
    val verbalPre: String? = null,
    /** `verbal_transition_alert_instruction` Valhalla (en français) : prononcée en anticipation ; absente au départ. */
    val verbalAlert: String? = null,
    /** Voies à l'approche de la manœuvre ; vide si Valhalla n'en renvoie pas. */
    val lanes: List<Lane> = emptyList(),
    /** Numéro de sortie signalisé (`sign.exit_number_elements[0].text`), p. ex. « 12 ». */
    val exitNumber: String? = null,
)

/** Préférences d'itinéraire appliquées aux trois variantes et au recalcul (`costing_options.auto.use_* = 0`). */
data class RoutePreferences(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
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
    /** Altitudes (m) échantillonnées tous les [elevationIntervalM] le long du tracé (`elevation_interval` Valhalla) ; null = non fournies. */
    val elevationM: List<Double>? = null,
    val elevationIntervalM: Double? = null,
    /** Variante trottinette calculée (null = itinéraire motorisé). */
    val scooterGoal: ScooterRouteGoal? = null,
)

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val waypoints: List<GeoPoint> = emptyList(),
    val costing: RouteCosting,
    val vehicle: VehicleProfile? = null,
    val departAtEpochMs: Long? = null,
    val preferences: RoutePreferences = RoutePreferences(),
    /** Cap de l'utilisateur en degrés (0..360) : utilisé au recalcul pour éviter un demi-tour imposé ; null = inconnu. */
    val originHeadingDeg: Float? = null,
    /** Non nul = itinéraire trottinette (costing Valhalla `bicycle` paramétré par le profil et les préférences trottinette). */
    val scooter: ScooterRouteRequest? = null,
    /** Zones à éviter (`exclude_polygons` Valhalla), anneaux fermés ; vide = aucune. */
    val excludePolygons: List<List<GeoPoint>> = emptyList(),
)
