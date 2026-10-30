package fr.roadly.core.navigation

/** Seuils du guidage (spec Phase 2 §2 et §8). */
object GuidanceConstants {
    /** Hors itinéraire si > 40 m pendant 5 fixes consécutifs… */
    const val OFF_ROUTE_SOFT_M = 40.0
    const val OFF_ROUTE_SOFT_FIXES = 5
    /** … ou > 100 m une seule fois. */
    const val OFF_ROUTE_HARD_M = 100.0
    /** Arrivée : < 25 m de la destination et dernière manœuvre. */
    const val ARRIVAL_RADIUS_M = 25.0
    /** Fair-use : au plus un recalcul toutes les 10 s. */
    const val REROUTE_MIN_INTERVAL_MS = 10_000L
    /** Sans fix depuis > 3 s : extrapolation le long du tracé. */
    const val GPS_EXTRAPOLATE_AFTER_MS = 3_000L
    /** Badge « GPS perdu » après 10 s sans fix. */
    const val GPS_LOST_BADGE_AFTER_MS = 10_000L
    const val WATCHDOG_TICK_MS = 1_000L
    /** Fair-use : `trace_attributes` au plus toutes les 5 s. */
    const val SPEED_LIMIT_MIN_INTERVAL_MS = 5_000L
    /** Fenêtre de recherche du segment autour de la dernière position projetée. */
    const val SEARCH_BACK_M = 50.0
    const val SEARCH_AHEAD_M = 500.0
    /** Fixes conservés pour `trace_attributes`. */
    const val RECENT_FIXES = 10
    /** Manœuvres du bandeau « à venir ». */
    const val UPCOMING_COUNT = 3
    /** Extrapolation GPS plafonnée : au plus 10 s et 150 m après le dernier fix réel. */
    const val EXTRAPOLATION_MAX_MS = 10_000L
    const val EXTRAPOLATION_MAX_M = 150.0
    /** Un fix de précision > 50 m ne peut pas déclencher seul la règle « hors itinéraire > 100 m ». */
    const val HARD_RULE_MAX_ACCURACY_M = 50f
    /** Filtre de cap : au-delà de 2,5 m/s, les segments à plus de 90° du cap GPS sont écartés. */
    const val HEADING_FILTER_MIN_SPEED_MPS = 2.5f
    const val HEADING_MAX_DIFF_DEG = 90.0
    /** Quasi-égalité de distance perpendiculaire (m) : on préfère alors la continuité le long du tracé. */
    const val SEGMENT_TIE_M = 10.0
    /** Un saut global de plus de 100 m le long du tracé doit être confirmé par un second fix (à 50 m près). */
    const val GLOBAL_JUMP_M = 100.0
    const val GLOBAL_CONFIRM_RADIUS_M = 50.0
    /** Après l'arrivée, le moteur de synthèse n'est libéré qu'au bout de ce délai : la dernière annonce doit pouvoir se terminer. */
    const val SPEECH_RELEASE_DELAY_MS = 20_000L
}
