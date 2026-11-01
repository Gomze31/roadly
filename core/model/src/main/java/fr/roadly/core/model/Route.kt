package fr.roadly.core.model

enum class RouteCosting { FASTEST, SHORTEST, NO_TOLL }

/** Types de manœuvre ; le côté des bretelles, sorties, fusions et demi-tours est conservé (B3). */
enum class ManeuverType {
    DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN_LEFT, UTURN_RIGHT, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT,
    /** Fusion sans côté indiqué (Valhalla 25). */
    MERGE, MERGE_LEFT, MERGE_RIGHT,
    /** Bretelle tout droit (Valhalla 17). */
    RAMP, RAMP_LEFT, RAMP_RIGHT,
    EXIT_LEFT, EXIT_RIGHT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER,
}

/** Côté de la chaussée où se trouve une étape (`locations[].side_of_street`). */
enum class StreetSide { LEFT, RIGHT }

/** Ramène un angle (°) dans ]−180, 180]. */
fun normalizeSignedDeg(deg: Double): Double {
    val r = ((deg % 360.0) + 360.0) % 360.0
    return if (r > 180.0) r - 360.0 else r
}

/** Direction indiquée sur une voie, de gauche à droite (masques Valhalla `kTurnLane*` regroupés). */
enum class LaneDirection { LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN, MERGE_LEFT, MERGE_RIGHT }

/** Une voie à l'approche d'une manœuvre ; [valid] = elle permet la manœuvre ; [active] = voie conseillée par Valhalla (toujours valide). */
data class Lane(val directions: Set<LaneDirection>, val valid: Boolean, val active: Boolean = false)

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
    /** Directions signalées sur le panneau de sortie (`sign.exit_toward_elements`, 2 premières jointes par « · »), p. ex. « Toulouse-Centre · Blagnac ». */
    val exitToward: String? = null,
    /** Route signalée sur le panneau de sortie (`sign.exit_branch_elements[0]`), p. ex. « A62 ». */
    val exitBranch: String? = null,
    /** Cap (°, 0 = nord) avant la manœuvre (`bearing_before`) ; absent au départ. */
    val bearingBeforeDeg: Int? = null,
    /** Cap (°) après la manœuvre (`bearing_after`) ; absent à l'arrivée. */
    val bearingAfterDeg: Int? = null,
    /** Dernier point du tracé de la manœuvre (`end_shape_index`, décalé comme [beginShapeIndex]). */
    val endShapeIndex: Int? = null,
    /** `verbal_post_transition_instruction` Valhalla (« Continuez pendant 200 mètres. »). */
    val verbalPost: String? = null,
    /** Tous les noms de voie (`street_names`) ; [streetName] reste le premier. */
    val streetNames: List<String> = emptyList(),
    /** Côté de l'étape atteinte (`locations[].side_of_street`), porté par la dernière manœuvre de chaque étape. */
    val sideOfStreet: StreetSide? = null,
) {
    /** Angle signé du virage (°, −180..180, > 0 = à droite) = `bearing_after − bearing_before` ; null sans les deux caps. */
    val turnAngleDeg: Double?
        get() {
            val before = bearingBeforeDeg ?: return null
            val after = bearingAfterDeg ?: return null
            return normalizeSignedDeg((after - before).toDouble())
        }
}

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
