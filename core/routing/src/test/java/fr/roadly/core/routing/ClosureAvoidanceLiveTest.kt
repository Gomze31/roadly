package fr.roadly.core.routing

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.data.traffic.TrafficKind
import fr.roadly.core.data.traffic.TrafficOnRoute
import fr.roadly.core.data.traffic.TrafficRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Contrôle réel : fermetures en cours publiées par Bison Futé, tracé Valhalla qui les traverse, puis contournement.
 * Inactif sauf avec -ProadlyLive=true ; sans fermeture traversée aujourd'hui, le test est ignoré (rien à vérifier).
 */
class ClosureAvoidanceLiveTest {
    @Test fun realClosuresAreAvoided() = runBlocking {
        assumeTrue(System.getProperty("roadly.live") == "true")
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.IO; override val default = Dispatchers.Default; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT)
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        val api = ApiClient(OkHttpClient(), dispatchers)
        val valhalla = ValhallaRoutingProvider(api, config, Json { ignoreUnknownKeys = true })
        val traffic = TrafficRepository(api)
        val now = System.currentTimeMillis()
        val closures = traffic.events().filter { it.kind == TrafficKind.CLOSURE && (it.startEpochMs ?: 0) <= now && (it.endEpochMs ?: Long.MAX_VALUE) > now }
        println("Fermetures en cours : ${closures.size}")
        val aware = ClosureAwareRoutingProvider(valhalla, traffic, { now })
        var checked = 0
        for (c in closures.shuffled(java.util.Random(1)).take(25)) {
            val p = c.points.first()
            // Trajet de part et d'autre de la fermeture, dans l'axe du tronçon s'il est connu.
            val (o, d) = if (c.points.size == 2) {
                val a = c.points[0]; val b = c.points[1]
                GeoPoint(a.lat + (a.lat - b.lat) * 0.5 - 0.0, a.lon + (a.lon - b.lon) * 0.5) to GeoPoint(b.lat + (b.lat - a.lat) * 0.5, b.lon + (b.lon - a.lon) * 0.5)
            } else GeoPoint(p.lat, p.lon - 0.03) to GeoPoint(p.lat, p.lon + 0.03)
            val req = RouteRequest(o, d, costing = RouteCosting.FASTEST)
            val plain = (valhalla.route(req) as? Outcome.Success)?.value?.firstOrNull() ?: continue
            if (TrafficOnRoute.along(plain.geometry, listOf(c)).isEmpty()) continue
            val r = (aware.route(req) as? Outcome.Success)?.value?.firstOrNull() ?: continue
            val still = TrafficOnRoute.along(r.geometry, listOf(c)).isNotEmpty()
            println("${c.road} ${c.description?.take(60)} : ${(plain.durationSeconds / 60).toInt()} min → ${(r.durationSeconds / 60).toInt()} min, évitées=${r.avoidedClosures}, traverse encore=$still")
            checked++
            if (r.avoidedClosures > 0) assertTrue(!still)
            if (checked >= 4) break
        }
        assumeTrue("aucune fermeture traversée aujourd'hui", checked > 0)
    }
}
