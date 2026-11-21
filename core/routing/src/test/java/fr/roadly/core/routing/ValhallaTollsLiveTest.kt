package fr.roadly.core.routing

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Contrôle sur le vrai serveur Valhalla (FOSSGIS) : « Sans péage » choisi dans la recherche change bien les variantes proposées.
 * Inactif sauf avec -ProadlyLive=true (réseau requis).
 */
class ValhallaTollsLiveTest {
    @Test fun avoidTollsRemovesTollsFromEveryVariant() = runBlocking {
        assumeTrue(System.getProperty("roadly.live") == "true")
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.IO; override val default = Dispatchers.Default; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT)
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        val useCase = RouteAlternativesUseCase(ValhallaRoutingProvider(ApiClient(OkHttpClient(), dispatchers), config, Json { ignoreUnknownKeys = true }))
        val toulouse = GeoPoint(43.6045, 1.4440)
        val carcassonne = GeoPoint(43.2130, 2.3491)
        val with = (useCase(toulouse, carcassonne, null, 0L) as Outcome.Success).value
        val without = (useCase(toulouse, carcassonne, null, 0L, RoutePreferences(avoidTolls = true)) as Outcome.Success).value
        fun List<RouteOption>.describe() = joinToString { "${it.label}: ${(it.route.durationSeconds / 60).toInt()} min, péage=${it.route.hasToll}" + ", conso ≈ ${(FuelSpeedModel.equivalentMeters(it.route) / it.route.distanceMeters * 100).toInt()} %" + (it.ecoSavingPct?.let { p -> ", ÉCONOME −$p %" } ?: "") }
        println("Péages autorisés  → ${with.describe()}")
        println("Sans péage choisi → ${without.describe()}")
        assertTrue(with.first().route.hasToll)
        assertTrue(without.none { it.route.hasToll })
    }
}
