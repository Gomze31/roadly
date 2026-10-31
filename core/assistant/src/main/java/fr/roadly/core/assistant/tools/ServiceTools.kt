package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.getOrNull
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.routing.FuelCostCalculator
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

private fun guiding(session: NavigationSession) = session.state.value as? NavigationState.Guiding
private fun here(session: NavigationSession, location: LocationRepository): GeoPoint? = guiding(session)?.progress?.snapped ?: location.lastKnown?.point
private val NO_POSITION = ToolOutput.Done(ToolJson.unavailable("Position inconnue."))
private val FUEL_PARAM = ToolParam("fuel", ParamType.STRING, "Carburant ; par défaut celui du profil véhicule.", required = false, enumValues = FuelType.entries.map { it.name })
private fun Double.cents(): Double = (this * 1000).roundToInt() / 1000.0
private fun hoursAgo(epochMs: Long?, nowMs: Long): Int? = epochMs?.let { ((nowMs - it) / 3_600_000L).toInt().coerceAtLeast(0) }

/**
 * Résultat agrégé de plusieurs appels fournisseur : [failed] appels sur [total] ont échoué. Un échec n'est jamais
 * présenté comme « rien trouvé » : [allFailed] → erreur, sinon les résultats gardés sont marqués `partial`.
 */
private class Gathered<T>(val values: List<T>, val failed: Int, val total: Int, val firstError: AppError?) {
    val allFailed: Boolean get() = total > 0 && failed == total
    val partial: Boolean get() = failed in 1 until total
}

private fun <T> List<Outcome<List<T>>>.gathered() = Gathered(
    values = flatMap { it.getOrNull().orEmpty() },
    failed = count { it is Outcome.Failure },
    total = size,
    firstError = firstNotNullOfOrNull { (it as? Outcome.Failure)?.error },
)

/** Lieux OSM proches ; hors guidage ouvre la recherche filtrée, en guidage les affiche dans la fenêtre compacte. */
class ShowNearbyTool(
    name: String,
    description: String,
    private val categories: List<PoiCategory>,
    private val title: String,
    private val session: NavigationSession,
    private val nearby: NearbyPoiProvider,
    private val location: LocationRepository,
    private val navigator: AppNavigator,
    private val cards: AssistantCards,
    private val places: ToolPlaces,
) : AssistantTool {
    override val schema = ToolSchema(name, description)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session)
        val at = here(session, location) ?: return NO_POSITION
        val gathered = categories.map { nearby.nearby(it, at, 10) }.gathered()
        if (gathered.allFailed) return ToolOutput.Done(ToolJson.error("nearby_unavailable", ToolFormat.describe(gathered.firstError ?: AppError.NoResult)))
        val found = gathered.values.distinctBy { it.id }.sortedBy { at.distanceTo(it.point) }.take(5)
        if (g == null) navigator.navigate(NavRequest.OpenSearch(categories.first().name))
        else if (found.isNotEmpty()) cards.show(ResultCard(title, found.map { CardRow(it.name, Formatters.distance(at.distanceTo(it.point)), places.remember(it)) }))
        return ToolOutput.Done(ToolJson.ok {
            put("opened_search", g == null)
            if (gathered.partial) put("partial", true)
            putJsonArray("places") {
                found.forEach { p -> addJsonObject { put("place_id", places.remember(p)); put("name", p.name); put("distance_m", at.distanceTo(p.point).roundToInt()); p.address?.let { put("address", it) } } }
            }
            if (found.isEmpty()) put("message", "Aucun lieu trouvé à proximité (données OpenStreetMap).")
        })
    }
}

class GetFuelPricesTool @Inject constructor(
    private val session: NavigationSession,
    private val stations: FuelStationProvider,
    private val vehicles: VehicleProfileRepository,
    private val location: LocationRepository,
    private val places: ToolPlaces,
) : AssistantTool {
    override val schema = ToolSchema("getFuelPrices", "Prix publiés (flux officiel prix-carburants) des stations à 10 km, du moins cher au plus cher, avec leur fraîcheur.", listOf(FUEL_PARAM))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val fuel = args.string("fuel")?.let(FuelType::valueOf) ?: vehicles.profile.first()?.fuel
            ?: return ToolOutput.Done(ToolJson.error("fuel_unknown", "Carburant inconnu : demandez-le ou renseignez le profil véhicule."))
        if (fuel == FuelType.ELECTRIC) return ToolOutput.Done(ToolJson.unavailable("Aucune source de prix de recharge."))
        val at = here(session, location) ?: return NO_POSITION
        return ToolOutput.Done(when (val r = stations.nearby(at, radiusKm = 10)) {
            is Outcome.Failure -> ToolJson.error("prices_unavailable", ToolFormat.describe(r.error))
            is Outcome.Success -> {
                val priced = r.value.mapNotNull { s -> s.price(fuel)?.let { s to it } }.sortedBy { it.second.eurPerLitre }.take(5)
                ToolJson.ok {
                    put("fuel", fuel.name)
                    putJsonArray("stations") {
                        priced.forEach { (s, p) ->
                            addJsonObject {
                                put("place_id", places.remember(s.toPlace())); put("name", s.name); put("price_eur", p.eurPerLitre.cents())
                                put("distance_m", at.distanceTo(s.point).roundToInt())
                                hoursAgo(p.updatedAtEpochMs, ctx.nowMs)?.let { put("updated_hours_ago", it) }
                            }
                        }
                    }
                    if (priced.isEmpty()) put("message", "Aucune station avec un prix publié pour ce carburant dans un rayon de 10 km.")
                }
            }
        })
    }
}

