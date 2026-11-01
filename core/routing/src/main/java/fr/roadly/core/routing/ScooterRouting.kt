package fr.roadly.core.routing

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.ScooterRouteRequest
import fr.roadly.core.scooter.analysis.RankInput
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysis
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysisBuilder
import fr.roadly.core.scooter.analysis.ScooterRouteRanking
import fr.roadly.core.scooter.geo.Polyline6Encoder
import fr.roadly.core.scooter.osm.Overpass
import fr.roadly.core.scooter.range.EnergyLeg
import fr.roadly.core.scooter.range.RangeCalibration
import fr.roadly.core.scooter.range.RangeEstimate
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import fr.roadly.core.scooter.routing.ForbiddenAreas
import fr.roadly.core.scooter.routing.ScooterRoutingProfile
import fr.roadly.core.scooter.valhalla.TraceAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Informations trottinette d'une option d'itinéraire. [analysisError] non nul = analyse des voies indisponible. */
data class ScooterOptionInfo(
    val goal: ScooterRouteGoal,
    val analysis: ScooterRouteAnalysis?,
    val range: RangeEstimate,
    /** Classement d'après les données (« Le plus sécurisé »…). */
    val badges: List<String>,
    val analysisError: AppError? = null,
    /** Un second calcul a contourné des portions interdites détectées sur le premier tracé. */
    val reroutedAroundForbidden: Boolean = false,
)

/** Analyse des voies d'un itinéraire trottinette. */
interface ScooterRouteAnalyzer {
    suspend fun analyze(route: Route, request: ScooterRouteRequest, withOsmTags: Boolean): Outcome<ScooterRouteAnalysis>
}

/**
 * Valhalla `/trace_attributes` (une requête par itinéraire, `edge_walk` sur le tracé calculé) puis, si demandé,
 * Overpass pour les tags OSM des voies (une requête par itinéraire, tags mis en cache). Échec Overpass : analyse
 * conservée sans tags (agglomération, éclairage et restrictions locales restent alors « inconnus »).
 */
@Singleton
class ValhallaScooterRouteAnalyzer @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : ScooterRouteAnalyzer {
    private val tagCache = object : LinkedHashMap<Long, Map<String, String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Map<String, String>>?) = size > 5_000
    }

    override suspend fun analyze(route: Route, request: ScooterRouteRequest, withOsmTags: Boolean): Outcome<ScooterRouteAnalysis> {
        val cfg = try { config.config.first() } catch (e: CancellationException) { throw e } catch (e: Exception) { return Outcome.Failure(AppError.ProviderUnavailable("valhalla")) }
        val url = "${cfg.valhallaBaseUrl.trimEnd('/')}/trace_attributes".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable("valhalla"))
        if (route.geometry.size < 2) return Outcome.Failure(AppError.NoResult)
        val body = TraceAttributes.request(Polyline6Encoder.encode(route.geometry), ScooterRoutingProfile.costingOptions(request))
        val trace = when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> TraceAttributes.parse(json, r.value) ?: return Outcome.Failure(AppError.Parse("trace_attributes"))
            is Outcome.Failure -> return Outcome.Failure(r.error)
        }
        if (trace.edges.isEmpty()) return Outcome.Failure(AppError.NoResult)
        val tags = if (withOsmTags) wayTags(cfg.overpassBaseUrl, trace.edges.mapNotNull { it.wayId }) else null
        return Outcome.Success(ScooterRouteAnalysisBuilder.build(route, trace, tags, request.profile))
    }

    /** Null si Overpass est indisponible (l'analyse le signale) ; voies déjà connues servies depuis le cache. */
    private suspend fun wayTags(base: String, ids: List<Long>): Map<Long, Map<String, String>>? {
        val wanted = ids.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val missing = synchronized(tagCache) { wanted.filter { it !in tagCache } }
        if (missing.isNotEmpty()) {
            val url = "${base.trimEnd('/')}/interpreter".toHttpUrlOrNull() ?: return null
            val fetched = missing.chunked(Overpass.MAX_WAY_IDS).map { chunk ->
                when (val r = api.postForm(url, mapOf("data" to Overpass.wayTagsQuery(chunk)))) {
                    is Outcome.Success -> Overpass.parseWayTags(json, r.value) ?: return null
                    is Outcome.Failure -> return null
                }
            }
            synchronized(tagCache) { fetched.forEach { tagCache.putAll(it) } }
        }
        return synchronized(tagCache) { wanted.mapNotNull { id -> tagCache[id]?.let { id to it } }.toMap() }
    }
}

/**
 * Variantes trottinette : objectif choisi + deux autres, calculées en parallèle ; analyse de chaque tracé ; si des
 * portions interdites sont détectées, un second calcul les exclut (`exclude_polygons`) ; estimation d'autonomie et
 * classement. Les préférences voiture / moto ne sont ni lues ni modifiées.
 */
