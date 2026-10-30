package fr.roadly.core.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal data class ValhallaResponse(val trip: ValhallaTrip)
@Serializable internal data class ValhallaTrip(val legs: List<ValhallaLeg> = emptyList(), val summary: ValhallaSummary = ValhallaSummary())
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
)
@Serializable internal data class ValhallaError(@SerialName("error_code") val errorCode: Int = 0, val error: String = "")