/** Stations à ≤ 1,5 km du tracé sur les 50 km à venir, triées par prix ; détour réel calculé pour les 3 moins chères. */
class FindCheapestFuelOnRouteTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val stations: FuelStationProvider,
    private val vehicles: VehicleProfileRepository,
    private val routing: RoutingProvider,
    private val prefs: UserPreferencesRepository,
    private val cards: AssistantCards,
    private val places: ToolPlaces,
) : AssistantTool {
    override val schema = ToolSchema("findCheapestFuelOnRoute", "Trouve les stations les moins chères sur les 50 km à venir du trajet, avec prix, fraîcheur et détour réel en minutes ; propose ensuite addStop avec le place_id.", listOf(FUEL_PARAM))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours : utilisez getFuelPrices pour les stations autour."))
        val fuel = args.string("fuel")?.let(FuelType::valueOf) ?: vehicles.profile.first()?.fuel
            ?: return ToolOutput.Done(ToolJson.error("fuel_unknown", "Carburant inconnu : demandez-le ou renseignez le profil véhicule."))
        val tracker = RouteTracker(g.route)
        val geo = RouteGeometry(g.route.geometry)
        val start = g.progress.distanceAlongM
        val end = min(start + SEARCH_AHEAD_M, geo.totalM)
        val samples = generateSequence(start) { it + SAMPLE_STEP_M }.takeWhile { it <= end }.map { tracker.pointAt(it) }.toList() + tracker.pointAt(end)
        val gathered = samples.map { stations.nearby(it, radiusKm = 3, limit = 20) }.gathered()
        if (gathered.allFailed) return ToolOutput.Done(ToolJson.error("fuel_unavailable", ToolFormat.describe(gathered.firstError ?: AppError.NoResult)))
        val candidates = gathered.values.distinctBy { it.id }
            .mapNotNull { s ->
                val price = s.price(fuel) ?: return@mapNotNull null
                val p = geo.project(s.point) ?: return@mapNotNull null
                if (p.offsetM > MAX_OFFSET_M || p.alongM < start) null else Triple(s, price, p.alongM)
            }
            .sortedBy { it.second.eurPerLitre }.take(3)
        val preferences = prefs.routePreferences.first()
        val vehicle = vehicles.profile.first()
        val stops = guidance.remainingStops()
        val rows = candidates.map { (s, price, along) ->
            val waypoints = (stops.map { it.place.point to it.alongM } + (s.point to along)).sortedBy { it.second }.map { it.first }
            val request = RouteRequest(origin = g.progress.snapped, destination = g.route.geometry.last(), waypoints = waypoints, costing = g.route.costing, preferences = preferences, originHeadingDeg = g.progress.bearingDeg, vehicle = vehicle)
            val detourMin = routing.route(request).getOrNull()?.firstOrNull()?.let { ToolFormat.minutes(it.durationSeconds - g.progress.remainingDurationS).coerceAtLeast(0) }
            Triple(s, price, along) to detourMin
        }
        if (rows.isNotEmpty()) cards.show(ResultCard("Carburant sur le trajet", rows.map { (t, detour) ->
            CardRow(t.first.name, "${t.second.eurPerLitre.cents()} € · " + (detour?.let { "+$it min" } ?: "détour inconnu"), places.remember(t.first.toPlace()))
        }))
        return ToolOutput.Done(ToolJson.ok {
            put("fuel", fuel.name)
            if (gathered.partial) put("partial", true)
            putJsonArray("stations") {
                rows.forEach { (t, detour) ->
                    val (s, price, along) = t
                    addJsonObject {
                        put("place_id", places.remember(s.toPlace())); put("name", s.name); put("price_eur", price.eurPerLitre.cents())
                        put("in_km", ToolFormat.km(along - start))
                        if (detour != null) put("detour_min", detour) else put("detour_unknown", true)
                        hoursAgo(price.updatedAtEpochMs, ctx.nowMs)?.let { put("updated_hours_ago", it) }
                    }
                }
            }
            if (rows.isEmpty()) put("message", "Aucune station avec un prix publié pour ce carburant près du trajet sur les 50 km à venir.")
        })
    }

    private companion object {
        const val SEARCH_AHEAD_M = 50_000.0
        const val SAMPLE_STEP_M = 10_000.0
        const val MAX_OFFSET_M = 1_500.0
    }
}

