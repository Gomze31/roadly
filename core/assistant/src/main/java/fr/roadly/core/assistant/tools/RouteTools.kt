package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

private fun guiding(session: NavigationSession) = session.state.value as? NavigationState.Guiding
private val NO_GUIDANCE = ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours."))
private const val NO_TRAFFIC_NOTE = "Estimation sans trafic prédictif : Roadly n'a pas de source de trafic."

/** `avoidTolls` / `avoidHighways` : enregistre la préférence ; en guidage, recalcule et compare le temps restant avant/après. */
class AvoidOptionTool(
    name: String,
    description: String,
    private val update: (RoutePreferences, Boolean) -> RoutePreferences,
    private val prefs: UserPreferencesRepository,
    private val guidance: GuidanceActions,
    private val session: NavigationSession,
) : AssistantTool {
    override val schema = ToolSchema(name, description, listOf(ToolParam("enabled", ParamType.BOOLEAN, "true pour éviter, false pour autoriser de nouveau.", required = false)))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val enabled = args.bool("enabled") ?: true
        val next = update(prefs.routePreferences.first(), enabled)
        // Temps restant avant le recalcul : `before.route` est la durée complète de l'ancien itinéraire, pas comparable au restant de `after`.
        val remainingBeforeS = guiding(session)?.progress?.remainingDurationS?.toDouble()
        return ToolOutput.Done(when (val r = guidance.applyPreferences(next)) {
            is Outcome.Failure -> ToolJson.error("route_failed", "Préférence enregistrée mais recalcul impossible : ${ToolFormat.describe(r.error)}.")
            is Outcome.Success -> {
                val (before, after) = r.value
                if (before == null || after == null) ToolJson.ok { put("guiding", false); put("saved", true) }
                else ToolJson.ok {
                    put("guiding", true)
                    put("before_min", ToolFormat.minutes(remainingBeforeS ?: before.route.durationSeconds))
                    put("after_min", ToolFormat.minutes(after.route.durationSeconds))
                    put("before_arrival", ToolFormat.clock(before.arrivalEpochMs, ctx.zone))
                    put("after_arrival", ToolFormat.clock(after.arrivalEpochMs, ctx.zone))
                    put("after_has_toll", after.route.hasToll)
                }
            }
        })
    }
}

class FindAlternativeRoutesTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val routes: ToolRoutes,
) : AssistantTool {
    override val schema = ToolSchema("findAlternativeRoutes", "Calcule depuis la position les itinéraires le plus rapide, le plus court et sans péage, avec un route_id chacun.")
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(when (val r = guidance.alternativesFromHere()) {
            is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            is Outcome.Success -> ToolJson.ok {
                put("current_remaining_min", ToolFormat.minutes(g.progress.remainingDurationS.toDouble()))
                putJsonArray("routes") {
                    routes.replace(r.value).forEach { (id, o) -> addJsonObject { put("route_id", id); put("label", o.label); putOption(o, ctx.zone) } }
                }
                put("note", NO_TRAFFIC_NOTE)
            }
        })
    }
}

class SelectRouteTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val routes: ToolRoutes,
) : AssistantTool {
    override val schema = ToolSchema("selectRoute", "Bascule le guidage sur un itinéraire proposé par findAlternativeRoutes.", listOf(ToolParam("routeId", ParamType.STRING, "route_id choisi par l'utilisateur.")))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        val option = routes[args.string("routeId")!!] ?: return ToolOutput.Done(ToolJson.error("unknown_route", "route_id inconnu ; appelez d'abord findAlternativeRoutes."))
        guidance.select(option)
        return ToolOutput.Done(ToolJson.ok { put("label", option.label); putOption(option, ctx.zone) })
    }
}

