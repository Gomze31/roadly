package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.getOrNull
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.box
import fr.roadly.core.model.HazardKind
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.reportsAhead
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import kotlin.math.roundToInt

private val NO_ARGS = emptyList<ToolParam>()
private fun guiding(session: NavigationSession) = session.state.value as? NavigationState.Guiding
private val NO_GUIDANCE = ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours."))

class GetNavigationStatusTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val community: CommunityRepository,
) : AssistantTool {
    override val schema = ToolSchema("getNavigationStatus", "État réel du guidage : destination, distance et durée restantes, heure d'arrivée, prochaine instruction, arrêts, virages et zones de vigilance à venir, pluie, signalements à venir (avec report_id). Le trafic est inconnu.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(notGuiding(session.state.value))
        val reports = if (community.isConfigured.first()) community.nearby(g.progress.snapped.box()).getOrNull()?.let { reportsAhead(g, it) }.orEmpty() else null
        return ToolOutput.Done(ToolJson.ok {
            put("guiding", true)
            guidance.destinationName?.let { put("destination", it) }
            putProgress(g, ctx.zone)
            putJsonArray("stops") {
                guidance.remainingStops().forEach { s -> addJsonObject { put("stop_id", s.id); put("name", s.place.name); put("in_km", ToolFormat.km(s.alongM - g.progress.distanceAlongM)) } }
            }
            putJsonArray("hazards_ahead") {
                g.hazards.filter { it.startAlongM > g.progress.distanceAlongM }.sortedBy { it.startAlongM }.take(5).forEach { h ->
                    addJsonObject {
                        put("kind", if (h.kind == HazardKind.CURVE) "virage serré" else "zone de vigilance")
                        put("in_m", (h.startAlongM - g.progress.distanceAlongM).roundToInt())
                        h.speedLimitKmh?.let { put("limit_kmh", it) }
                    }
                }
            }
            g.raining?.let { put("raining", it) }
            if (reports == null) put("community_reports", "non configuré")
            else putJsonArray("reports_ahead") { reports.take(5).forEach { r -> addJsonObject { put("report_id", r.report.id); put("kind", r.report.kind.name); put("in_m", r.distanceM.roundToInt()) } } }
            put("traffic", "inconnu (Roadly n'a pas de source de trafic)")
        })
    }
}

class GetNextInstructionTool @Inject constructor(private val session: NavigationSession) : AssistantTool {
    override val schema = ToolSchema("getNextInstruction", "Prochaine manœuvre du guidage et la distance pour l'atteindre, puis les deux suivantes.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        val m = g.route.maneuvers.getOrNull(g.progress.nextManeuverIndex) ?: return ToolOutput.Done(ToolJson.unavailable("Aucune manœuvre restante."))
        return ToolOutput.Done(ToolJson.ok {
            put("instruction", m.verbalPre ?: m.instruction)
            put("in_m", g.progress.distanceToNextManeuverM.roundToInt())
            m.streetName?.let { put("street", it) }
            m.exitNumber?.let { put("exit", it) }
            putJsonArray("then") { g.upcoming.take(2).forEach { u -> addJsonObject { put("instruction", u.instruction) } } }
        })
    }
}

class GetDistanceRemainingTool @Inject constructor(private val session: NavigationSession) : AssistantTool {
    override val schema = ToolSchema("getDistanceRemaining", "Distance et durée restantes jusqu'à la destination, d'après le moteur de guidage.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(ToolJson.ok {
            put("remaining_km", ToolFormat.km(g.progress.remainingDistanceM))
            put("remaining_min", ToolFormat.minutes(g.progress.remainingDurationS))
        })
    }
}

class GetArrivalTimeTool @Inject constructor(private val session: NavigationSession) : AssistantTool {
    override val schema = ToolSchema("getArrivalTime", "Heure d'arrivée estimée par le moteur de guidage (sans trafic prédictif).", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(ToolJson.ok {
            put("arrival_time", ToolFormat.clock(g.etaEpochMs, ctx.zone))
            put("remaining_min", ToolFormat.minutes(g.progress.remainingDurationS))
        })
    }
}

/** `startNavigation` et `changeDestination` : même résolution, même confirmation, même démarrage. */
class DestinationTool(
    name: String,
    description: String,
    private val verb: String,
    private val resolver: DestinationResolver,
    private val guidance: GuidanceActions,
    private val navigator: AppNavigator,
) : AssistantTool {
    override val schema = ToolSchema(name, description, listOf(
        ToolParam("destination", ParamType.STRING, "Destination telle que dite par l'utilisateur (adresse, lieu, « maison », « travail »).", required = false),
        ToolParam("placeId", ParamType.STRING, "place_id d'un lieu déjà renvoyé par un outil.", required = false),
    ))

    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val query = args.string("destination")
        val candidates = resolver.resolve(query, args.string("placeId"))
        val place = candidates.firstOrNull()
            ?: return ToolOutput.Done(ToolJson.error("not_found", "Aucun lieu trouvé pour « ${query.orEmpty()} » ; demandez une précision (ville, adresse)."))
        val details = buildJsonObject {
            putPlace("destination", place)
            putJsonArray("other_candidates") { candidates.drop(1).forEach { c -> addJsonObject { put("place_id", c.id); put("name", c.name); c.address?.let { put("address", it) } } } }
        }
        return ToolOutput.NeedsConfirmation("$verb ${place.name}" + (place.address?.let { " ($it)" } ?: ""), details) {
            when (val r = guidance.startTo(place)) {
                is Outcome.Success -> {
                    navigator.navigate(NavRequest.OpenGuidance)
                    ToolJson.ok { put("destination", place.name); putOption(r.value, ctx.zone) }
                }
                is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            }
        }
    }
}

class StopNavigationTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("stopNavigation", "Arrête le guidage en cours (après confirmation).", NO_ARGS)
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        return ToolOutput.NeedsConfirmation("Arrêter le guidage" + (guidance.destinationName?.let { " vers $it" } ?: "")) {
            guidance.stop()
            ToolJson.ok { put("stopped", true) }
        }
    }
}

class RecalculateRouteTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("recalculateRoute", "Recalcule l'itinéraire depuis la position actuelle, mêmes options et arrêts.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        return ToolOutput.Done(when (val r = guidance.recalculate()) {
            is Outcome.Success -> ToolJson.ok { putOption(r.value, ctx.zone) }
            is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
        })
    }
}

class EndConversationTool @Inject constructor() : AssistantTool {
    override val schema = ToolSchema(NAME, "L'utilisateur a terminé (« merci, c'est tout ») : la conversation se ferme après ta réponse.", NO_ARGS)
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.Done(ToolJson.ok { put("closing", true) })
    companion object { const val NAME = "endConversation" }
}
