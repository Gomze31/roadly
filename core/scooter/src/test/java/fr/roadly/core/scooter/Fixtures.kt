package fr.roadly.core.scooter

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.scooter.segment.EdgeAttributes

/** Jeux de données contrôlés : tracé rectiligne vers l'est, 1 point tous les ~10 m. */
object Fixtures {
    /** Longitude correspondant à ~10 m à la latitude de Toulouse. */
    private const val LAT = 43.6
    private const val STEP_DEG = 10.0 / (111_320.0 * 0.7242)

    fun line(lengthM: Double): List<GeoPoint> = (0..(lengthM / 10).toInt()).map { GeoPoint(LAT, 1.44 + it * STEP_DEG) }

    fun route(lengthM: Double, maneuvers: List<Maneuver> = defaultManeuvers(lengthM), elevation: List<Double>? = null) = Route(
        id = "r1", costing = RouteCosting.FASTEST, distanceMeters = lengthM, durationSeconds = lengthM / (20 / 3.6),
        geometry = line(lengthM), maneuvers = maneuvers, hasToll = false, providerId = "test",
        elevationM = elevation, elevationIntervalM = elevation?.let { 30.0 },
    )

    fun maneuver(type: ManeuverType, atM: Double, instruction: String = type.name) =
        Maneuver(type, instruction, null, 0.0, 0.0, (atM / 10).toInt(), null, verbalPre = instruction, verbalAlert = instruction)

    fun defaultManeuvers(lengthM: Double) = listOf(maneuver(ManeuverType.DEPART, 0.0), maneuver(ManeuverType.ARRIVE, lengthM))

    fun edge(lengthM: Double, use: String = "road", cycleLane: String = "none", roadClass: String = "residential", surface: String = "paved_smooth", grade: Double? = 0.0, speed: Int? = null, wayId: Long? = null) =
        EdgeAttributes(lengthM = lengthM, use = use, cycleLane = cycleLane, roadClass = roadClass, surface = surface, weightedGradePct = grade, maxUpGradePct = grade?.coerceAtLeast(0.0), maxDownGradePct = grade?.coerceAtMost(0.0), speedLimitKmh = speed, wayId = wayId)

    val URBAN50 = mapOf("highway" to "residential", "maxspeed" to "50", "source:maxspeed" to "FR:urban")
}
