package fr.roadly.core.geocoding

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProvider(override val id: String, val result: Outcome<List<Place>>) : GeocodingProvider {
    override suspend fun search(query: String, near: GeoPoint?, limit: Int) = result
    override suspend fun reverse(point: GeoPoint): Outcome<Place?> = when (result) { is Outcome.Success -> Outcome.Success(result.value.firstOrNull()); is Outcome.Failure -> result }
}

private fun place(id: String, name: String, lat: Double, lon: Double, source: String) = Place(id, name, null, GeoPoint(lat, lon), PlaceKind.ADDRESS, source)

class CompositeGeocoderTest {
    @Test fun `coordinates input returns a COORDINATES place first without calling providers`() = runTest {
        val g = CompositeGeocoder(FakeProvider("ban", Outcome.Failure(AppError.Network)), FakeProvider("photon", Outcome.Failure(AppError.Network)))
        val r = (g.search("48.85, 2.35", null) as Outcome.Success).value
        assertEquals(PlaceKind.COORDINATES, r.first().kind)
    }

    @Test fun `merges ban then photon and dedupes within 30 m and same normalized name`() = runTest {
        val ban = FakeProvider("ban", Outcome.Success(listOf(place("b1", "Rue de la Paix", 48.8690, 2.3310, "ban"))))
        val photon = FakeProvider("photon", Outcome.Success(listOf(place("p1", "rue de la paix", 48.8691, 2.3311, "photon"), place("p2", "Tour Eiffel", 48.8584, 2.2945, "photon"))))
        val r = (CompositeGeocoder(ban, photon).search("paix", null) as Outcome.Success).value
        assertEquals(2, r.size)
        assertEquals("ban", r[0].source)
        assertEquals("Tour Eiffel", r[1].name)
    }

    @Test fun `ban failure falls back to photon`() = runTest {
        val g = CompositeGeocoder(FakeProvider("ban", Outcome.Failure(AppError.Timeout)), FakeProvider("photon", Outcome.Success(listOf(place("p", "X", 1.0, 1.0, "photon")))))
        assertEquals(1, (g.search("x", null) as Outcome.Success).value.size)
    }

    @Test fun `both failures give ProviderUnavailable`() = runTest {
        val g = CompositeGeocoder(FakeProvider("ban", Outcome.Failure(AppError.Timeout)), FakeProvider("photon", Outcome.Failure(AppError.Network)))
        assertTrue((g.search("x", null) as Outcome.Failure).error is AppError.ProviderUnavailable)
    }

    @Test fun `photon results are not truncated by limit`() = runTest {
        val banPlaces = listOf(place("b1", "Rue A", 48.80, 2.30, "ban"), place("b2", "Rue B", 48.81, 2.31, "ban"), place("b3", "Rue C", 48.82, 2.32, "ban"))
        val ban = FakeProvider("ban", Outcome.Success(banPlaces))
        val photon = FakeProvider("photon", Outcome.Success(listOf(place("p1", "Gare de Lyon", 48.8447, 2.3738, "photon"))))
        val r = (CompositeGeocoder(ban, photon).search("rue", null, limit = 3) as Outcome.Success).value
        assertEquals(4, r.size)
        assertTrue(r.any { it.name == "Gare de Lyon" && it.source == "photon" })
    }
}
