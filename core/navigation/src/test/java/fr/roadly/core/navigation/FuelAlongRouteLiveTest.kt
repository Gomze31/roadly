package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.fuel.OpenDataFuelStationProvider
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.FuelType
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
import java.util.Locale

/** Contrôle réel : stations les moins chères sur Toulouse → Carcassonne (flux officiel des prix). Inactif sauf avec -ProadlyLive=true. */
class FuelAlongRouteLiveTest {
    @Test fun cheapestStationsOnARealRoute() = runBlocking {
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
        val out = FuelAlongRoute(OpenDataFuelStationProvider(api, json)).ahead(route.geometry, 0.0, FuelType.DIESEL)
        val list = (out as? Outcome.Success)?.value ?: error("échec : $out")
        list.take(6).forEach {
            println(String.format(Locale.FRANCE, "%-34s %s €  à %4.1f km  détour ≈ %4.0f m", it.station.name.take(34), it.priceEur?.let { p -> "%.3f".format(Locale.FRANCE, p) } ?: "—", it.aheadM / 1000, it.detourM))
        }
        println("Stations retenues : ${list.size}")
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.offsetM <= FuelAlongRoute.MAX_OFFSET_M })
    }
}
