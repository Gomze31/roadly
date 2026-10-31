package fr.roadly.core.routing

import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Décodage des voies Valhalla (`maneuvers[].lanes`) ; masques de `baldr/graphconstants.h` (`kTurnLane*`). */
internal object ValhallaLanes {
    private const val THROUGH = 1 shl 1
    private const val SHARP_LEFT = 1 shl 2
    private const val LEFT = 1 shl 3
    private const val SLIGHT_LEFT = 1 shl 4
    private const val SLIGHT_RIGHT = 1 shl 5
    private const val RIGHT = 1 shl 6
    private const val SHARP_RIGHT = 1 shl 7
    private const val REVERSE = 1 shl 8
    private const val MERGE_TO_LEFT = 1 shl 9
    private const val MERGE_TO_RIGHT = 1 shl 10

    /** `NONE` (1) et 0 ne donnent aucune direction : la voie est dessinée « tout droit » par l'écran. */
    fun directions(mask: Int): Set<LaneDirection> = buildSet {
        if (mask and (SHARP_LEFT or LEFT) != 0) add(LaneDirection.LEFT)
        if (mask and SLIGHT_LEFT != 0) add(LaneDirection.SLIGHT_LEFT)
        if (mask and THROUGH != 0) add(LaneDirection.STRAIGHT)
        if (mask and SLIGHT_RIGHT != 0) add(LaneDirection.SLIGHT_RIGHT)
        if (mask and (RIGHT or SHARP_RIGHT) != 0) add(LaneDirection.RIGHT)
        if (mask and REVERSE != 0) add(LaneDirection.UTURN)
        if (mask and MERGE_TO_LEFT != 0) add(LaneDirection.MERGE_LEFT)
        if (mask and MERGE_TO_RIGHT != 0) add(LaneDirection.MERGE_RIGHT)
    }

    /** Éléments mal formés ignorés ; jamais d'exception. */
    fun parse(elements: List<JsonElement>): List<Lane> = elements.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val mask = (o["directions"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        // B3 : `active` (voie conseillée) est distinct de `valid` ; une voie active est aussi valide.
        Lane(directions(mask), valid = o["valid"].isSet() || o["active"].isSet(), active = o["active"].isSet())
    }

    private fun JsonElement?.isSet(): Boolean {
        val p = this as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: false
    }
}
