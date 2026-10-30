package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationRepository()
    private val vehicle = object : VehicleProfileRepository {
        val p = MutableStateFlow<VehicleProfile?>(null)
        override val profile = p
        override suspend fun save(profile: VehicleProfile) { p.value = profile }
        override suspend fun clear() { p.value = null }
    }
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    private var routing: RoutingProvider = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(
            Route(request.costing.name, request.costing, 10_000.0, 600.0, listOf(request.origin, request.destination), emptyList(), false, "fake"),
        ))
    }
    private val handle = SavedStateHandle(mapOf("destLat" to 48.9, "destLon" to 2.4, "destName" to "Test"))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config)

    @Test fun `without known position state is NoOrigin`() = runTest {
        vm().uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertTrue(s.phase is RouteUiState.NoOrigin)
            assertTrue("la carte doit rester affichable sans position", s.styleUrl.isNotEmpty())
        }
    }

    @Test fun `with position computes three options and selects fastest`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        vm().uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(3, s.options.size)
            assertEquals(RouteCosting.FASTEST, s.options.first { it.route.id == s.activeId }.route.costing)
            assertTrue(s.options.all { it.cost == null })
        }
    }

    @Test fun `vehicle profile adds cost estimate`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        vehicle.p.value = VehicleProfile(fr.roadly.core.model.VehicleType.SEDAN, fr.roadly.core.model.FuelType.DIESEL, 5.0, 1.70)
        vm().uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(0.85, s.options.first().cost!!.fuelEur, 0.001)
        }
    }

    @Test fun `routing failure is surfaced and origin can be picked manually`() = runTest {
        routing = object : RoutingProvider { override val id = "fake"; override suspend fun route(request: RouteRequest) = Outcome.Failure(AppError.Timeout) }
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            assertEquals(AppError.Timeout, (expectMostRecentItem().phase as RouteUiState.Error).error)
        }
    }

    @Test fun `setOrigin from NoOrigin triggers computation`() = runTest {
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            assertTrue(expectMostRecentItem().phase is RouteUiState.NoOrigin)
            vm.setOrigin(GeoPoint(48.8, 2.3))
            advanceUntilIdle()
            val s = expectMostRecentItem().phase as RouteUiState.Content
            assertEquals(3, s.options.size)
            assertEquals(GeoPoint(48.8, 2.3), s.origin)
        }
    }
}
