package fr.roadly.core.navigation

import fr.roadly.core.model.Lane
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType

/**
 * Guidage vocal progressif (docs/GUIDAGE-INTELLIGENT.md §4). Quatre temps par manœuvre :
 * 1. anticipation (palier FAR de [AnnouncementPolicy]) ;
 * 2. préparation : où se placer ([PositioningPolicy]) — seulement quand un côté ou des voies sont connus ;
 * 3. manœuvre (paliers NEAR puis NOW) ;
 * 4. confirmation ([ConfirmationPolicy]) — seulement avant un long tronçon sans manœuvre.
 * Les temps 2 et 4 se taisent dès qu'ils n'apportent rien : une voix qui parle trop n'est plus écoutée.
 */

/** Voies à utiliser pour la prochaine manœuvre, résumées en un côté et un nombre. */
data class LaneAdvice(val side: ManeuverSide?, val count: Int, val total: Int) {
    val phrase: String
        get() {
            val n = when (count) { 2 -> "deux "; 3 -> "trois "; 4 -> "quatre "; else -> "" }
            return when (side) {
                ManeuverSide.RIGHT -> if (count == 1) "Restez sur la voie de droite." else "Restez sur les ${n}voies de droite."
                ManeuverSide.LEFT -> if (count == 1) "Restez sur la voie de gauche." else "Restez sur les ${n}voies de gauche."
                null -> if (count == 1) "Restez sur la voie du milieu." else "Restez sur les voies du milieu."
            }
        }

    companion object {
        /**
         * Résumé des [lanes] (de gauche à droite) : voies conseillées si Valhalla en marque, sinon voies permises. Null quand il
         * n'y a rien à dire : pas de données, toutes les voies conviennent, ou voies utiles non contiguës.
         */
        fun of(lanes: List<Lane>): LaneAdvice? {
            if (lanes.size < 2) return null
            val usable = if (lanes.any { it.active }) lanes.map { it.active } else lanes.map { it.valid }
            val count = usable.count { it }
            if (count == 0 || count == lanes.size) return null
            val first = usable.indexOf(true)
            val last = usable.lastIndexOf(true)
            if (last - first + 1 != count) return null
            val side = when {
                last == lanes.lastIndex -> ManeuverSide.RIGHT
                first == 0 -> ManeuverSide.LEFT
                else -> null
            }
            return LaneAdvice(side, count, lanes.size)
        }
    }
}

/** Temps 2 : « Restez sur la voie de droite. » / « Serrez à droite. », dit entre l'anticipation et la manœuvre. */
object PositioningPolicy {
    /** Rien n'est dit si l'annonce précédente du guidage date de moins de ceci : jamais deux consignes coup sur coup. */
    const val MIN_SILENCE_MS = 6_000L

    private val sidedExits = mapOf(
        ManeuverType.EXIT_RIGHT to ManeuverSide.RIGHT, ManeuverType.RAMP_RIGHT to ManeuverSide.RIGHT,
        ManeuverType.EXIT_LEFT to ManeuverSide.LEFT, ManeuverType.RAMP_LEFT to ManeuverSide.LEFT,
    )

    /**
     * Consigne de placement pour [m] ; null = rien à dire. Les voies réelles priment ; sans elles, seules les sorties et bretelles
     * reçoivent « Serrez à … » (une bifurcation dit déjà son côté dans sa propre instruction).
     */
    fun phrase(m: Maneuver): String? {
        val advice = LaneAdvice.of(m.lanes)
        // Voies et manœuvre en désaccord (sortie à droite, voies conseillées à gauche) : donnée douteuse, la voix n'en dit rien.
        if (advice != null && conflicts(advice, m.type)) return null
        return advice?.phrase ?: when (sidedExits[m.type]) {
            ManeuverSide.RIGHT -> "Serrez à droite."
            ManeuverSide.LEFT -> "Serrez à gauche."
            null -> null
        }
    }

