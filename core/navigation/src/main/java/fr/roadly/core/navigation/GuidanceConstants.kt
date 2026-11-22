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
    /** B6 : un fix de précision > 30 m est ignoré par toute décision « hors itinéraire » (compteurs inchangés), sauf règle dégradée ([DEGRADED_MIN_FIXES]). */
    const val OFF_ROUTE_MAX_ACCURACY_M = 30f
    /**
     * NAV-3 : un fix moins précis que ce seuil est traité comme une absence de fix par le guidage (position inchangée, GPS toujours
     * réputé perdu, extrapolation poursuivie). Ce sont les positions « réseau » (antenne, Wi-Fi) que le téléphone rend quand le GNSS
     * est coupé — tunnel, parking — précises à plusieurs centaines de mètres : elles ne disent pas où est le véhicule sur le tracé.
     * 100 m : c'est l'écart qui suffit à déclarer le véhicule hors itinéraire ([OFF_ROUTE_HARD_M]) ; un fix qui peut se tromper
     * d'autant ne peut pas servir à le placer. Un GNSS médiocre (canyon urbain, 30 à 80 m) et la précision par défaut d'un fix qui
     * n'en déclare pas (50 m) restent en dessous.
     */
    const val UNUSABLE_FIX_ACCURACY_M = 100f
    /**
     * NAV-4 : règle dégradée. Quand la précision reste durablement entre [OFF_ROUTE_MAX_ACCURACY_M] et [UNUSABLE_FIX_ACCURACY_M]
     * (canyon urbain), plus aucun fix ne « décide » : ni hors itinéraire, ni recalcul, ni arrivée. Après ce nombre de fixes médiocres
     * consécutifs et cohérents entre eux (chacun à une distance du précédent compatible avec la vitesse et les deux précisions),
     * les décisions sont de nouveau prises, avec des marges élargies :
     * - hors itinéraire : l'écart au tracé est compté diminué de la précision du fix (le véhicule est au moins à cette distance
     *   du tracé même si l'erreur joue contre lui) — à 45 m de précision, 145 m au lieu de 100, 85 m au lieu de 40 ;
     * - arrivée : [DEGRADED_ARRIVAL_FIXES] fixes consécutifs dans la zone d'arrivée, véhicule au pas ou à l'arrêt.
     * 10 fixes (10 s à 1 Hz) : un passage médiocre de quelques secondes (pont, carrefour encaissé) ne change rien au comportement
     * d'avant, et un saut isolé remet le compte à zéro. Un recalcul à tort coûte plus cher qu'un recalcul en retard.
     */
    const val DEGRADED_MIN_FIXES = 10
    /** NAV-4 : fixes médiocres consécutifs dans la zone d'arrivée, à moins de [HEADING_FILTER_MIN_SPEED_MPS], pour déclarer l'arrivée. */
    const val DEGRADED_ARRIVAL_FIXES = 3
    /** B6 : la règle des 5 fixes > 40 m exige aussi un cap écarté de plus de 50° du tracé sur 3 fixes consécutifs (route parallèle). */
    const val OFF_ROUTE_HEADING_DEG = 50.0
    const val OFF_ROUTE_HEADING_FIXES = 3
    /** Filtre de cap : au-delà de 2,5 m/s, les segments à plus de 90° du cap GPS sont écartés. */
    const val HEADING_FILTER_MIN_SPEED_MPS = 2.5f
    const val HEADING_MAX_DIFF_DEG = 90.0
    /** B1 : l'indice de manœuvre ne recule que sur un recul de plus de 30 m sous l'abscisse maximale atteinte… */
    const val BACKTRACK_CONFIRM_M = 30.0
    /** … confirmé par 2 fixes consécutifs. */
    const val BACKTRACK_CONFIRM_FIXES = 2
    /** B6 : à écart perpendiculaire quasi égal (≤ 15 m), un autre passage du tracé proche de la dernière abscisse l'emporte (pont, échangeur)… */
    const val CONTINUITY_TIE_M = 15.0
    /** … s'il est à plus de 50 m le long du tracé (en deçà : simple angle du même passage)… */
    const val CONTINUITY_MIN_GAP_M = 50.0
    /** … et ne recule pas de plus de 30 m (même valeur que le recul confirmé de B1). */
    const val CONTINUITY_BACK_M = BACKTRACK_CONFIRM_M
    /** Un saut global de plus de 100 m le long du tracé doit être confirmé par un second fix (à 50 m près). */
    const val GLOBAL_JUMP_M = 100.0
    const val GLOBAL_CONFIRM_RADIUS_M = 50.0
    /** Après l'arrivée, le moteur de synthèse n'est libéré qu'au bout de ce délai : la dernière annonce doit pouvoir se terminer. */
    const val SPEECH_RELEASE_DELAY_MS = 20_000L
    /** Un recalcul sans réponse au bout de ce délai est traité comme un échec (l'ancien tracé est conservé). */
    const val REROUTE_TIMEOUT_MS = 10_000L
    /** Un fix plus ancien que le démarrage de la course (moins cette tolérance) est un rejeu périmé : ignoré. */
    const val STALE_FIX_TOLERANCE_MS = 2_000L
    /** Pluie (Open-Meteo) : au plus une requête toutes les 10 min. */
    const val RAIN_CHECK_INTERVAL_MS = 600_000L
    /** B1 : une manœuvre est passée quand `along` dépasse son début de max(10 m, 2 × précision du fix), marge plafonnée (voir ci-dessous). */
    const val MANEUVER_PASSED_MIN_M = 10.0
    /** B1 : facteur appliqué à la précision du fix dans la marge de passage. */
    const val MANEUVER_PASSED_ACCURACY_FACTOR = 2.0
    /** B1 : plafond de la marge de passage, elle-même bornée à la moitié du tronçon de la manœuvre (un dernier tronçon court doit pouvoir arriver). */
    const val MANEUVER_PASSED_MAX_M = 60.0
    /** B5 : une annonce LOW / NORMAL restée plus de 8 s en file n'est plus dite (palier dépassé). */
    const val SPEECH_STALE_AFTER_MS = 8_000L
    /**
     * Garde-fou du moteur de synthèse : un énoncé dont le rappel de fin (`onDone` / `onStop` / `onError`) n'arrive pas est réputé fini
     * au bout de 3 s + 100 ms par caractère ; sans cela la file resterait bloquée et le focus audio tenu.
     */
    const val SPEECH_UTTERANCE_TIMEOUT_MS = 3_000L
    const val SPEECH_MS_PER_CHAR = 100L
    /** B5 : l'annonce « maintenant » part à max(40 m, 4 s de trajet à la vitesse lissée). */
    const val NOW_MIN_M = 40.0
    const val NOW_LEAD_S = 4.0
    /** B5 : une manœuvre suivante à moins de 200 m est annoncée avec la courante (« …, puis … »). */
    const val COMBINE_NEXT_MANEUVER_M = 200.0
    /** B5 : pas d'annonce lointaine (FAR) dans les 10 s qui suivent le passage d'une manœuvre. */
    const val FAR_QUIET_AFTER_MANEUVER_MS = 10_000L
}
