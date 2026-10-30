package fr.roadly.core.geocoding

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
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

class PhotonNearbyPoiProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: PhotonNearbyPoiProvider
    private val capitole = GeoPoint(43.6045, 1.4440)
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
        provider = PhotonNearbyPoiProvider(ApiClient(OkHttpClient(), dispatchers), config, Json { ignoreUnknownKeys = true })
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `nearby fuel stations are POI sorted by distance and the request carries radius and tag`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("photon_nearby_fuel.json")))
        val places = (provider.nearby(PoiCategory.FUEL, capitole) as Outcome.Success).value
        assertEquals(5, places.size)
        assertTrue(places.all { it.kind == PlaceKind.POI && it.source == "photon" })
        val distances = places.map { it.point.distanceTo(capitole) }
        assertEquals(distances.sorted(), distances)
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/reverse?lat=43.604500&lon=1.444000&radius=5&limit=25&lang=fr&osm_tag=amenity%3Afuel"))
    }

    @Test fun `rest areas send both tags`() = runTest {
        server.enqueue(MockResponse().setBody("""{"features":[]}"""))
        provider.nearby(PoiCategory.REST_AREA, capitole)
        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("radius=30") && path.contains("osm_tag=highway%3Arest_area") && path.contains("osm_tag=highway%3Aservices"))
    }
}
