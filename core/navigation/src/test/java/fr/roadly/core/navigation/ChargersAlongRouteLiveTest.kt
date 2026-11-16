package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.charging.IrveChargingStationProvider
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.routing.EvRange
import fr.roadly.core.routing.ValhallaRoutingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Contrôle réel : bornes rapides IRVE sur Toulouse → Carcassonne et leur dernier état publié. Inactif sauf avec -ProadlyLive=true. */
class ChargersAlongRouteLiveTest {
    @Test fun fastChargersOnARealRoute() = runBlocking {
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
        val api = ApiClient(OkHttpClient.Builder().readTimeout(40, java.util.concurrent.TimeUnit.SECONDS).build(), dispatchers)
        val route = (ValhallaRoutingProvider(api, config, json).route(
            RouteRequest(GeoPoint(43.6045, 1.4440), GeoPoint(43.2130, 2.3491), costing = RouteCosting.FASTEST, departAtEpochMs = 0L),
        ) as Outcome.Success).value.first()
        val ev = EvRange.arrival(route, 18.0, 60.0, 30)!!
        println("Toulouse → Carcassonne : ${(route.distanceMeters / 1000).toInt()} km, ≈ ${"%.1f".format(ev.usedKwh)} kWh ; départ 30 % → arrivée ≈ ${ev.arrivalPct} % (${ev.level})")
        val out = ChargersAlongRoute(IrveChargingStationProvider(api, json)).ahead(route.geometry, 0.0)
        val list = (out as? Outcome.Success)?.value ?: error("échec : $out")
        val now = System.currentTimeMillis()
        list.forEach {
            println("  ${"%5.1f".format(it.aheadM / 1000)} km · ${it.station.maxKw} kW · ${it.station.name.take(40)} · détour ≈ ${it.detourM.toInt()} m" +
                (it.status?.describe(now)?.let { s -> " · $s" } ?: ""))
        }
        assertTrue(list.isNotEmpty())
        assertTrue(list.all { it.station.maxKw >= 50 && it.offsetM <= ChargersAlongRoute.MAX_OFFSET_M })
    }
}