class SetArrivalTimeTool @Inject constructor(
    private val session: NavigationSession,
    private val resolver: DestinationResolver,
    private val alternatives: RouteAlternativesUseCase,
    private val prefs: UserPreferencesRepository,
    private val vehicles: VehicleProfileRepository,
    private val location: LocationRepository,
) : AssistantTool {
    override val schema = ToolSchema("setArrivalTime", "Arriver à une heure : en guidage, dit si l'heure est tenable ; sinon donne l'heure de départ conseillée vers la destination.", listOf(
        ToolParam("time", ParamType.STRING, "Heure souhaitée, ex. « 18:30 » ou « 18h30 »."),
        ToolParam("destination", ParamType.STRING, "Destination, si aucun guidage n'est en cours.", required = false),
    ))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val target = parseClockTime(args.string("time")!!, ctx.nowMs, ctx.zone)
            ?: return ToolOutput.Done(ToolJson.error("invalid_time", "Heure non comprise ; demandez une heure du type 18h30."))
        guiding(session)?.let { g ->
            val marginMin = ((target - g.etaEpochMs) / 60_000L).toInt()
            return ToolOutput.Done(ToolJson.ok {
                put("on_time", marginMin >= 0)
                put("arrival_time", ToolFormat.clock(g.etaEpochMs, ctx.zone))
                put("margin_min", marginMin)
                put("note", NO_TRAFFIC_NOTE)
            })
        }
        val place = when (val r = resolver.resolve(args.string("destination"), null)) {
            is Outcome.Success -> r.value.firstOrNull()
                ?: return ToolOutput.Done(ToolJson.error("missing_destination", "Aucun guidage en cours : demandez la destination."))
            is Outcome.Failure -> return ToolOutput.Done(ToolJson.error("search_unavailable", "Recherche de lieu indisponible : ${ToolFormat.describe(r.error)}."))
        }
        val origin = location.lastKnown?.point ?: return ToolOutput.Done(ToolJson.unavailable("Position inconnue."))
        return ToolOutput.Done(when (val r = alternatives(origin, place.point, vehicles.profile.first(), ctx.nowMs, prefs.routePreferences.first())) {
            is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            is Outcome.Success -> {
                val fastest = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                val depart = target - (fastest.route.durationSeconds * 1000).toLong()
                ToolJson.ok {
                    putPlace("destination", place)
                    put("depart_time", ToolFormat.clock(depart, ctx.zone))
                    put("duration_min", ToolFormat.minutes(fastest.route.durationSeconds))
                    put("already_late", depart < ctx.nowMs)
                    put("note", NO_TRAFFIC_NOTE)
                }
            }
        })
    }
}

/** « 18:30 », « 18h30 », « 18h », « 18 h 30 » → prochaine occurrence (aujourd'hui, sinon demain). */
internal fun parseClockTime(text: String, nowMs: Long, zone: ZoneId): Long? {
    val m = Regex("""(\d{1,2})\s*[h:]\s*(\d{2})?""").find(text.lowercase()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (h > 23 || min > 59) return null
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var at = now.with(LocalTime.of(h, min))
    if (!at.isAfter(now)) at = at.plusDays(1)
    return at.toInstant().toEpochMilli()
}

class AddStopTool @Inject constructor(
    private val session: NavigationSession,
    private val resolver: DestinationResolver,
    private val guidance: GuidanceActions,
) : AssistantTool {
    override val schema = ToolSchema("addStop", "Ajoute un arrêt au guidage en cours (10 maximum), après confirmation ; renvoie le temps ajouté.", listOf(
        ToolParam("destination", ParamType.STRING, "Lieu de l'arrêt tel que dit par l'utilisateur.", required = false),
        ToolParam("placeId", ParamType.STRING, "place_id d'un lieu déjà renvoyé (ex. station).", required = false),
    ))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours : démarrez d'abord un guidage."))
        val place = when (val r = resolver.resolve(args.string("destination"), args.string("placeId"))) {
            is Outcome.Success -> r.value.firstOrNull()
                ?: return ToolOutput.Done(ToolJson.error("not_found", "Lieu de l'arrêt introuvable."))
            is Outcome.Failure -> return ToolOutput.Done(ToolJson.error("search_unavailable", "Recherche de lieu indisponible : ${ToolFormat.describe(r.error)}."))
        }
        val beforeS = g.progress.remainingDurationS.toDouble()
        return ToolOutput.NeedsConfirmation("Ajouter l'arrêt ${place.name}", buildJsonObject { putPlace("stop", place) }) {
            when (val r = guidance.addStop(place)) {
                is Outcome.Success -> ToolJson.ok {
                    put("stop", place.name)
                    put("extra_min", ToolFormat.minutes(r.value.route.durationSeconds - beforeS).coerceAtLeast(0))
                    putOption(r.value, ctx.zone)
                }
                is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            }
        }
    }
}

class RemoveStopTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("removeStop", "Retire un arrêt du guidage en cours, après confirmation.", listOf(ToolParam("stopId", ParamType.STRING, "stop_id renvoyé par getStops ou getNavigationStatus.")))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        val stop = guidance.remainingStops().firstOrNull { it.id == args.string("stopId") }
            ?: return ToolOutput.Done(ToolJson.error("unknown_stop", "Arrêt inconnu ; appelez getStops."))
        return ToolOutput.NeedsConfirmation("Retirer l'arrêt ${stop.place.name}") {
            when (val r = guidance.removeStop(stop.id)) {
                is Outcome.Success -> ToolJson.ok { put("removed", stop.place.name); putOption(r.value, ctx.zone) }
                is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            }
        }
    }
}

class GetStopsTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("getStops", "Arrêts restants du guidage en cours, dans l'ordre.")
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(ToolJson.ok {
            putJsonArray("stops") {
                guidance.remainingStops().forEach { s -> addJsonObject { put("stop_id", s.id); put("name", s.place.name); put("in_km", ToolFormat.km(s.alongM - g.progress.distanceAlongM)) } }
            }
        })
    }
}
