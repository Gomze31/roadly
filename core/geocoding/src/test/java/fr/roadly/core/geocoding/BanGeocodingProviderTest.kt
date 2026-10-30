package fr.roadly.core.geocoding

import fr.roadly.core.common.AppError
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BanGeocodingProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: BanGeocodingProvider

    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Before fun setUp() {
        server.start()
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(banBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = BanGeocodingProvider(ApiClient(OkHttpClient(), dispatchers), config, Json { ignoreUnknownKeys = true })
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `search parses features into places`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ban_search.json")))
        val places = (provider.search("8 boulevard du port amiens", null) as Outcome.Success).value
        assertTrue(places.isNotEmpty())
        val first = places.first()
        assertTrue(first.name.contains("Boulevard du Port", ignoreCase = true))
        assertEquals("ban", first.source)
        assertEquals(PlaceKind.ADDRESS, first.kind)
        assertEquals(49.89, first.point.lat, 0.1); assertEquals(2.29, first.point.lon, 0.1)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/search/?q=8+boulevard+du+port+amiens"))
    }

    @Test fun `search sends lat lon when near is given`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ban_search.json")))
        provider.search("port", GeoPoint(49.0, 2.0))
        val path = server.takeRequest().path!!
        assertTrue(path.contains("lat=49.000000") && path.contains("lon=2.000000"))
    }

    @Test fun `reverse returns first feature or null`() = runTest {
        server.enqueue(MockResponse().setBody(fixture("ban_reverse.json")))
        val place = (provider.reverse(GeoPoint(48.357, 2.37)) as Outcome.Success).value
        // La fixture enregistrée contient bien un résultat : on vérifie le premier (23 Grande Rue, Prunay-sur-Essonne).
        assertNotNull(place)
        assertEquals("ban", place!!.source)
        assertEquals("23 Grande Rue", place.name)
        assertEquals(48.356, place.point.lat, 0.01); assertEquals(2.371, place.point.lon, 0.01)
    }

    @Test fun `empty features gives empty list not error`() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"FeatureCollection","features":[]}"""))
        assertEquals(emptyList<Any>(), (provider.search("zzzz", null) as Outcome.Success).value)
    }

    @Test fun `malformed base url returns failure not exception`() = runTest {
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val badConfig = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(banBaseUrl = "not a url"))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        val bad = BanGeocodingProvider(ApiClient(OkHttpClient(), dispatchers), badConfig, Json { ignoreUnknownKeys = true })
        val r = bad.search("x", null) as Outcome.Failure
        assertTrue(r.error is AppError.ProviderUnavailable)
    }
}
