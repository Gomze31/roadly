package fr.roadly.core.geocoding

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.PlaceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhotonGeocodingProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: PhotonGeocodingProvider
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Before fun setUp() {
        server.start()
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(photonBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = PhotonGeocodingProvider(ApiClient(OkHttpClient(), dispatchers), config, Json { ignoreUnknownKeys = true })
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `search parses POI with french lang param`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("photon_search.json")))
        val places = (provider.search("gare de lyon", null) as Outcome.Success).value
        assertTrue(places.isNotEmpty())
        assertEquals("photon", places.first().source)
        assertTrue(places.first().kind == PlaceKind.POI || places.first().kind == PlaceKind.STREET || places.first().kind == PlaceKind.CITY || places.first().kind == PlaceKind.ADDRESS)
        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/api/?q=gare+de+lyon") && path.contains("lang=fr"))
    }
}
