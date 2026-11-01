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

    /** Animer seulement si une durée est demandée et si le déplacement reste local (≤ [SNAP_ABOVE_M]). */
    fun shouldAnimate(from: MarkerPose, to: MarkerPose, animateMs: Long): Boolean =
        animateMs > 0 && GeoPoint(from.lat, from.lon).distanceTo(GeoPoint(to.lat, to.lon)) <= SNAP_ABOVE_M
}
