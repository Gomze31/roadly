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
import fr.roadly.core.data.network.RequestGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
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

    // ── Débit (VAL-4) : une requête par seconde, retrait après un 429 ; serveur local, temps virtuel ──

    private fun TestScope.gated(): BanGeocodingProvider {
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(banBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        val gate = RequestGate({ testScheduler.currentTime }, BanGeocodingProvider.MIN_INTERVAL_MS, BanGeocodingProvider.BACKOFF_MS, BanGeocodingProvider.BACKOFF_MAX_MS)
        return BanGeocodingProvider(ApiClient(OkHttpClient(), dispatchers), config, Json { ignoreUnknownKeys = true }, gate)
    }
    private val empty = """{"type":"FeatureCollection","features":[]}"""

    @Test fun `two requests are one second apart, search and reverse alike`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setBody(empty)) }
        val ban = gated()
        ban.search("a", null)
        assertEquals(0L, testScheduler.currentTime)
        ban.search("ab", null)
        assertEquals(BanGeocodingProvider.MIN_INTERVAL_MS, testScheduler.currentTime)
        ban.reverse(GeoPoint(48.0, 2.0))
        assertEquals(2 * BanGeocodingProvider.MIN_INTERVAL_MS, testScheduler.currentTime)
        assertEquals(3, server.requestCount)
    }

    @Test fun `a 429 costs one request and nothing is sent until the back-off ends`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setBody(empty))
        val ban = gated()
        assertEquals(Outcome.Failure(AppError.RateLimited), ban.search("a", null))
        assertEquals("le client HTTP ne refait pas ses deux essais (3 requêtes avant)", 1, server.requestCount)
        advanceTimeBy(2_000)
        assertEquals(Outcome.Failure(AppError.RateLimited), ban.search("ab", null))
        assertEquals(1, server.requestCount)
        advanceTimeBy(BanGeocodingProvider.BACKOFF_MS)
        assertTrue(ban.search("abc", null) is Outcome.Success)
        assertEquals(2, server.requestCount)
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
