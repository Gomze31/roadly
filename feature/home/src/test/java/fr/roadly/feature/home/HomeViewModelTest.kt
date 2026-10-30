package fr.roadly.feature.home

import app.cash.turbine.test
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.connectivity.ConnectivityObserver
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationMode
import fr.roadly.core.location.PermissionEvents
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.map.CameraMode
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationRepository()
    private val reverseCalls = mutableListOf<GeoPoint>()
    private val geocoder = object : GeocodingProvider {
        override val id = "fake"
        override suspend fun search(query: String, near: GeoPoint?, limit: Int) = Outcome.Success(emptyList<Place>())
        override suspend fun reverse(point: GeoPoint): Outcome<Place?> { reverseCalls += point; return Outcome.Success(Place("r", "Rue Test", "75001 Paris", point, PlaceKind.ADDRESS, "fake")) }
    }
    private val favorites = object : FavoritesRepository {
        override fun observeAll() = flowOf(emptyList<Place>())
        override fun observeHome() = flowOf<Place?>(null)
        override fun observeWork() = flowOf<Place?>(null)
        override suspend fun isFavorite(id: String) = false
        override suspend fun toggle(place: Place) = Unit
        override suspend fun setHome(place: Place) = Unit
        override suspend fun setWork(place: Place) = Unit
        override suspend fun clear() = Unit
    }
    private val connectivity = object : ConnectivityObserver { override val isOnline = MutableStateFlow(true) }
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun fix(lat: Double, lon: Double) = LocationFix(GeoPoint(lat, lon), 90f, 13.9f, 5f, 0L)
    private fun vm() = HomeViewModel(location, favorites, geocoder, connectivity, config, PermissionEvents())

    @Test fun `collecting state switches location to MAP mode and exposes speed in km per h`() = runTest {
        val vm = vm()
        vm.uiState.test {
            location.emit(fix(48.85, 2.35)); advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(LocationMode.MAP, location.mode)
            assertEquals(50.0, s.speedKmh!!, 0.1)
            assertEquals("Rue Test, 75001 Paris", s.address)
        }
    }

    @Test fun `reverse geocode only after moving more than 50 m`() = runTest {
        val vm = vm()
        vm.uiState.test {
            location.emit(fix(48.8500, 2.3500)); advanceUntilIdle()
            location.emit(fix(48.8501, 2.3500)); advanceUntilIdle() // ~11 m
            location.emit(fix(48.8510, 2.3500)); advanceUntilIdle() // ~111 m
            expectMostRecentItem()
            assertEquals(2, reverseCalls.size)
        }
    }

    @Test fun `user gesture frees camera and recenter cycles CENTERED then FOLLOW`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.onUserGesture(); advanceUntilIdle()
            assertEquals(CameraMode.FREE, expectMostRecentItem().cameraMode)
            vm.onRecenter(); advanceUntilIdle()
            assertEquals(CameraMode.CENTERED, expectMostRecentItem().cameraMode)
            vm.onRecenter(); advanceUntilIdle()
            assertEquals(CameraMode.FOLLOW, expectMostRecentItem().cameraMode)
            assertEquals(LocationMode.FOLLOW, location.mode)
        }
    }
}
