package fr.roadly.feature.car

import fr.roadly.core.model.ManeuverType
import kotlin.math.abs

/** Cadrage de la carte de la voiture en suivi : zoom, inclinaison et position verticale du véhicule (0 = haut, 1 = bas). */
internal data class CarCamera(val zoom: Double, val tilt: Double, val anchorY: Float)

/** Famille d'approche d'une manœuvre : chacune a sa distance de début et son cadrage au point de manœuvre. */
internal enum class CarApproach(val startM: Double, val zoom: Double, val tilt: Double) {
    /** Carrefour : vue serrée, encore en perspective. */
    TURN(350.0, 17.3, 48.0),
    /** Rond-point : presque à plat, pour voir l'anneau entier et la sortie à prendre. */
    ROUNDABOUT(300.0, 17.5, 28.0),
    /** Sortie, bretelle, bifurcation : on s'y prépare de loin, vue plus large (les deux branches visibles). */
    EXIT(900.0, 16.6, 42.0),
}

/**
 * Caméra de l'écran de la voiture (Kotlin pur, testé sur JVM).
 *
 * Constat en voiture : cadrage fixe (zoom 16,5, 45°) quelle que soit la vitesse, et aucun mouvement à l'approche d'un rond-point ou
 * d'une sortie. Ici :
 * - en croisière, le zoom recule avec la vitesse (on voit plus loin sur voie rapide) ;
 * - à l'approche d'une manœuvre, la caméra glisse du cadrage de croisière vers celui de la manœuvre ([CarApproach]) à mesure que la
 *   distance diminue — c'est cette progression, rejouée à chaque position, qui anime l'arrivée sur le rond-point ou la sortie ;
 * - après la manœuvre, retour au cadrage de croisière par paliers ([MAX_ZOOM_STEP], [MAX_TILT_STEP]) : jamais de saut.
 */
internal object CarCameraPolicy {
    const val TILT_CRUISE = 55.0
    const val ANCHOR_CRUISE = 0.76f
    /** À l'approche, le véhicule remonte un peu : la manœuvre est devant, mais ce qui suit doit rester visible. */
    const val ANCHOR_APPROACH = 0.68f
    const val MAX_ZOOM_STEP = 0.35
    const val MAX_TILT_STEP = 7.0
    const val MAX_ANCHOR_STEP = 0.03f
    /** Vitesse (km/h) au-delà de laquelle une sortie se prépare de loin ; en dessous, c'est un carrefour comme un autre. */
    const val EXIT_FAR_MIN_KMH = 70.0

    /** (vitesse km/h, zoom) : écran large et lu de loin → un cran plus large que le téléphone. */
    private val cruise = listOf(0.0 to 17.0, 30.0 to 16.8, 50.0 to 16.4, 80.0 to 15.8, 110.0 to 15.3, 130.0 to 15.0)

    private val exitLike = setOf(
        ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT,
        ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT,
    )
    private val never = setOf(ManeuverType.DEPART, ManeuverType.CONTINUE, ManeuverType.FERRY, ManeuverType.OTHER)

    fun approachOf(type: ManeuverType, speedKmh: Double?): CarApproach? = when {
        type in never -> null
        type == ManeuverType.ROUNDABOUT_ENTER || type == ManeuverType.ROUNDABOUT_EXIT -> CarApproach.ROUNDABOUT
        type in exitLike && (speedKmh ?: 0.0) >= EXIT_FAR_MIN_KMH -> CarApproach.EXIT
        else -> CarApproach.TURN
    }

    fun cruiseZoom(speedKmh: Double?): Double {
        val v = speedKmh ?: return cruise.first().second
        if (v <= cruise.first().first) return cruise.first().second
        if (v >= cruise.last().first) return cruise.last().second
        val i = cruise.indexOfLast { it.first <= v }
        val (v0, z0) = cruise[i]
        val (v1, z1) = cruise[i + 1]
        return z0 + (z1 - z0) * (v - v0) / (v1 - v0)
    }

    /** Avancement de l'approche, 0 (début) à 1 (au point de manœuvre), adouci aux deux bouts. */
    fun approachProgress(distanceM: Double, approach: CarApproach): Double {
        val t = (1.0 - distanceM / approach.startM).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** Guidage : cadrage voulu pour cette position, rapproché de [previous] par paliers quand on s'éloigne d'une manœuvre. */
    fun guidance(speedKmh: Double?, distanceToManeuverM: Double, type: ManeuverType, previous: CarCamera?): CarCamera {
        val base = CarCamera(cruiseZoom(speedKmh), TILT_CRUISE, ANCHOR_CRUISE)
        val approach = approachOf(type, speedKmh)
        val target = if (approach == null) base else {
            val t = approachProgress(distanceToManeuverM, approach)
            // Jamais plus large que la croisière : en ville lente, la croisière est déjà serrée.
            val zoom = base.zoom + (maxOf(approach.zoom, base.zoom) - base.zoom) * t
            CarCamera(zoom, base.tilt + (approach.tilt - base.tilt) * t, (ANCHOR_CRUISE + (ANCHOR_APPROACH - ANCHOR_CRUISE) * t).toFloat())
        }
        return eased(target, previous)
    }

    /** Conduite libre : croisière seule. */
    fun freeDrive(speedKmh: Double?, previous: CarCamera?): CarCamera =
        eased(CarCamera(cruiseZoom(speedKmh) - 0.2, TILT_CRUISE - 5.0, 0.70f), previous)

    private fun eased(target: CarCamera, previous: CarCamera?): CarCamera {
        if (previous == null) return target
        fun step(from: Double, to: Double, max: Double) = if (abs(to - from) <= max) to else from + max * (if (to > from) 1 else -1)
        return CarCamera(
            zoom = step(previous.zoom, target.zoom, MAX_ZOOM_STEP),
            tilt = step(previous.tilt, target.tilt, MAX_TILT_STEP),
            anchorY = step(previous.anchorY.toDouble(), target.anchorY.toDouble(), MAX_ANCHOR_STEP.toDouble()).toFloat(),
        )
    }
}
