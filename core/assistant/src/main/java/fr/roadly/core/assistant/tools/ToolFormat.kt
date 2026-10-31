package fr.roadly.core.assistant.tools

import fr.roadly.core.common.AppError
import fr.roadly.core.model.Place
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteOption
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal object ToolFormat {
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
    fun clock(epochMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(HHMM)
    fun minutes(seconds: Double): Int = (seconds / 60.0).roundToInt()
    fun km(meters: Double): Double = (meters / 100.0).roundToInt() / 10.0
    fun describe(e: AppError): String = when (e) {
        AppError.Network -> "réseau indisponible"
        AppError.Timeout -> "le service ne répond pas"
        AppError.RateLimited -> "service saturé, réessayer plus tard"
        AppError.PermissionDenied -> "position indisponible (permission de localisation)"
        AppError.NoResult -> "aucun résultat"
        is AppError.Unknown -> if (e.detail == "max_stops") "10 arrêts maximum" else "erreur inattendue"
        else -> "service indisponible"
    }
}

internal fun JsonObjectBuilder.putPlace(key: String, place: Place) = putJsonObject(key) {
    put("place_id", place.id)
    put("name", place.name)
    place.address?.let { put("address", it) }
}

internal fun JsonObjectBuilder.putOption(option: RouteOption, zone: ZoneId) {
    put("duration_min", ToolFormat.minutes(option.route.durationSeconds))
    put("distance_km", ToolFormat.km(option.route.distanceMeters))
    put("arrival_time", ToolFormat.clock(option.arrivalEpochMs, zone))
    put("has_toll", option.route.hasToll)
}

internal fun JsonObjectBuilder.putProgress(g: NavigationState.Guiding, zone: ZoneId) {
    put("remaining_km", ToolFormat.km(g.progress.remainingDistanceM))
    put("remaining_min", ToolFormat.minutes(g.progress.remainingDurationS))
    put("arrival_time", ToolFormat.clock(g.etaEpochMs, zone))
    g.route.maneuvers.getOrNull(g.progress.nextManeuverIndex)?.let { m ->
        put("next_instruction", m.verbalPre ?: m.instruction)
        put("next_in_m", g.progress.distanceToNextManeuverM.roundToInt())
        m.streetName?.let { put("next_street", it) }
    }
    g.speedLimitKmh?.let { put("speed_limit_kmh", it) }
    if (g.rerouting) put("rerouting", true)
    if (g.gpsLost) put("gps_lost", true)
}

internal fun notGuiding(state: NavigationState): JsonObject = when (state) {
    is NavigationState.Arrived -> ToolJson.ok { put("guiding", false); put("arrived", true) }
    is NavigationState.Failed -> ToolJson.ok { put("guiding", false); put("last_error", ToolFormat.describe(state.error)) }
    else -> ToolJson.ok { put("guiding", false) }
}
