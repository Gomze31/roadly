package fr.roadly.feature.settings

import fr.roadly.core.common.Outcome
import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.model.BoundingBox
import app.cash.turbine.test
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.Place
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.testing.FakeSpeechService
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
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
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
    private val vigilance = object : VigilanceRepository {
        override val count = MutableStateFlow(0)
        override val updatedAtMs = MutableStateFlow<Long?>(null)
        override suspend fun zonesIn(box: BoundingBox) = emptyList<VigilanceZoneEntity>()
        override suspend fun refresh(): Outcome<Int> { count.value = 3; updatedAtMs.value = 1L; return Outcome.Success(3) }
        override suspend fun refreshIfStale(nowMs: Long): Outcome<Int>? = null
    }

    private val communityCfg = object : fr.roadly.core.data.community.CommunityConfigRepository {
        val c = MutableStateFlow(fr.roadly.core.data.community.CommunityConfig())
        override val config = c
        override suspend fun save(config: fr.roadly.core.data.community.CommunityConfig) { c.value = config }
        override suspend fun clear() { c.value = fr.roadly.core.data.community.CommunityConfig() }
        override suspend fun deviceId() = "dev"
    }

    private fun vm() = SettingsViewModel(vehicle, prefs, config, recents, favorites, speech, vigilance, communityCfg)

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

    @Test fun `voice settings are exposed and persisted`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setVoiceEnabled(false); vm.setVoiceVolume(140); vm.setOverSpeedSound(true); advanceUntilIdle()
            val v = expectMostRecentItem().voice
            assertEquals(false, v.enabled)
            assertEquals(100, v.volume)
            assertEquals(true, v.overSpeedSound)
            assertEquals(false, prefs.voice.value)
        }
    }

    @Test fun `speed tolerance accepts only 0 5 10`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setSpeedTolerance(10); advanceUntilIdle()
            assertEquals(10, expectMostRecentItem().voice.toleranceKmh)
            vm.setSpeedTolerance(7); advanceUntilIdle()
            assertEquals(10, prefs.tolerance.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `route preference switches are independent`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setAvoidTolls(true); advanceUntilIdle()
            vm.setAvoidFerries(true); advanceUntilIdle()
            assertEquals(RoutePreferences(avoidTolls = true, avoidHighways = false, avoidFerries = true), expectMostRecentItem().routePreferences)
            vm.setAvoidTolls(false); vm.setAvoidHighways(true); advanceUntilIdle()
            assertEquals(RoutePreferences(avoidTolls = false, avoidHighways = true, avoidFerries = true), expectMostRecentItem().routePreferences)
        }
    }

    @Test fun `test voice applies the saved volume then speaks at once`() = runTest {
        prefs.setVoiceVolume(40)
        val vm = vm()
        vm.testVoice("Essai de voix."); advanceUntilIdle()
        assertEquals(40, speech.volume)
        assertEquals(listOf("Essai de voix."), speech.spoken)
    }

    @Test fun `map detail level is exposed and persisted`() = runTest {
        val vm = vm()
        vm.mapDetail.test {
            assertEquals(MapDetail.NORMAL, awaitItem())
            vm.setMapDetail(MapDetail.REDUCED); advanceUntilIdle()
            assertEquals(MapDetail.REDUCED, expectMostRecentItem())
            assertEquals(MapDetail.REDUCED, prefs.detail.value)
        }
    }
}
