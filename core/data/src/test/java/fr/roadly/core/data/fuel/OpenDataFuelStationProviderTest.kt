package fr.roadly.core.data.fuel

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.FuelType
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenDataFuelStationProviderTest {
    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
    }
    private val provider = OpenDataFuelStationProvider(ApiClient(OkHttpClient(), dispatchers), Json { ignoreUnknownKeys = true })
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun `parses the real flow with only the published prices and their dates`() {
        val stations = (provider.parse(fixture("prix_carburants_toulouse.json")) as Outcome.Success).value
        assertEquals(3, stations.size)
        val s = stations[1]
        assertEquals("31300007", s.id)
        assertEquals("104 Av Gde Bretagne", s.name)
        assertEquals("31300 Toulouse", s.address)
        assertEquals(43.59914, s.point.lat, 1e-9)
        assertEquals(2.25, s.price(FuelType.DIESEL)!!.eurPerLitre, 1e-9)
        assertEquals(1.99, s.price(FuelType.E10)!!.eurPerLitre, 1e-9)
        assertNull("prix absent → jamais inventé", s.price(FuelType.SP98))
        assertEquals(OpenDataFuelStationProvider.parseDate("2026-10-31T00:01:00+00:00"), s.price(FuelType.DIESEL)!!.updatedAtEpochMs)
        assertFalse(s.open24h)
        assertTrue(s.services.contains("Station de gonflage"))
        assertTrue("station sans aucun prix conservée (rupture)", stations[2].prices.isEmpty())
    }

    @Test fun `malformed body is a parse failure`() {
        assertTrue(provider.parse("{") is Outcome.Failure)
    }
}
