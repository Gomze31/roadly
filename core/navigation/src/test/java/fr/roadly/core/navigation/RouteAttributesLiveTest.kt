package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.routing.ValhallaRoutingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Contrôle réel (FOSSGIS) : gares de péage et feux lus sur Toulouse → Carcassonne. Inactif sauf avec -ProadlyLive=true. */
class RouteAttributesLiveTest {
    @Test fun tollBoothsAndSignalsOnARealRoute() = runBlocking {
        assumeTrue(System.getProperty("roadly.live") == "true")
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.IO; override val default = Dispatchers.Default; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT)
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        val json = Json { ignoreUnknownKeys = true }
        val api = ApiClient(OkHttpClient(), dispatchers)
        val route = (ValhallaRoutingProvider(api, config, json).route(
            RouteRequest(GeoPoint(43.6045, 1.4440), GeoPoint(43.2130, 2.3491), costing = RouteCosting.FASTEST, departAtEpochMs = 0L),
        ) as Outcome.Success).value.first()
        val a = (ValhallaRouteAttributesProvider(api, config, json).attributes(route) as Outcome.Success).value
        println("Gares de péage : ${a.tollBooths.map { (it / 1000).toInt() }} km · feux : ${a.trafficSignals.size} · identité : ${RouteIdentity.from(a)}")
        assertTrue(a.tollBooths.isNotEmpty())
        assertTrue(a.trafficSignals.isNotEmpty())
    }
}