class GetEstimatedTripCostTool @Inject constructor(private val session: NavigationSession, private val vehicles: VehicleProfileRepository) : AssistantTool {
    override val schema = ToolSchema("getEstimatedTripCost", "Coût carburant estimé de la distance restante d'après le profil véhicule (consommation et prix saisis). Péages non estimés.")
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours."))
        val profile = vehicles.profile.first() ?: return ToolOutput.Done(ToolJson.unavailable("Aucun profil véhicule : à créer dans Réglages."))
        val cost = FuelCostCalculator.estimate(g.progress.remainingDistanceM, profile)
            ?: return ToolOutput.Done(ToolJson.unavailable("Consommation ou prix du carburant non renseignés dans le profil."))
        return ToolOutput.Done(ToolJson.ok {
            put("fuel_eur", (cost.fuelEur * 100).roundToInt() / 100.0)
            put("remaining_km", ToolFormat.km(g.progress.remainingDistanceM))
            put("tolls", "non estimés (aucune source de tarifs de péage)")
        })
    }
}

class ShowTrafficTool @Inject constructor() : AssistantTool {
    override val schema = ToolSchema("showTraffic", "Trafic en temps réel : indisponible dans Roadly.")
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext) =
        ToolOutput.Done(ToolJson.unavailable("Roadly n'a pas de source de trafic en temps réel : impossible d'afficher ou d'estimer le trafic."))
}

class ReportIncidentTool @Inject constructor(
    private val session: NavigationSession,
    private val location: LocationRepository,
    private val community: CommunityRepository,
) : AssistantTool {
    override val schema = ToolSchema("reportIncident", "Signale un événement à la position actuelle (service communautaire Roadly).", listOf(
        ToolParam("type", ParamType.STRING, "Type d'événement.", enumValues = ReportKind.entries.map { it.name }),
    ))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (!community.isConfigured.first()) return ToolOutput.Done(ToolJson.unavailable("Signalements communautaires non configurés (Réglages → Communauté)."))
        val at = here(session, location) ?: return NO_POSITION
        val heading = guiding(session)?.progress?.bearingDeg ?: location.lastKnown?.bearingDeg
        return ToolOutput.Done(when (val r = community.report(ReportKind.valueOf(args.string("type")!!), at, heading)) {
            is Outcome.Success -> ToolJson.ok { put("report_id", r.value.id) }
            is Outcome.Failure -> ToolJson.error("report_failed", ToolFormat.describe(r.error))
        })
    }
}

class ConfirmIncidentTool @Inject constructor(private val community: CommunityRepository) : AssistantTool {
    override val schema = ToolSchema("confirmIncident", "Confirme qu'un signalement à venir (report_id de getNavigationStatus) est toujours présent.", listOf(
        ToolParam("reportId", ParamType.STRING, "report_id du signalement."),
    ))
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (!community.isConfigured.first()) return ToolOutput.Done(ToolJson.unavailable("Signalements communautaires non configurés."))
        return ToolOutput.Done(when (val r = community.vote(args.string("reportId")!!, true)) {
            is Outcome.Success -> ToolJson.ok { put("confirmations", r.value.confirmations) }
            is Outcome.Failure -> ToolJson.error("vote_failed", ToolFormat.describe(r.error))
        })
    }
}

class GetVehicleProfileTool @Inject constructor(private val vehicles: VehicleProfileRepository) : AssistantTool {
    override val schema = ToolSchema("getVehicleProfile", "Profil véhicule enregistré (type, carburant, consommation, prix saisi).")
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val p = vehicles.profile.first() ?: return ToolOutput.Done(ToolJson.unavailable("Aucun profil véhicule enregistré."))
        return ToolOutput.Done(ToolJson.ok {
            put("type", p.type.name); put("fuel", p.fuel.name); put("consumption_per_100", p.consumptionPer100)
            p.fuelPriceEur?.let { put("fuel_price_eur", it) }
            p.critAir?.let { put("crit_air", it.name) }
        })
    }
}

class ChangeVehicleProfileTool @Inject constructor(private val vehicles: VehicleProfileRepository) : AssistantTool {
    override val schema = ToolSchema("changeVehicleProfile", "Change le type (et éventuellement le carburant) du profil véhicule unique, après confirmation ; les itinéraires suivants en tiennent compte.", listOf(
        ToolParam("type", ParamType.STRING, "Type de véhicule.", enumValues = VehicleType.entries.map { it.name }),
        FUEL_PARAM,
    ))
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val current = vehicles.profile.first() ?: return ToolOutput.Done(ToolJson.unavailable("Aucun profil véhicule : à créer dans Réglages."))
        val next = current.copy(type = VehicleType.valueOf(args.string("type")!!), fuel = args.string("fuel")?.let(FuelType::valueOf) ?: current.fuel)
        return ToolOutput.NeedsConfirmation("Passer le profil véhicule en ${next.type.name.lowercase()} (${next.fuel.name})") {
            vehicles.save(next)
            ToolJson.ok { put("type", next.type.name); put("fuel", next.fuel.name) }
        }
    }
}
