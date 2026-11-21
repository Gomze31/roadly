package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType

/**
 * Situation de conduite (docs/GUIDAGE-INTELLIGENT.md §2). Le guidage ne se contente pas de la prochaine instruction : il classe
 * ce que le conducteur vit maintenant, et l'écran comme la voix s'y adaptent. Un seul mode à la fois, dans un ordre de priorité fixe.
 */
enum class GuidanceMode {
    /** Rien à préparer : carte, prochaine instruction, vitesse, heure d'arrivée. */
    CRUISE,
    /** Manœuvre simple qui approche. */
    PREPARE,
    ROUNDABOUT_APPROACH,
    ROUNDABOUT,
    /** Sortie de rond-point prise : confirmation brève. */
    ROUNDABOUT_EXITED,
    /** Bifurcation : la chaussée se sépare, il faut choisir un côté. */
    FORK,
    /** Sortie ou bretelle. */
    EXIT,
    /** Plusieurs manœuvres rapprochées, ou intersection à plusieurs voies. */
    COMPLEX,
    ARRIVAL,
    REROUTING,
    /** Position perdue ou trop imprécise : le guidage reste affiché mais n'affirme plus rien au mètre près. */
    GPS_UNCERTAIN,
}

/** Quantité d'information à l'écran. */
enum class GuidanceDensity {
    /** Situation simple : le secondaire s'efface. */
    MINIMAL,
    STANDARD,
    /** Situation complexe : manœuvre détaillée, voies, vue dédiée, manœuvre suivante. */
    REINFORCED,
}

/** Côté où se placer pour la prochaine manœuvre, quand il est connu. */
enum class ManeuverSide { LEFT, RIGHT }

data class GuidanceSituation(
    val mode: GuidanceMode = GuidanceMode.CRUISE,
    val density: GuidanceDensity = GuidanceDensity.MINIMAL,
    /** 0..1 : avancement dans la fenêtre de préparation (0 = elle s'ouvre, 1 = au point de manœuvre) ; 0 hors fenêtre. */
    val urgency: Float = 0f,
    /** Secondes avant la manœuvre à la vitesse lissée ; null à l'arrêt ou vitesse inconnue. */
    val secondsToManeuver: Double? = null,
    /** Côté où se placer (sortie, bretelle, bifurcation, voies conseillées) ; null = aucun côté à annoncer. */
    val side: ManeuverSide? = null,
    /** La manœuvre d'après suit de près : elle doit être montrée et dite avec la prochaine. */
    val chained: Boolean = false,
)

data class SituationInput(
    val routeId: String,
    val nextIndex: Int,
    val next: Maneuver,
    val following: Maneuver?,
    val distanceToNextM: Double,
    /** Distance entre la prochaine manœuvre et la suivante ; null sans suivante. */
    val gapToFollowingM: Double?,
    val speedKmh: Double?,
    val rerouting: Boolean,
    val gpsLost: Boolean,
    val extrapolated: Boolean,
    val accuracyM: Float,
    val roundabout: RoundaboutLive?,
)

/**
 * Classe la situation à chaque position (Kotlin pur). La fenêtre de préparation dépend du temps restant, pas seulement de la
 * distance : 400 m ne laissent pas le même délai à 50 et à 130 km/h. Une fois ouverte pour une manœuvre, la fenêtre reste ouverte
 * jusqu'à ce que la manœuvre soit passée (pas de clignotement au seuil).
 */
class SituationEngine {
    private var latchedRoute: String? = null
    private var latchedIndex = -1
    private var latchedWindowM = 0.0
    private var uncertainFixes = 0

    fun reset() { latchedRoute = null; latchedIndex = -1; latchedWindowM = 0.0; uncertainFixes = 0 }

