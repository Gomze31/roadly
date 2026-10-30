package fr.roadly.feature.settings

import app.cash.turbine.test
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.Place
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val vehicle = object : VehicleProfileRepository {
        val p = MutableStateFlow<VehicleProfile?>(null)
        override val profile = p
        override suspend fun save(profile: VehicleProfile) { p.value = profile }
        override suspend fun clear() { p.value = null }
    }
    private val prefs = object : UserPreferencesRepository {
        val t = MutableStateFlow(ThemeMode.AUTO_SOLAR)
        override val themeMode = t
        override suspend fun setThemeMode(mode: ThemeMode) { t.value = mode }
        override val locationRationaleSeen = flowOf(false)
        override suspend fun setLocationRationaleSeen() = Unit
        override val locationDeniedForever = flowOf(false)
        override suspend fun setLocationDeniedForever(value: Boolean) = Unit
    }
    private var overrideCalls = 0
    private val config = object : ProviderConfigRepository {
        val c = MutableStateFlow(ProviderConfig.DEFAULT)
        override val config = c
        override suspend fun override(config: ProviderConfig) { overrideCalls++; c.value = config }
        override suspend fun reset() { c.value = ProviderConfig.DEFAULT }
    }
    private var recentsCleared = false
    private val recents = object : RecentSearchRepository {
        override fun observe() = flowOf(emptyList<Place>())
        override suspend fun record(place: Place) = Unit
        override suspend fun clear() { recentsCleared = true }
    }
    private val favorites = object : FavoritesRepository {
        override fun observeAll() = flowOf(emptyList<Place>()); override fun observeHome() = flowOf<Place?>(null); override fun observeWork() = flowOf<Place?>(null)
        override suspend fun isFavorite(id: String) = false; override suspend fun toggle(place: Place) = Unit
        override suspend fun setHome(place: Place) = Unit; override suspend fun setWork(place: Place) = Unit; override suspend fun clear() = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()
    private fun vm() = SettingsViewModel(vehicle, prefs, config, recents, favorites)

    @Test fun `saving vehicle parses french decimals and rejects invalid price`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.saveVehicle(VehicleType.SUV, FuelType.E10, consumption = "7,5", price = "1,89"); advanceUntilIdle()
            assertEquals(VehicleProfile(VehicleType.SUV, FuelType.E10, 7.5, 1.89), expectMostRecentItem().vehicle)
            vm.saveVehicle(VehicleType.SUV, FuelType.E10, consumption = "7,5", price = ""); advanceUntilIdle()
            assertNull(expectMostRecentItem().vehicle!!.fuelPriceEur)
        }
    }

    @Test fun `theme change and clear history`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setTheme(ThemeMode.DARK); advanceUntilIdle()
            assertEquals(ThemeMode.DARK, expectMostRecentItem().themeMode)
            vm.clearHistory(); advanceUntilIdle()
            assertEquals(true, recentsCleared)
        }
    }

    @Test fun `invalid provider url sets error and is not saved`() = runTest {
        val vm = vm()
        vm.saveProviders(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = "pas une url")); advanceUntilIdle()
        assertEquals("pas une url", vm.providerError.value)
        assertEquals(0, overrideCalls)
    }

    @Test fun `valid provider urls are normalised and saved`() = runTest {
        val vm = vm()
        vm.saveProviders(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = "  https://valhalla.exemple.fr/  ")); advanceUntilIdle()
        assertNull(vm.providerError.value)
        assertEquals("https://valhalla.exemple.fr", config.c.value.valhallaBaseUrl)
    }
}
