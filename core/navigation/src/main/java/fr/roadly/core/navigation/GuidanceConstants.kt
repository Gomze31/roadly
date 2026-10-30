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
}