    private val rightTypes = setOf(ManeuverType.EXIT_RIGHT, ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_RIGHT, ManeuverType.TURN_RIGHT, ManeuverType.SHARP_RIGHT)
    private val leftTypes = setOf(ManeuverType.EXIT_LEFT, ManeuverType.RAMP_LEFT, ManeuverType.KEEP_LEFT, ManeuverType.TURN_LEFT, ManeuverType.SHARP_LEFT)

    /** Les voies conseillées sont toutes du côté opposé à la manœuvre : la carte et l'itinéraire ne disent pas la même chose. */
    fun conflicts(advice: LaneAdvice, type: ManeuverType): Boolean =
        (advice.side == ManeuverSide.LEFT && type in rightTypes) || (advice.side == ManeuverSide.RIGHT && type in leftTypes)

    /**
     * Fenêtre (m) où la consigne est utile à [speedKmh] : assez tôt pour changer de file, assez tard pour ne pas être oubliée,
     * et jamais collée au palier NEAR. Voie rapide : 1000 → 450 m ; route : 350 → 225 m ; ville : 180 → 120 m (voies connues seulement).
     */
    fun window(speedKmh: Double?, scooter: Boolean, hasLanes: Boolean): ClosedFloatingPointRange<Double>? {
        if (scooter) return null
        return when (SpeedClass.of(speedKmh)) {
            SpeedClass.HIGHWAY -> 450.0..1000.0
            SpeedClass.ROAD -> 225.0..350.0
            SpeedClass.CITY -> if (hasLanes) 120.0..180.0 else null
            SpeedClass.SCOOTER -> null
        }
    }

    fun next(m: Maneuver, distanceM: Double, speedKmh: Double?, scooter: Boolean, sinceLastSpeechMs: Long?): String? {
        val text = phrase(m) ?: return null
        val w = window(speedKmh, scooter, m.lanes.isNotEmpty()) ?: return null
        if (distanceM !in w) return null
        if (sinceLastSpeechMs != null && sinceLastSpeechMs < MIN_SILENCE_MS) return null
        return text
    }
}

/** Temps 4 : après une manœuvre, « Continuez pendant 12 kilomètres. » quand rien n'arrive avant longtemps. */
object ConfirmationPolicy {
    /** Tronçon minimal (m) et durée minimale (s) avant la manœuvre suivante pour qu'une confirmation soit dite. */
    const val MIN_STRETCH_M = 1_500.0
    const val MIN_STRETCH_S = 60.0
    /** La confirmation attend que la manœuvre soit vraiment derrière soi. */
    const val DELAY_MS = 3_000L

    fun text(passed: Maneuver, distanceToNextM: Double, speedKmh: Double?, scooter: Boolean): String? {
        if (scooter || passed.type == ManeuverType.ARRIVE) return null
        val mps = (speedKmh ?: 0.0).coerceAtLeast(0.0) / 3.6
        if (distanceToNextM < maxOf(MIN_STRETCH_M, mps * MIN_STRETCH_S)) return null
        return passed.verbalPost?.takeIf { it.isNotBlank() }
    }
}

/**
 * Sortie de rond-point : quand le décompte des sorties est fiable, « Quittez le rond-point » est dit dès que la sortie précédente
 * est passée — ni avant (le conducteur prendrait la mauvaise), ni au seul jugé d'une distance qui ne distingue pas deux sorties voisines.
 */
object RoundaboutVoice {
    /** null = pas de décompte fiable (règle de distance habituelle) ; true = dire « sortez » maintenant ; false = attendre. */
    fun exitGate(nextType: ManeuverType, live: RoundaboutLive?): Boolean? {
        if (nextType != ManeuverType.ROUNDABOUT_EXIT || live == null || live.phase != RoundaboutPhase.INSIDE) return null
        if (!live.layout.counted || live.layout.intermediateCount == 0) return null
        // Décompte gelé (position douteuse) et sortie pas encore acquise : on ne sait pas, la règle de distance reprend la main
        // (elle refuse elle-même « maintenant » sur une position extrapolée ou trop imprécise).
        if (live.confidence == RoundaboutConfidence.LOW && !live.exitIsNext) return null
        return live.exitIsNext
    }
}