    fun update(i: SituationInput): GuidanceSituation {
        val seconds = i.speedKmh?.takeIf { it >= MIN_SPEED_KMH }?.let { i.distanceToNextM / (it / 3.6) }
        if (i.rerouting) { reset(); return GuidanceSituation(GuidanceMode.REROUTING, GuidanceDensity.STANDARD) }
        // Imprécision : il faut plusieurs positions de suite, un seul fix médiocre ne change pas l'écran.
        uncertainFixes = if (i.extrapolated || i.accuracyM > UNCERTAIN_ACCURACY_M) uncertainFixes + 1 else 0
        if (i.gpsLost || uncertainFixes >= UNCERTAIN_FIXES) {
            return GuidanceSituation(GuidanceMode.GPS_UNCERTAIN, GuidanceDensity.STANDARD, secondsToManeuver = seconds, side = sideOf(i.next))
        }
        i.roundabout?.let { r ->
            if (r.phase == RoundaboutPhase.INSIDE) return GuidanceSituation(GuidanceMode.ROUNDABOUT, GuidanceDensity.REINFORCED, urgency = r.ringFraction, secondsToManeuver = seconds)
            if (r.phase == RoundaboutPhase.EXITED) return GuidanceSituation(GuidanceMode.ROUNDABOUT_EXITED, GuidanceDensity.STANDARD)
        }
        val chained = i.gapToFollowingM != null && i.gapToFollowingM < CHAINED_GAP_M &&
            i.following?.type != ManeuverType.ROUNDABOUT_EXIT && i.following?.type != ManeuverType.ARRIVE
        val kind = kindOf(i.next, chained)
        val window = windowM(kind, i.speedKmh)
        val sameManeuver = latchedRoute == i.routeId && latchedIndex == i.nextIndex
        if (!sameManeuver) { latchedRoute = null; latchedIndex = -1 }
        val open = i.distanceToNextM <= window || (sameManeuver && i.distanceToNextM <= latchedWindowM + UNLATCH_MARGIN_M)
        if (!open || kind == null) {
            latchedRoute = null; latchedIndex = -1
            return GuidanceSituation(secondsToManeuver = seconds)
        }
        if (!sameManeuver) { latchedRoute = i.routeId; latchedIndex = i.nextIndex; latchedWindowM = window }
        val span = maxOf(latchedWindowM, window, 1.0)
        val urgency = (1.0 - i.distanceToNextM / span).toFloat().coerceIn(0f, 1f)
        val density = if (kind == GuidanceMode.PREPARE) GuidanceDensity.STANDARD else GuidanceDensity.REINFORCED
        return GuidanceSituation(kind, density, urgency, seconds, sideOf(i.next), chained)
    }

    companion object {
        /** En dessous, le temps restant n'a pas de sens (arrêt, bouchon). */
        const val MIN_SPEED_KMH = 5.0
        const val UNCERTAIN_ACCURACY_M = 40f
        const val UNCERTAIN_FIXES = 3
        /** Deux manœuvres séparées de moins de ceci forment un enchaînement. */
        const val CHAINED_GAP_M = 200.0
        const val UNLATCH_MARGIN_M = 80.0
        private const val MAX_WINDOW_M = 1_500.0

        private val exitTypes = setOf(ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT)
        private val forkTypes = setOf(ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT)
        private val passiveTypes = setOf(ManeuverType.DEPART, ManeuverType.CONTINUE, ManeuverType.FERRY, ManeuverType.OTHER, ManeuverType.ROUNDABOUT_EXIT)

        /** Mode de la prochaine manœuvre une fois sa fenêtre ouverte ; null = rien à préparer. */
        internal fun kindOf(m: Maneuver, chained: Boolean): GuidanceMode? = when {
            m.type == ManeuverType.ARRIVE -> GuidanceMode.ARRIVAL
            m.type == ManeuverType.ROUNDABOUT_ENTER -> GuidanceMode.ROUNDABOUT_APPROACH
            m.type in exitTypes -> GuidanceMode.EXIT
            m.type in forkTypes -> GuidanceMode.FORK
            m.type in passiveTypes -> null
            chained || m.lanes.size >= 3 -> GuidanceMode.COMPLEX
            else -> GuidanceMode.PREPARE
        }

        /**
         * Fenêtre de préparation (m) : la plus grande d'une distance plancher et d'un délai à la vitesse courante.
         * Sortie / bifurcation : 500 m ou 22 s (≈ 800 m à 130 km/h) ; rond-point : 200 m ou 14 s ; enchaînement : 250 m ou 16 s ;
         * virage simple : 150 m ou 11 s ; arrivée : 300 m ou 20 s.
         */
        internal fun windowM(kind: GuidanceMode?, speedKmh: Double?): Double {
            val mps = (speedKmh ?: 0.0).coerceAtLeast(0.0) / 3.6
            val (floorM, seconds) = when (kind) {
                GuidanceMode.EXIT, GuidanceMode.FORK -> 500.0 to 22.0
                GuidanceMode.ROUNDABOUT_APPROACH -> 200.0 to 14.0
                GuidanceMode.COMPLEX -> 250.0 to 16.0
                GuidanceMode.ARRIVAL -> 300.0 to 20.0
                GuidanceMode.PREPARE -> 150.0 to 11.0
                else -> return 0.0
            }
            return maxOf(floorM, mps * seconds).coerceAtMost(MAX_WINDOW_M)
        }

        /** Côté imposé par la manœuvre : son type, sinon les voies conseillées quand elles sont toutes d'un même côté. */
        fun sideOf(m: Maneuver): ManeuverSide? = when (m.type) {
            ManeuverType.EXIT_LEFT, ManeuverType.RAMP_LEFT, ManeuverType.KEEP_LEFT -> ManeuverSide.LEFT
            ManeuverType.EXIT_RIGHT, ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_RIGHT -> ManeuverSide.RIGHT
            else -> LaneAdvice.of(m.lanes)?.side
        }
    }
}
