package fr.roadly.core.navigation

import kotlin.math.roundToInt

/**
 * S5 — carte d'identité d'un tracé, tirée de son profil réel ([RouteAttributes], `trace_attributes` Valhalla) : rien n'est estimé
 * hors des tronçons dont la limitation est connue.
 *
 * - [knownLimitShare] : part de la distance (0..1) dont la limitation est connue.
 * - [fastRoadSeconds] : temps passé sur les tronçons limités au-dessus de 90 km/h, compté à la limitation (borne basse : on n'y roule
 *   pas plus vite). Null si aucune limitation n'est connue (on ne sait pas), 0 si toutes les limitations connues sont ≤ 90.
 * - [fastRoadShare] : part de la distance totale sur ces tronçons.
 * - [tollGantries] : portiques de péage en flux libre (OSM `highway=toll_gantry`) franchis.
 */
data class RouteIdentity(
    val knownLimitShare: Double,
    val fastRoadSeconds: Double?,
    val fastRoadShare: Double,
    val tollGantries: Int,
) {
    /** Pourcentage entier de distance à limitation connue. */
    val knownLimitPercent: Int get() = (knownLimitShare * 100).roundToInt().coerceIn(0, 100)

    /** Minutes (arrondies, au moins 1 s'il y a un tronçon rapide) passées au-dessus de 90 ; null si inconnu. */
    val fastRoadMinutes: Int? get() = fastRoadSeconds?.let { s -> if (s <= 0.0) 0 else (s / 60.0).roundToInt().coerceAtLeast(1) }

    companion object {
        /** Seuil des « routes rapides » : au-dessus de 90 km/h (voies express, autoroutes). */
        const val FAST_ROAD_ABOVE_KMH = 90

        fun from(a: RouteAttributes): RouteIdentity {
            var fastSeconds = 0.0
            var fastMeters = 0.0
            for (s in a.speedLimits) {
                val len = (s.endAlongM - s.startAlongM).coerceAtLeast(0.0)
                if (s.kmh > FAST_ROAD_ABOVE_KMH && len > 0.0) {
                    fastMeters += len
                    fastSeconds += len / (s.kmh / 3.6)
                }
            }
            return RouteIdentity(
                knownLimitShare = a.coverage,
                fastRoadSeconds = if (a.speedLimits.isEmpty()) null else fastSeconds,
                fastRoadShare = if (a.totalM <= 0.0) 0.0 else (fastMeters / a.totalM).coerceIn(0.0, 1.0),
                tollGantries = a.tollGantries.size,
            )
        }
    }
}
