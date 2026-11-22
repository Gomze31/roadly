package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.junction.JunctionWindow
import fr.roadly.core.navigation.junction.ValhallaJunctionTopologyProvider
import fr.roadly.core.navigation.testing.TestRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Budget de requêtes vers les serveurs publics (VAL-2, VAL-3), compté sur un faux client HTTP : l'intercepteur répond lui-même,
 * aucune requête ne quitte la machine, même quand l'URL configurée est celle d'un serveur public.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestBudgetTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val route = TestRoutes.straight()
    private val geometry = RouteGeometry(route.geometry)

    /** Requête reçue par le faux serveur : chemin, corps décodé et instant virtuel. */
    private class Seen(val path: String, val body: String, val atMs: Long)

    private class FakeHttp(private val nowMs: () -> Long, var answer: (Seen) -> Pair<Int, String>) {
        val seen = CopyOnWriteArrayList<Seen>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            val request: Request = chain.request()
            val text = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            val s = Seen(request.url.encodedPath, URLDecoder.decode(text, "UTF-8"), nowMs())
            seen += s
            val (code, body) = answer(s)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fake")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val count: Int get() = seen.size
    }

    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
    }

    private fun config(value: ProviderConfig) = object : ProviderConfigRepository {
        override val config = flowOf(value)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }

    private fun TestScope.fakeHttp(answer: (Seen) -> Pair<Int, String>) = FakeHttp({ testScheduler.currentTime }, answer)

    private val selfHosted = ProviderConfig.DEFAULT.copy(valhallaBaseUrl = "https://valhalla.exemple.fr")

    // ---- Carrefours (VAL-2) ----

    private val topologyBody = """{"edges":[{"begin_shape_index":0,"end_shape_index":1,"length":0.073,"road_class":"primary"}]}"""
    private fun window(alongM: Double) = JunctionWindow.around(geometry, alongM)

    @Test fun `junction topology on the public host - no request, whatever the number of junctions and attempts`() = runTest {
        assertTrue(ValhallaTraceSpeedLimitProvider.isPublicInstance("${ProviderConfig.DEFAULT.valhallaBaseUrl}/trace_attributes".toHttpUrl()))
        val http = fakeHttp { 200 to topologyBody }
        val provider = ValhallaJunctionTopologyProvider(ApiClient(http.client, dispatchers), config(ProviderConfig.DEFAULT), json)
        // Trois carrefours, trois essais chacun (ce que fait la session quand la topologie manque).
        for (maneuver in 0..2) repeat(3) {
            assertEquals(Outcome.Failure(AppError.NoResult), provider.topology(route, maneuver, window(700.0)))
        }
        assertEquals("requêtes trace_attributes par carrefour sur l'instance publique (1 à 3 par carrefour avant)", 0, http.count)
    }

    @Test fun `junction topology on a self-hosted server - still one request per junction, then the memory`() = runTest {
        val http = fakeHttp { 200 to topologyBody }
        val provider = ValhallaJunctionTopologyProvider(ApiClient(http.client, dispatchers), config(selfHosted), json)
        val first = provider.topology(route, 1, window(700.0))
        assertTrue(first is Outcome.Success)
        assertEquals(1, http.count)
        assertEquals("/trace_attributes", http.seen.single().path)
        assertEquals(first, provider.topology(route, 1, window(700.0)))
        assertEquals(1, http.count)
    }

    // ---- Profil du tracé (VAL-2) ----

    private fun profileBody(r: Route = route) = """{"edges":[{"length":${RouteGeometry(r.geometry).totalM / 1000.0},"speed_limit":50,"way_id":1}]}"""
    private fun profileProvider(http: FakeHttp) = ValhallaRouteAttributesProvider(ApiClient(http.client, dispatchers), config(selfHosted), json)

    @Test fun `route profile - one request per route, also when two callers ask at the same time`() = runTest {
        val http = fakeHttp { 200 to profileBody() }
        val provider = profileProvider(http)
        val both = listOf(async { provider.attributes(route) }, async { provider.attributes(route) }).awaitAll()
        assertTrue(both.all { it is Outcome.Success })
        assertTrue(provider.attributes(route) is Outcome.Success)
        assertEquals(1, http.count)
    }

    @Test fun `route profile - a reroute to the same destination costs one request for the new route, never more`() = runTest {
        val http = fakeHttp { 200 to profileBody() }
        val provider = profileProvider(http)
        val rerouted = route.copy(id = "rerouted")
        repeat(3) { assertTrue(provider.attributes(route) is Outcome.Success) }
        repeat(3) { assertTrue(provider.attributes(rerouted) is Outcome.Success) }
        assertEquals(2, http.count)
    }

    @Test fun `route profile - an unusable answer is not asked again for the same route`() = runTest {
        // Longueur des arêtes sans rapport avec le tracé, corps illisible, refus 400 : même réponse à chaque fois.
        for (answer in listOf(200 to """{"edges":[{"length":99.0}]}""", 200 to "<html>", 400 to """{"error_code":443}""")) {
            val http = fakeHttp { answer }
            val provider = profileProvider(http)
            val first = provider.attributes(route)
            assertTrue(first is Outcome.Failure)
            repeat(3) { assertEquals(first, provider.attributes(route)) }
            assertEquals("réponse $answer", 1, http.count)
        }
    }

    @Test fun `route profile - a passing failure is not remembered and each call makes one attempt only`() = runTest {
        val http = fakeHttp { 503 to "" }
        val provider = profileProvider(http)
        assertEquals(AppError.ProviderUnavailable("valhalla.exemple.fr", 503), (provider.attributes(route) as Outcome.Failure).error)
        assertEquals("une demande, une requête : pas de boucle de tentatives", 1, http.count)
        http.answer = { 200 to profileBody() }
        assertTrue(provider.attributes(route) is Outcome.Success)
        assertEquals(2, http.count)
    }

    // ---- Ralentisseurs, Overpass (VAL-3) ----

    private fun bumpProvider(scope: TestScope, http: FakeHttp) = OverpassSpeedBumpProvider(
        ApiClient(http.client, dispatchers), config(ProviderConfig.DEFAULT), json, NavigationClock { scope.testScheduler.currentTime },
    )
    private fun profile(r: Route, ways: List<Long>) = RouteAttributes(r.id, geometry.totalM, emptyList(), emptyList(), slowWayIds = ways)
    private fun overpassBody(vararg alongM: Double) =
        """{"elements":[${alongM.joinToString(",") { a -> geometry.pointAt(a).let { """{"type":"node","id":1,"lat":${it.lat},"lon":${it.lon}}""" } }}]}"""
    private fun waysOf(s: Seen): List<Long> = Regex("""way\(id:([0-9,]+)\)""").find(s.body)!!.groupValues[1].split(",").map { it.toLong() }

    @Test fun `speed bumps - same corridor rerouted five times costs no request after the first`() = runTest {
        val http = fakeHttp { 200 to overpassBody(500.0, 1_000.0) }
        val provider = bumpProvider(this, http)
        val ways = (1L..10L).toList()
        val first = (provider.bumps(route, profile(route, ways)) as Outcome.Success).value
        assertEquals(2, first.size)
        assertEquals(1, http.count)
        // Cinq recalculs sur le même couloir : nouveaux tracés (nouvel identifiant) dont les voies lentes sont déjà connues.
        for (i in 1..5) {
            val rerouted = route.copy(id = "reroute-$i")
            val bumps = (provider.bumps(rerouted, profile(rerouted, ways.drop(i - 1))) as Outcome.Success).value
            assertEquals(first, bumps)
        }
        assertEquals("requêtes Overpass pour 1 tracé + 5 recalculs sur le même couloir (6 avant)", 1, http.count)
        assertEquals("aucune attente imposée : rien n'a été demandé", 0L, testScheduler.currentTime)
    }

    @Test fun `speed bumps - a reroute through new streets asks for the new ways only, ten seconds after the last request`() = runTest {
        val http = fakeHttp { 200 to overpassBody(500.0) }
        val provider = bumpProvider(this, http)
        provider.bumps(route, profile(route, (1L..10L).toList()))
        val rerouted = route.copy(id = "reroute")
        assertTrue(provider.bumps(rerouted, profile(rerouted, (5L..14L).toList())) is Outcome.Success)
        assertEquals(2, http.count)
        assertEquals((11L..14L).toList(), waysOf(http.seen[1]))
        assertEquals(OverpassSpeedBumpProvider.MIN_REQUEST_INTERVAL_MS, http.seen[1].atMs - http.seen[0].atMs)
    }

    @Test fun `speed bumps - the packets of a long route are sent one at a time, ten seconds apart`() = runTest {
        val http = fakeHttp { 200 to overpassBody() }
        val provider = bumpProvider(this, http)
        assertTrue(provider.bumps(route, profile(route, (1L..1_000L).toList())) is Outcome.Success)
        assertEquals(listOf(400, 400, 200), http.seen.map { waysOf(it).size })
        assertEquals(listOf(0L, 10_000L, 20_000L), http.seen.map { it.atMs })
    }

    @Test fun `speed bumps - routes overtaken while waiting are not requested`() = runTest {
        val http = fakeHttp { 200 to overpassBody(500.0) }
        val provider = bumpProvider(this, http)
        provider.bumps(route, profile(route, listOf(1L)))
        // Trois recalculs coup sur coup pendant l'intervalle minimal : seul le dernier tracé est demandé.
        val results = (1..3).map { i -> route.copy(id = "r$i").let { r -> async { provider.bumps(r, profile(r, listOf(100L + i))) } } }.awaitAll()
        assertEquals(listOf(false, false, true), results.map { it is Outcome.Success })
        assertEquals("requêtes pour 1 tracé + 3 recalculs rapprochés (4 avant)", 2, http.count)
        assertEquals(listOf(103L), waysOf(http.seen[1]))
    }

    @Test fun `speed bumps - 504 then a new route - nothing is sent during the back-off, which doubles`() = runTest {
        val http = fakeHttp { 504 to "" }
        val provider = bumpProvider(this, http)
        assertEquals(AppError.ProviderUnavailable("overpass-api.de", 504), (provider.bumps(route, profile(route, listOf(1L))) as Outcome.Failure).error)
        assertEquals(1, http.count)
        // Nouveaux tracés pendant le retrait : refusés tout de suite, sans requête.
        for (i in 1..4) {
            val next = route.copy(id = "next-$i")
            assertEquals(Outcome.Failure(AppError.RateLimited), provider.bumps(next, profile(next, listOf(10L + i))))
            advanceTimeBy(10_000)
        }
        assertEquals("requêtes pendant le retrait (4 avant : une par nouveau tracé)", 1, http.count)
        // Retrait écoulé (60 s) : une requête repart ; nouvel échec, le retrait double (120 s).
        advanceTimeBy(OverpassSpeedBumpProvider.BACKOFF_MS)
        val again = route.copy(id = "again")
        assertTrue(provider.bumps(again, profile(again, listOf(20L))) is Outcome.Failure)
        assertEquals(2, http.count)
        advanceTimeBy(OverpassSpeedBumpProvider.BACKOFF_MS + 30_000)
        assertEquals(Outcome.Failure(AppError.RateLimited), provider.bumps(again, profile(again, listOf(20L))))
        assertEquals(2, http.count)
        // Fin du second retrait : le serveur répond, la zone est lue, le retrait est levé.
        advanceTimeBy(OverpassSpeedBumpProvider.BACKOFF_MS)
        http.answer = { 200 to overpassBody(500.0) }
        assertEquals(1, (provider.bumps(again, profile(again, listOf(20L))) as Outcome.Success).value.size)
        assertEquals(3, http.count)
    }

    @Test fun `speed bumps - 429 then a new route - the new route sends nothing during the back-off`() = runTest {
        val http = fakeHttp { 429 to "" }
        val provider = bumpProvider(this, http)
        assertEquals(Outcome.Failure(AppError.RateLimited), provider.bumps(route, profile(route, listOf(1L))))
        // Un 429 coûte une seule requête : le client HTTP commun ne refait pas ses deux essais (2 s puis 8 s) pour cet appelant.
        val afterFirst = http.count
        assertEquals("requêtes pour un 429 (3 avant)", 1, afterFirst)
        assertEquals("aucune attente de nouvelle tentative", 0L, testScheduler.currentTime)
        http.answer = { 200 to overpassBody(500.0) }
        val next = route.copy(id = "next")
        assertEquals(Outcome.Failure(AppError.RateLimited), provider.bumps(next, profile(next, listOf(2L))))
        assertEquals("requêtes du tracé suivant pendant le retrait (1 à 3 avant)", afterFirst, http.count)
        advanceTimeBy(OverpassSpeedBumpProvider.BACKOFF_MS)
        assertTrue(provider.bumps(next, profile(next, listOf(2L))) is Outcome.Success)
        assertEquals(afterFirst + 1, http.count)
    }

    @Test fun `speed bumps - bumps read for another trip's streets are not given to a route that does not use them`() = runTest {
        // Les voies 1 à 10 (un premier trajet) portent un ralentisseur ; les voies 20 et 21 (une rue parallèle, même géométrie
        // ici, donc à 0 m) n'en portent aucun.
        val http = fakeHttp { s -> 200 to if (1L in waysOf(s)) overpassBody(500.0) else overpassBody() }
        val provider = bumpProvider(this, http)
        assertEquals(1, (provider.bumps(route, profile(route, (1L..10L).toList())) as Outcome.Success).value.size)
        val parallel = route.copy(id = "parallele")
        assertEquals("aucun ralentisseur sur les voies de ce tracé", emptyList<Double>(), (provider.bumps(parallel, profile(parallel, listOf(20L, 21L))) as Outcome.Success).value)
        // Un tracé qui reprend l'une des premières voies retrouve son ralentisseur, sans nouvelle requête.
        val back = route.copy(id = "retour")
        assertEquals(1, (provider.bumps(back, profile(back, listOf(3L, 20L))) as Outcome.Success).value.size)
        assertEquals(2, http.count)
    }

    @Test fun `speed bumps - a route without slow ways or already answered sends nothing`() = runTest {
        val http = fakeHttp { 200 to overpassBody(500.0) }
        val provider = bumpProvider(this, http)
        assertEquals(Outcome.Success(emptyList<Double>()), provider.bumps(route, profile(route, emptyList())))
        assertEquals(0, http.count)
        val first = provider.bumps(route, profile(route, listOf(1L)))
        assertEquals(first, provider.bumps(route, profile(route, listOf(1L))))
        assertEquals(1, http.count)
    }
}
