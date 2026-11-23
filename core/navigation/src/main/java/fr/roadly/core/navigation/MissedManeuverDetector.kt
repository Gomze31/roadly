package fr.roadly.core.navigation

/**
 * Détection d'une manœuvre manquée (docs/GUIDAGE-INTELLIGENT.md §9.1) : sortie d'autoroute dépassée, mauvaise branche d'une
 * bifurcation, sortie de rond-point ratée, virage non pris. Kotlin pur.
 *
 * Problème : la règle générale de sortie d'itinéraire (40 m pendant 5 positions avec un cap écarté de 50°, ou 100 m) est faite
 * pour ne pas recalculer sur une route parallèle. Or une bretelle quitte l'autoroute sous un angle de quelques degrés : à 130 km/h,
 * celui qui manque sa sortie met près d'une minute à s'éloigner de 100 m du tracé, sans jamais montrer un cap écarté de 50°.
 *
 * Principe : autour d'un point de décision (une manœuvre), ce n'est pas l'écart qui prouve l'erreur mais sa **croissance
 * régulière**. Une route parallèle garde un écart constant ; un saut GPS ne dure qu'une position ; une voie de gauche sur une
 * chaussée large reste à quelques mètres. Seul un véhicule parti sur une autre branche s'éloigne position après position.
 *
 * Il faut donc, réunis : une manœuvre proche (juste devant ou passée depuis peu), des positions mesurées et assez précises,
 * un écart au-dessus du bruit, qui grandit sur [CONFIRM_SAMPLES] positions de suite, d'au moins [MIN_TOTAL_GROWTH_M] en tout.
 * Jamais sur une seule position.
 */
class MissedManeuverDetector {
    data class Sample(
        val routeId: String,
        /** Écart perpendiculaire au tracé (m). */
        val offRouteM: Double,
        val accuracyM: Float,
        val extrapolated: Boolean,
        /** Distance parcourue depuis le début de la dernière manœuvre passée ; null si aucune n'a encore été passée. */
        val sinceManeuverM: Double?,
        val toNextManeuverM: Double,
    )

    private var routeId: String? = null
    private var streak = 0
    private var lastOffM = 0.0
    private var startOffM = 0.0

    fun reset() { routeId = null; streak = 0; lastOffM = 0.0; startOffM = 0.0 }

    /** Vrai une seule fois, quand l'ensemble des preuves est réuni ; le détecteur repart ensuite de zéro. */
    fun update(s: Sample): Boolean {
        if (s.routeId != routeId) { reset(); routeId = s.routeId }
        val armed = (s.sinceManeuverM != null && s.sinceManeuverM in -WINDOW_BEFORE_M..WINDOW_AFTER_M) || s.toNextManeuverM <= WINDOW_BEFORE_M
        if (!armed) { streak = 0; return false }
        // Position douteuse : elle ne compte ni pour ni contre (une preuve ne se construit pas sur un fix médiocre).
        if (s.extrapolated || s.accuracyM > MAX_ACCURACY_M) return false
        val floor = maxOf(MIN_OFF_M, ACCURACY_FACTOR * s.accuracyM)
        if (s.offRouteM < floor) { streak = 0; return false }
        if (streak > 0 && s.offRouteM >= lastOffM + MIN_STEP_GROWTH_M) streak++ else { streak = 1; startOffM = s.offRouteM }
        lastOffM = s.offRouteM
        val missed = streak >= CONFIRM_SAMPLES && s.offRouteM - startOffM >= MIN_TOTAL_GROWTH_M && s.offRouteM >= CONFIRM_OFF_M
        if (missed) streak = 0
        return missed
    }

    companion object {
        /** Fenêtre de surveillance autour d'une manœuvre : de 30 m avant son point à 800 m après. */
        const val WINDOW_BEFORE_M = 30.0
        const val WINDOW_AFTER_M = 800.0
        const val MAX_ACCURACY_M = 30f
        /** Écart minimal pour commencer à compter : 12 m, ou 1,5 fois l'imprécision du fix. */
        const val MIN_OFF_M = 12.0
        const val ACCURACY_FACTOR = 1.5
        /** L'écart doit grandir d'au moins ceci à chaque position… */
        const val MIN_STEP_GROWTH_M = 0.5
        /** … sur ce nombre de positions de suite… */
        const val CONFIRM_SAMPLES = 3
        /** … d'au moins ceci en tout, pour finir au-delà de [CONFIRM_OFF_M]. */
        const val MIN_TOTAL_GROWTH_M = 8.0
        const val CONFIRM_OFF_M = 18.0
    }
}
