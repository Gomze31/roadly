package fr.roadly.core.routing

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.StreetSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fixture FOSSGIS réelle (Toulouse, périphérique et A620, enregistrée le 2026-10-31) : sorties numérotées, échangeur, ronds-points (B3, scénarios 7 et 8). */
class ValhallaMotorwayFixtureTest {
    private val server = MockWebServer()
    private lateinit var provider: ValhallaRoutingProvider
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture() = javaClass.classLoader!!.getResource("fixtures/valhalla_motorway.json")!!.readText()
    private fun raw(): List<JsonObject> = json.parseToJsonElement(fixture()).jsonObject["trip"]!!.jsonObject["legs"]!!.jsonArray[0].jsonObject["maneuvers"]!!.jsonArray.map { it.jsonObject }

    @Before fun setUp() {
        server.start()
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = ValhallaRoutingProvider(ApiClient(OkHttpClient(), dispatchers), config, json)
    }
    @After fun tearDown() = server.shutdown()

    private suspend fun parsed(): Route {
        server.enqueue(MockResponse().setBody(fixture()))
        return (provider.route(RouteRequest(GeoPoint(43.6300, 1.4100), GeoPoint(43.5540, 1.5000), costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
    }

    private fun JsonObject.type() = this["type"]!!.jsonPrimitive.int
    private fun JsonObject.signTexts(key: String): List<String> =
        ((this["sign"] as? JsonObject)?.get(key) as? JsonArray)?.map { it.jsonObject["text"]!!.jsonPrimitive.content }.orEmpty()

    @Test fun `the recorded trip contains a numbered exit followed by an interchange`() {
        val raw = raw()
        val firstExit = raw.indexOfFirst { it.type() in 20..21 && it.signTexts("exit_number_elements").any { t -> t.isNotBlank() } }
        assertTrue("au moins une sortie numérotée", firstExit >= 0)
        assertTrue("échangeur : bretelle, sortie ou bifurcation après la sortie", raw.drop(firstExit + 1).any { it.type() in setOf(18, 19, 20, 21, 23, 24) })
    }

    @Test fun `every maneuver keeps its side, bearings, sign and street names`() = runTest {
        val r = parsed()
        raw().forEachIndexed { i, m ->
            val p = r.maneuvers[i]
            when (m.type()) {
                20 -> assertEquals("sortie à droite $i", ManeuverType.EXIT_RIGHT, p.type)
                21 -> assertEquals("sortie à gauche $i", ManeuverType.EXIT_LEFT, p.type)
                18 -> assertEquals("bretelle à droite $i", ManeuverType.RAMP_RIGHT, p.type)
                19 -> assertEquals("bretelle à gauche $i", ManeuverType.RAMP_LEFT, p.type)
                23 -> assertEquals("serrer à droite $i", ManeuverType.KEEP_RIGHT, p.type)
                24 -> assertEquals("serrer à gauche $i", ManeuverType.KEEP_LEFT, p.type)
                else -> assertEquals("type $i", ValhallaManeuverTypes.of(m.type()), p.type)
            }
            assertEquals("bearing_before $i", m["bearing_before"]?.jsonPrimitive?.int, p.bearingBeforeDeg)
            assertEquals("bearing_after $i", m["bearing_after"]?.jsonPrimitive?.int, p.bearingAfterDeg)
            assertEquals("numéro $i", m.signTexts("exit_number_elements").firstOrNull { it.isNotBlank() }, p.exitNumber)
            assertEquals(
                "direction $i",
                m.signTexts("exit_toward_elements").map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" · ").takeIf { it.isNotEmpty() },
                p.exitToward,
            )
            assertEquals("noms $i", (m["street_names"] as? JsonArray)?.map { it.jsonPrimitive.content.trim() }?.filter { it.isNotEmpty() }.orEmpty(), p.streetNames)
        }
    }

    /** Valeurs lues dans la réponse du 2026-10-31 (script de la tâche 5, étape 2) ; à remplacer par celles du fichier s'il diffère. */
    @Test fun `reference values read in the recorded response`() = runTest {
        val r = parsed()
        val exit31b = r.maneuvers.single { it.exitNumber == "31b" }
        assertEquals(ManeuverType.EXIT_RIGHT, exit31b.type)
        assertEquals("Toulouse-centre · Tarbes - Lourdes", exit31b.exitToward)
        assertEquals(listOf("A 620"), exit31b.streetNames)
        assertEquals("échangeur juste après la sortie 31b", ManeuverType.KEEP_LEFT, r.maneuvers[r.maneuvers.indexOf(exit31b) + 1].type)
        assertEquals(ManeuverType.EXIT_RIGHT, r.maneuvers.single { it.exitNumber == "19" }.type)
        assertEquals(StreetSide.LEFT, r.maneuvers.last().sideOfStreet)
        assertTrue("LIMITE : aucune voie renvoyée par le serveur public sur ce trajet", r.maneuvers.all { it.lanes.isEmpty() })
    }
}
