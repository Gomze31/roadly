package fr.roadly.core.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable internal data class ValhallaResponse(val trip: ValhallaTrip)
@Serializable internal data class ValhallaTrip(val legs: List<ValhallaLeg> = emptyList(), val summary: ValhallaSummary = ValhallaSummary(), val locations: List<ValhallaLocation> = emptyList())
/** `original_index` n'est renvoyé que par `/optimized_route` : position du point dans la demande. */
@Serializable internal data class ValhallaLocation(
    @SerialName("original_index") val originalIndex: Int? = null,
    @SerialName("side_of_street") val sideOfStreet: String? = null,
)
@Serializable internal data class ValhallaSummary(val length: Double = 0.0, val time: Double = 0.0, @SerialName("has_toll") val hasToll: Boolean = false)
@Serializable internal data class ValhallaLeg(val shape: String = "", val maneuvers: List<ValhallaManeuver> = emptyList(), val summary: ValhallaSummary = ValhallaSummary())
@Serializable internal data class ValhallaManeuver(
    val type: Int = 0,
    val instruction: String = "",
    val length: Double = 0.0,
    val time: Double = 0.0,
    @SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
    @SerialName("street_names") val streetNames: List<String> = emptyList(),
    @SerialName("roundabout_exit_count") val roundaboutExitCount: Int? = null,
    val toll: Boolean = false,
    @SerialName("verbal_pre_transition_instruction") val verbalPre: String? = null,
    @SerialName("verbal_transition_alert_instruction") val verbalAlert: String? = null,
    @SerialName("verbal_post_transition_instruction") val verbalPost: String? = null,
    @SerialName("end_shape_index") val endShapeIndex: Int? = null,
    @SerialName("bearing_before") val bearingBefore: Int? = null,
    @SerialName("bearing_after") val bearingAfter: Int? = null,
    /** Brut : le format varie selon les versions de Valhalla ; décodé de façon tolérante par [ValhallaLanes]. */
    val lanes: List<JsonElement> = emptyList(),
    val sign: ValhallaSign? = null,
)
@Serializable internal data class ValhallaSign(
    @SerialName("exit_number_elements") val exitNumberElements: List<ValhallaSignElement> = emptyList(),
    @SerialName("exit_branch_elements") val exitBranchElements: List<ValhallaSignElement> = emptyList(),
    @SerialName("exit_toward_elements") val exitTowardElements: List<ValhallaSignElement> = emptyList(),
)
@Serializable internal data class ValhallaSignElement(val text: String = "")
@Serializable internal data class ValhallaError(@SerialName("error_code") val errorCode: Int = 0, val error: String = "")
