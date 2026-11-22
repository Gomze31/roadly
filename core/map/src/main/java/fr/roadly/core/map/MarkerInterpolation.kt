package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix

/** Pose affichée du marqueur utilisateur (position et cap), interpolée entre deux positions GPS. */
internal data class MarkerPose(val lat: Double, val lon: Double, val bearingDeg: Double)

/** C1 : le marqueur glisse d'une position à la suivante au rythme de la caméra, au lieu de sauter à chaque position (1 Hz). */
internal object MarkerInterpolation {
    /** Au-delà (m), le marqueur est posé directement (recalcul, saut GPS) au lieu de glisser à travers la carte. */
    const val SNAP_ABOVE_M = 200.0

    /** Pose d'un fix ; cap inconnu = nord (comme le marqueur d'origine). */
    fun pose(fix: LocationFix): MarkerPose = MarkerPose(fix.point.lat, fix.point.lon, Bearings.normalize((fix.bearingDeg ?: 0f).toDouble()))

    /** Pose à la fraction [t] (bornée à 0..1) : position linéaire, cap par le plus court chemin. */
    fun lerp(from: MarkerPose, to: MarkerPose, t: Double): MarkerPose {
        val k = t.coerceIn(0.0, 1.0)
        return MarkerPose(from.lat + (to.lat - from.lat) * k, from.lon + (to.lon - from.lon) * k, Bearings.lerp(from.bearingDeg, to.bearingDeg, k))
    }

    /** Vitesse (m/s) sous laquelle le véhicule est tenu pour arrêté, et plancher (m) de la dérive GPS tolérée à l'arrêt. */
    const val STANDSTILL_BELOW_MPS = 0.5f
    const val STANDSTILL_MIN_JITTER_M = 2.0

    /**
     * Vrai si [to] n'est que la dérive GPS d'un véhicule arrêté : vitesse mesurée quasi nulle et écart à la pose affichée [from]
     * inférieur à la précision annoncée (au moins [STANDSTILL_MIN_JITTER_M]). Le marqueur reste alors où il est. Constat sur le
     * HONOR, application à l'arrêt en intérieur (précision 8–12 m) : la carte était rendue à 57–59 images par seconde en continu ;
     * chaque position, décalée de quelques mètres, relançait 1 s d'animation. L'écart est mesuré à la pose affichée, pas au fix
     * précédent : un déplacement lent mais réel finit par dépasser la précision et le marqueur suit.
     */
    fun isStandstillJitter(from: MarkerPose, to: MarkerPose, speedMps: Float?, accuracyM: Float): Boolean {
        if (speedMps == null || speedMps >= STANDSTILL_BELOW_MPS) return false
        val moved = GeoPoint(from.lat, from.lon).distanceTo(GeoPoint(to.lat, to.lon))
        return moved < maxOf(STANDSTILL_MIN_JITTER_M, accuracyM.toDouble())
    }

    /** En deçà (m, et degrés de cap), le marqueur n'a pas bougé : une seule écriture au lieu d'une par image pendant 1 s. */
    const val STILL_BELOW_M = 0.5
    const val STILL_BELOW_DEG = 1.0

    /**
     * Animer seulement si une durée est demandée, si le déplacement reste local (≤ [SNAP_ABOVE_M]) et s'il y a quelque chose à
     * animer : à l'arrêt, chaque position relançait 1 s d'animation d'une pose vers la même pose, soit une réécriture de la source
     * du marqueur à chaque image rendue, en permanence.
     */
    fun shouldAnimate(from: MarkerPose, to: MarkerPose, animateMs: Long): Boolean {
        if (animateMs <= 0) return false
        val moved = GeoPoint(from.lat, from.lon).distanceTo(GeoPoint(to.lat, to.lon))
        val turned = kotlin.math.abs(((to.bearingDeg - from.bearingDeg + 540.0) % 360.0) - 180.0)
        return moved <= SNAP_ABOVE_M && (moved >= STILL_BELOW_M || turned >= STILL_BELOW_DEG)
    }
}
