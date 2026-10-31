package fr.roadly.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.FuelPrice
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val geocoder = FakeGeocoder()
    private val recents = FakeRecents()
    private val favorites = FakeFavorites()
    private val location = FakeLocationRepository()
    private val connectivity = FakeConnectivity()
    private val paris = Place("p1", "Paris", "75000 Paris", GeoPoint(48.8566, 2.3522), PlaceKind.CITY, "fake")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val nearby = FakeNearby()
    private val fuel = FakeFuel()
    private val vehicle = FakeVehicle()

    private fun vm() = SearchViewModel(geocoder, recents, favorites, location, connectivity, nearby, fuel, vehicle)

    private fun station(id: String, east: Double, diesel: Double?, e10: Double?) = FuelStation(
        id, "Station $id", "31000 Toulouse", GeoPoint(43.6, 1.44 + east / 80_000.0),
        listOfNotNull(diesel?.let { FuelPrice(FuelType.DIESEL, it, 1L) }, e10?.let { FuelPrice(FuelType.E10, it, 1L) }), false, emptyList(),
    )

    @Test fun `fuel category uses the price flow, defaults to the vehicle fuel and can sort by price`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(43.6, 1.44), null, null, 5f, 0L)
        vehicle.p.value = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, null)
        fuel.next = Outcome.Success(listOf(station("a", 100.0, 2.10, 1.99), station("b", 500.0, 2.00, 1.80), station("c", 900.0, null, null)))
        val vm = vm()
        vm.uiState.test {
            vm.onCategory(PoiCategory.FUEL); advanceTimeBy(10)
            var s = expectMostRecentItem()
            assertEquals(1, fuel.calls)
            assertEquals(0, nearby.calls.size)
            assertEquals(FuelType.E10, s.fuel)
            val content = s.results as SearchResults.Content
            assertEquals(listOf("fuel:a", "fuel:b", "fuel:c"), content.places.map { it.id })
            assertEquals(1.99, content.stations.getValue("fuel:a").price(FuelType.E10)!!.eurPerLitre, 1e-9)
            vm.onFuelSort(FuelSort.PRICE); advanceTimeBy(10)
            s = expectMostRecentItem()
            assertEquals(listOf("fuel:b", "fuel:a", "fuel:c"), (s.results as SearchResults.Content).places.map { it.id })
            vm.onFuel(FuelType.DIESEL); advanceTimeBy(10)
            s = expectMostRecentItem()
            assertEquals(listOf("fuel:b", "fuel:a", "fuel:c"), (s.results as SearchResults.Content).places.map { it.id })
            assertEquals(1, fuel.calls)
        }
    }

    @Test fun `fuel flow failure falls back to OSM places without prices`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(43.6, 1.44), null, null, 5f, 0L)
        fuel.next = Outcome.Failure(AppError.Timeout)
        nearby.next = Outcome.Success(listOf(paris))
        val vm = vm()
        vm.uiState.test {
            vm.onCategory(PoiCategory.FUEL); advanceTimeBy(10)
            val content = expectMostRecentItem().results as SearchResults.Content
            assertEquals(listOf(paris), content.places)
            assertTrue(content.stations.isEmpty())
        }
    }

    @Test fun `category chip queries nearby provider at once and typing clears it`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(43.6, 1.44), null, null, 5f, 0L)
        nearby.next = Outcome.Success(listOf(paris))
        val vm = vm()
        vm.uiState.test {
            vm.onCategory(PoiCategory.CHARGING); advanceTimeBy(10)
            val s = expectMostRecentItem()
            assertEquals(listOf(PoiCategory.CHARGING), nearby.calls)
            assertEquals(PoiCategory.CHARGING, s.category)
            assertEquals(PoiCategory.CHARGING, (s.results as SearchResults.Content).category)
            vm.onCategory(PoiCategory.CHARGING); advanceTimeBy(10)
            assertEquals(null, expectMostRecentItem().category)
            vm.onCategory(PoiCategory.PARKING); vm.onQueryChange("Paris"); advanceTimeBy(400)
            assertEquals(null, expectMostRecentItem().category)
            assertEquals(listOf("Paris"), geocoder.queries)
        }
    }

    @Test fun `category from the route starts a nearby search at once`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(43.6, 1.44), null, null, 5f, 0L)
        nearby.next = Outcome.Success(listOf(paris))
        val vm = SearchViewModel(geocoder, recents, favorites, location, connectivity, nearby, fuel, vehicle, SavedStateHandle(mapOf("category" to "PARKING")))
        vm.uiState.test {
            advanceTimeBy(10)
            val s = expectMostRecentItem()
            assertEquals(PoiCategory.PARKING, s.category)
            assertEquals(listOf(PoiCategory.PARKING), nearby.calls)
        }
    }

    @Test fun `category without a known position reports NoResult and does not call the provider`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.onCategory(PoiCategory.CHARGING); advanceTimeBy(10)
            assertEquals(AppError.NoResult, (expectMostRecentItem().results as SearchResults.Error).error)
            assertEquals(0, nearby.calls.size)
        }
    }

    @Test fun `short query stays idle and does not call geocoder`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.onQueryChange("P")
            advanceTimeBy(500)
            assertTrue(expectMostRecentItem().results is SearchResults.Idle)
        }
        assertEquals(0, geocoder.queries.size)
    }

    @Test fun `query is debounced 300 ms then results shown`() = runTest {
        geocoder.next = Outcome.Success(listOf(paris))
        val vm = vm()
        vm.uiState.test {
            vm.onQueryChange("Pa"); vm.onQueryChange("Par"); vm.onQueryChange("Pari")
            advanceTimeBy(200)
            assertEquals(0, geocoder.queries.size)
            advanceTimeBy(200)
            val s = expectMostRecentItem()
            assertEquals(listOf("Pari"), geocoder.queries)
            assertEquals(listOf(paris), (s.results as SearchResults.Content).places)
        }
    }

    @Test fun `provider error is surfaced`() = runTest {
        geocoder.next = Outcome.Failure(AppError.Timeout)
        val vm = vm()
        vm.uiState.test {
            vm.onQueryChange("Paris"); advanceTimeBy(400)
            assertEquals(AppError.Timeout, (expectMostRecentItem().results as SearchResults.Error).error)
        }
    }

    @Test fun `offline does not call network and keeps favorites and recents`() = runTest {
        connectivity.online.value = false
        recents.items.value = listOf(paris)
        val vm = vm()
        vm.uiState.test {
            vm.onQueryChange("Paris"); advanceTimeBy(400)
            val s = expectMostRecentItem()
            assertEquals(0, geocoder.queries.size)
            assertTrue(s.results is SearchResults.Error && (s.results as SearchResults.Error).error == AppError.Network)
            assertEquals(listOf(paris), s.recents)
        }
    }

    @Test fun `selected place exposes distance from last known position`() = runTest {
        val here = GeoPoint(48.85, 2.35)
        location.lastKnown = LocationFix(here, null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            vm.onPlaceSelected(paris); advanceTimeBy(10)
            val s = expectMostRecentItem()
            assertEquals(here.distanceTo(paris.point), s.selectedDistanceMeters!!, 1.0)
        }
    }

    @Test fun `selecting a place records it as recent and exposes favorite flag`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.onPlaceSelected(paris); advanceTimeBy(10)
            val s = expectMostRecentItem()
            assertEquals(paris, s.selected)
            assertEquals(listOf(paris), s.recents)
            vm.onToggleFavorite(); advanceTimeBy(10)
            assertTrue(expectMostRecentItem().selectedIsFavorite)
        }
    }
}
