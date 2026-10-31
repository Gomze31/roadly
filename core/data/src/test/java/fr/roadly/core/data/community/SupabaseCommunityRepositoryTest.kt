package fr.roadly.core.data.community

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ReportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseCommunityRepositoryTest {
    private val server = MockWebServer()
    private val cfg = MutableStateFlow(CommunityConfig())
    private val device = "11111111-1111-1111-1111-111111111111"
    private val configRepo = object : CommunityConfigRepository {
        override val config = cfg
        override suspend fun save(config: CommunityConfig) { cfg.value = config }
        override suspend fun clear() { cfg.value = CommunityConfig() }
        override suspend fun deviceId() = device
    }
    private lateinit var repo: SupabaseCommunityRepository
    private val row = """{"id":"r1","kind":"JAM","lat":43.6,"lon":1.44,"device_id":"$device","created_at":"2026-10-31T01:00:00+00:00","expires_at":"2026-10-31T01:30:00+00:00","confirmations":2,"denials":0,"active":true}"""
    private val json = Json { ignoreUnknownKeys = true }

    @Before fun setUp() {
        server.start()
        val d = object : DispatcherProvider { override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined }
        repo = SupabaseCommunityRepository(ApiClient(OkHttpClient(), d), configRepo, json)
    }
    @After fun tearDown() = server.shutdown()

    // MockWebServer publie parfois le nom de la machine : on force `localhost` (clair toléré en local).
    private fun configure() { cfg.value = CommunityConfig("http://localhost:${server.port}", "anon-key") }

    @Test fun `unconfigured or cleartext remote service never calls the network`() = runTest {
        val r = repo.report(ReportKind.JAM, GeoPoint(43.6, 1.44), 90f)
        assertTrue((r as Outcome.Failure).error is AppError.ProviderUnavailable)
        assertEquals(0, server.requestCount)
        assertFalse(CommunityConfig("http://example.org", "k").isConfigured)
        assertTrue(CommunityConfig("https://x.supabase.co", "k").isConfigured)
    }

    @Test fun `report posts to create_report with the key headers and parses the returned row`() = runTest {
        configure()
        server.enqueue(MockResponse().setBody(row))
        val r = (repo.report(ReportKind.JAM, GeoPoint(43.6, 1.44), 90.4f) as Outcome.Success).value
        val req = server.takeRequest()
        assertEquals("/rest/v1/rpc/create_report", req.path)
        assertEquals("anon-key", req.getHeader("apikey"))
        assertEquals("Bearer anon-key", req.getHeader("Authorization"))
        val body = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("JAM", body["p_kind"]!!.jsonPrimitive.content)
        assertEquals("90", body["p_heading"]!!.jsonPrimitive.content)
        assertEquals(device, body["p_device"]!!.jsonPrimitive.content)
        assertEquals("r1", r.id)
        assertTrue(r.mine)
        assertEquals(2, r.confirmations)
        assertTrue(r.expiresAtEpochMs > r.createdAtEpochMs)
    }

    @Test fun `nearby parses the list and drops inactive rows`() = runTest {
        configure()
        server.enqueue(MockResponse().setBody("[$row,${row.replace("\"id\":\"r1\"", "\"id\":\"r2\"").replace("\"active\":true", "\"active\":false")}]"))
        val list = (repo.nearby(BoundingBox(43.5, 1.4, 43.7, 1.5)) as Outcome.Success).value
        assertEquals(listOf("r1"), list.map { it.id })
        assertEquals("/rest/v1/rpc/reports_in_box", server.takeRequest().path)
    }

    @Test fun `business errors from the database are mapped`() = runTest {
        configure()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"P0003","message":"own_report"}"""))
        assertEquals(AppError.NoResult, (repo.vote("r1", true) as Outcome.Failure).error)
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"P0001","message":"rate_limited"}"""))
        assertEquals(AppError.RateLimited, (repo.report(ReportKind.HAZARD, GeoPoint(1.0, 1.0), null) as Outcome.Failure).error)
    }
    @Test fun `profile and leaderboard parse server rows`() = runTest {
        configure()
        server.enqueue(MockResponse().setBody("""{"device_id":"$device","pseudonym":"conducteur-ab12","points":7}"""))
        val p = (repo.profile() as Outcome.Success).value
        assertEquals("conducteur-ab12", p.pseudonym); assertEquals(7, p.points)
        assertEquals("/rest/v1/rpc/get_profile", server.takeRequest().path)
        server.enqueue(MockResponse().setBody("""[{"pseudonym":"a","points":10,"rank":1},{"pseudonym":"b","points":3,"rank":2}]"""))
        val lb = (repo.leaderboard(10) as Outcome.Success).value
        assertEquals(listOf(1, 2), lb.map { it.rank })
        assertEquals("/rest/v1/rpc/leaderboard", server.takeRequest().path)
    }
}
