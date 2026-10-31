package fr.roadly.core.routing

import fr.roadly.core.model.ManeuverType

/** Types de manœuvre Valhalla (`odin`, 0–38) → [ManeuverType], côté conservé (B3) ; public pour les fixtures de test des autres modules. */
object ValhallaManeuverTypes {
    fun of(t: Int): ManeuverType = when (t) {
        1, 2, 3 -> ManeuverType.DEPART
        4, 5, 6 -> ManeuverType.ARRIVE
        7, 8, 22 -> ManeuverType.CONTINUE
        9 -> ManeuverType.SLIGHT_RIGHT
        10 -> ManeuverType.TURN_RIGHT
        11 -> ManeuverType.SHARP_RIGHT
        12 -> ManeuverType.UTURN_RIGHT
        13 -> ManeuverType.UTURN_LEFT
        14 -> ManeuverType.SHARP_LEFT
        15 -> ManeuverType.TURN_LEFT
        16 -> ManeuverType.SLIGHT_LEFT
        17 -> ManeuverType.RAMP
        18 -> ManeuverType.RAMP_RIGHT
        19 -> ManeuverType.RAMP_LEFT
        20 -> ManeuverType.EXIT_RIGHT
        21 -> ManeuverType.EXIT_LEFT
        23 -> ManeuverType.KEEP_RIGHT
        24 -> ManeuverType.KEEP_LEFT
        25 -> ManeuverType.MERGE
        26 -> ManeuverType.ROUNDABOUT_ENTER
        27 -> ManeuverType.ROUNDABOUT_EXIT
        28, 29 -> ManeuverType.FERRY
        37 -> ManeuverType.MERGE_RIGHT
        38 -> ManeuverType.MERGE_LEFT
        else -> ManeuverType.OTHER
    }
}
