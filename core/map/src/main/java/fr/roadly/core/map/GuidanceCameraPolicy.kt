package fr.roadly.core.map

import fr.roadly.core.model.ManeuverType

/** Marges de la caméra en fractions de la vue (0..1) : gauche, haut, droite, bas. */
data class CameraPadding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object { val NONE = CameraPadding(0f, 0f, 0f, 0f) }
}

data class GuidanceCamera(val zoom: Double, val pitch: Double, val bearing: Double, val padding: CameraPadding)

data class GuidanceCameraInput(
    val speedKmh: Double?,
    val bearingDeg: Float?,
    val distanceToManeuverM: Double,
    val maneuverType: ManeuverType,
    /** Réglage « Carte 3D en guidage ». */
    val threeD: Boolean,
    /** Économiseur de batterie Android actif. */
    val powerSave: Boolean,
    val landscape: Boolean,
    /** Approche décidée par l'écran (hystérésis B1) ; null = règle de distance [GuidanceCameraPolicy.isApproaching]. */
    val approaching: Boolean? = null,
    /** Bâtiments 3D affichés (mode 3D hors économie d'énergie) : autorise l'inclinaison « ville dense ». */
    val buildingsVisible: Boolean = false,
)

/**
 * Caméra de guidage (spec guidage 3D §1, C1) : inclinaison 58° (50° à l'approche, 60° en ville dense, 45° en économie, 0° en 2D),
 * zoom de croisière selon la vitesse, vue serrée à l'approche d'une manœuvre, cap conservé à l'arrêt, voiture décalée vers le bas.
 */
object GuidanceCameraPolicy {
    const val PITCH_3D = 58.0
    const val PITCH_POWER_SAVE = 45.0
    const val PITCH_APPROACH = 50.0
    /** LIMITE : MapLibre Android 11.13.5 ne rend pas au-delà de 60° (`MapLibreConstants.MAXIMUM_PITCH`, `Transform.setMaxPitch`). */
    const val MAX_PITCH = 60.0
    /** Ville dense (C1 demande 62°) : plafonnée au maximum rendu par MapLibre, [MAX_PITCH]. */
    const val PITCH_DENSE_CITY = MAX_PITCH
    /** Vitesse (km/h) sous laquelle, bâtiments visibles, la caméra passe en inclinaison « ville dense ». */
    const val DENSE_CITY_MAX_KMH = 30.0
    const val ZOOM_2D = 17.0
    const val ZOOM_APPROACH = 18.0
    const val MAX_ZOOM_STEP = 0.5
    const val STOPPED_KMH = 5.0
    const val EXIT_APPROACH_M = 400.0
    const val EXIT_APPROACH_MIN_KMH = 70.0
    const val TURN_APPROACH_M = 150.0

    val PADDING_PORTRAIT = CameraPadding(0f, 0.45f, 0f, 0.12f)
    val PADDING_LANDSCAPE = CameraPadding(0.40f, 0.25f, 0f, 0f)

    private val exitLike = setOf(
        ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT,
        ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT,
    )
    private val neverApproach = setOf(ManeuverType.DEPART, ManeuverType.ARRIVE, ManeuverType.CONTINUE)

    /** (vitesse km/h, zoom) ; interpolation linéaire entre les points, bornée aux extrémités. */
    private val cruiseZoom = listOf(30.0 to 17.5, 50.0 to 17.0, 90.0 to 16.3, 110.0 to 15.8)

    fun isApproaching(distanceM: Double, type: ManeuverType, speedKmh: Double?): Boolean {
        if (type in neverApproach) return false
        if (type in exitLike && (speedKmh ?: 0.0) > EXIT_APPROACH_MIN_KMH) return distanceM < EXIT_APPROACH_M
        return distanceM < TURN_APPROACH_M
    }

    fun compute(input: GuidanceCameraInput, previous: GuidanceCamera?): GuidanceCamera {
        val bearing = when {
            input.bearingDeg == null -> previous?.bearing ?: 0.0
            (input.speedKmh ?: 0.0) < STOPPED_KMH && previous != null -> previous.bearing
            else -> input.bearingDeg.toDouble()
        }
        if (!input.threeD) return GuidanceCamera(ZOOM_2D, 0.0, bearing, CameraPadding.NONE)
        val approaching = input.approaching ?: isApproaching(input.distanceToManeuverM, input.maneuverType, input.speedKmh)
        val pitch = when {
            input.powerSave -> PITCH_POWER_SAVE
            approaching -> PITCH_APPROACH
            input.buildingsVisible && input.speedKmh != null && input.speedKmh < DENSE_CITY_MAX_KMH -> PITCH_DENSE_CITY
            else -> PITCH_3D
        }
        val target = if (approaching) ZOOM_APPROACH else zoomForSpeed(input.speedKmh)
        val zoom = if (approaching || previous == null) target else previous.zoom + (target - previous.zoom).coerceIn(-MAX_ZOOM_STEP, MAX_ZOOM_STEP)
        return GuidanceCamera(zoom, pitch, bearing, if (input.landscape) PADDING_LANDSCAPE else PADDING_PORTRAIT)
    }

    internal fun zoomForSpeed(speedKmh: Double?): Double {
        val v = speedKmh ?: return ZOOM_2D
        if (v <= cruiseZoom.first().first) return cruiseZoom.first().second
        if (v >= cruiseZoom.last().first) return cruiseZoom.last().second
        val i = cruiseZoom.indexOfLast { it.first <= v }
        val (v0, z0) = cruiseZoom[i]
        val (v1, z1) = cruiseZoom[i + 1]
        return z0 + (z1 - z0) * (v - v0) / (v1 - v0)
    }
}