class ScooterRouteOptionsUseCase @Inject constructor(
    private val routing: RoutingProvider,
    private val analyzer: ScooterRouteAnalyzer,
) {
    data class Params(
        val origin: GeoPoint,
        val destination: GeoPoint,
        val profile: ElectricScooterProfile,
        val preferences: ScooterRoutePreferences,
        val departAtEpochMs: Long,
        val waypoints: List<GeoPoint> = emptyList(),
        val batteryPct: Double? = null,
        val temperatureC: Double? = null,
        val calibration: RangeCalibration = RangeCalibration(),
        val withOsmTags: Boolean = true,
        val variants: Int = 3,
    )

    private data class Computed(val goal: ScooterRouteGoal, val route: Route, val analysis: ScooterRouteAnalysis?, val error: AppError?, val rerouted: Boolean)

    suspend operator fun invoke(p: Params): Outcome<List<RouteOption>> {
        val goals = ScooterRoutingProfile.variantGoals(p.preferences.goal, p.variants)
        val results = coroutineScope { goals.map { g -> async { g to safely { computeVariant(p, g) } } }.map { it.await() } }
        val computed = results.mapNotNull { (_, r) -> (r as? Outcome.Success)?.value }
            .distinctBy { it.route.geometry } // deux objectifs peuvent donner le même tracé : on n'en garde qu'un
        if (computed.isEmpty()) {
            return Outcome.Failure(results.firstNotNullOfOrNull { (_, r) -> (r as? Outcome.Failure)?.error } ?: AppError.NoResult)
        }
        val badges = ScooterRouteRanking.labels(computed.map { RankInput(it.route.id, it.goal, it.route.distanceMeters, it.route.durationSeconds, it.analysis) })
        val estimator = ScooterRangeEstimator(p.profile, p.calibration)
        return Outcome.Success(computed.map { c ->
            RouteOption(
                route = c.route,
                label = goalLabel(c.goal),
                arrivalEpochMs = p.departAtEpochMs + (c.route.durationSeconds * 1000).toLong(),
                cost = null,
                sameAsFastest = false,
                scooter = ScooterOptionInfo(c.goal, c.analysis, estimator.estimate(energyLegs(c.route, c.analysis), p.batteryPct, p.temperatureC), badges[c.route.id].orEmpty(), c.error, c.rerouted),
            )
        })
    }

    private suspend fun computeVariant(p: Params, goal: ScooterRouteGoal): Outcome<Computed> {
        val req = ScooterRouteRequest(p.profile, p.preferences, goal)
        val first = when (val r = route(p, req, emptyList())) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
        }
        val analysis = analyzer.analyze(first, req, p.withOsmTags)
        val a = (analysis as? Outcome.Success)?.value
        if (a != null && a.hasBlockingPortion) {
            val polygons = ForbiddenAreas.polygons(first.geometry, a.segments)
            if (polygons.isNotEmpty()) {
                val second = (route(p, req, polygons) as? Outcome.Success)?.value
                val a2 = second?.let { (analyzer.analyze(it, req, p.withOsmTags) as? Outcome.Success)?.value }
                if (second != null && a2 != null && a2.summary.forbiddenLengthM + a2.summary.notRidableLengthM < a.summary.forbiddenLengthM + a.summary.notRidableLengthM) {
                    return Outcome.Success(Computed(goal, second, a2, null, rerouted = true))
                }
            }
        }
        return Outcome.Success(Computed(goal, first, a, (analysis as? Outcome.Failure)?.error, rerouted = false))
    }

    private suspend fun route(p: Params, req: ScooterRouteRequest, exclude: List<List<GeoPoint>>): Outcome<Route> {
        val costing = if (req.goal == ScooterRouteGoal.SHORTEST) RouteCosting.SHORTEST else RouteCosting.FASTEST
        val r = routing.route(RouteRequest(p.origin, p.destination, p.waypoints, costing, vehicle = null, departAtEpochMs = p.departAtEpochMs, scooter = req, excludePolygons = exclude))
        return when (r) {
            is Outcome.Success -> r.value.firstOrNull()?.let { Outcome.Success(it.copy(costing = costing, scooterGoal = req.goal)) } ?: Outcome.Failure(AppError.NoResult)
            is Outcome.Failure -> r
        }
    }

    private suspend inline fun <T> safely(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(AppError.Unknown(e.message))
    }

    companion object {
        fun goalLabel(g: ScooterRouteGoal) = when (g) {
            ScooterRouteGoal.FASTEST -> "Rapide"
            ScooterRouteGoal.SAFEST -> "Sécurisé"
            ScooterRouteGoal.COMFORT -> "Confortable"
            ScooterRouteGoal.SHORTEST -> "Court"
        }

        /** Énergie : segments analysés (pente, revêtement), sinon profil d'altitude, sinon trajet supposé plat. */
        fun energyLegs(route: Route, analysis: ScooterRouteAnalysis?): List<EnergyLeg> {
            if (analysis != null && analysis.segments.isNotEmpty()) return ScooterRangeEstimator.legsFrom(analysis.segments)
            val e = route.elevationM
            val i = route.elevationIntervalM
            if (e != null && i != null && e.size >= 2) return ScooterRangeEstimator.legsFromElevation(e, i)
            return listOf(EnergyLeg(route.distanceMeters))
        }
    }
}
