package fr.roadly.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.BranchDiagram
import fr.roadly.core.navigation.BranchDiagrams
import fr.roadly.core.navigation.DiagramBranch
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.GuidanceDensity
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.GuidanceSituation
import fr.roadly.core.navigation.ManeuverSide
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RoundaboutPhase
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures JVM (Robolectric, rendu natif) de l'écran de guidage COMPLET : les vrais `PortraitOverlay` et `LandscapeOverlay`
 * (pile du bandeau, colonne d'actions, pastilles d'état, compteur, ruban Horizon, panneau du bas), avec un état fixe.
 *
 * ATTENTION — ce ne sont PAS des captures de carte : MapLibre ne peut pas dessiner sur la JVM. Le fond est un aplat neutre,
 * barré d'un trait épais qui tient lieu de tracé et étiqueté « fond de test (pas une carte) ». Seule la mise en page des
 * overlays est représentative ; la lisibilité réelle sur la carte (contraste, flèche de manœuvre, voiture) se juge sur appareil.
 *
 * Le `NavigationViewModel` est réel, bâti sur les mêmes faux que `NavigationViewModelTest` ; sa session reste au repos, l'état
 * affiché est passé explicitement aux overlays. La vue 3D de carrefour (OpenGL ES 3) n'est pas rendue ici.
 * Les images vont dans `build/outputs/roborazzi/full_<nom>.png` ; aucune référence n'est versionnée (on regarde, on ne compare pas).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PORTRAIT)
class NavigationFullScreenScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }
    private val fakeVehicle = object : fr.roadly.core.data.repository.VehicleProfileRepository {
        override val profile = flowOf<fr.roadly.core.model.VehicleProfile?>(null)
        override suspend fun save(profile: fr.roadly.core.model.VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }

    @After fun tearDown() = scope.cancel()

    /** Même montage que `NavigationViewModelTest` : session au repos, horloge figée. */
    private fun viewModel(): NavigationViewModel {
        // Répartiteur de test jamais avancé : la session ne fait rien (`limitedParallelism` exclut `Dispatchers.Unconfined`).
        val d = StandardTestDispatcher()
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d
            override val default: CoroutineDispatcher = d
            override val main: CoroutineDispatcher = d
        }
        val session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { 0L }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, scope)
        return NavigationViewModel(session, prefs, config, GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, fakeVehicle, NavigationClock { 0L }), NoopCommunityForShots)
    }

    /** Overlay réel posé sur un aplat neutre (pas une carte) ; [landscape] choisit l'overlay, comme `NavigationContent`. */
    private fun shot(name: String, dark: Boolean, state: NavigationUiState, landscape: Boolean = false, montage: Color? = null) {
        val vm = viewModel()
        compose.setContent {
            RoadlyTheme(darkTheme = dark, drivingMode = true) {
                Box(Modifier.fillMaxSize().background(montage ?: if (dark) Color(0xFF2A3140) else Color(0xFFD9DDE3))) {
                    val line = if (dark) Color(0xFF596274) else Color(0xFFA9B0BC)
                    // [montage] : aplat uni, sans trait ni étiquette, que l'outil d'images du README remplace par un rendu de carte.
                    if (montage == null) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(line, Offset(size.width * 0.5f, size.height), Offset(size.width * 0.5f, size.height * 0.45f), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
                        }
                        Text("fond de test (pas une carte)", Modifier.align(BiasAlignment(0.2f, -0.3f)), color = line, fontSize = 12.sp)
                    }
                    if (landscape) {
                        LandscapeOverlay(state, true, {}, {}, {}, vm, {}, {})
                    } else {
                        PortraitOverlay(state, true, {}, {}, {}, vm, {}, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/full_$name.png")
    }

    // ---- États ----

    private fun guiding(banner: BannerUi, situation: GuidanceSituation, speed: Double = 78.0, limit: Int? = 80) = NavigationUiState(
        phase = NavigationPhase.GUIDING,
        banner = banner,
        situation = situation,
        speedKmh = speed,
        speedLimitKmh = limit,
        etaClock = "14:32",
        remainingDuration = "42 min",
        remainingDistance = "38 km",
        remainingDurationSplit = SplitValue("42", "min"),
        remainingDistanceSplit = SplitValue("38", "km"),
        upcoming = listOf(
            UpcomingUi(ManeuverType.TURN_RIGHT, "1,2 km", "Route de Narbonne", 1200.0),
            UpcomingUi(ManeuverType.KEEP_LEFT, "3,4 km", "D 813", 3400.0),
        ),
        hazards = listOf(
            HazardUi(HazardKind.CURVE, 800.0, "800 m", null, 70.0),
            HazardUi(HazardKind.VIGILANCE_ZONE, 2400.0, "2,4 km", 80, null),
        ),
    )

    /** Route normale : densité minimale, manœuvre lointaine, 78 pour 80. */
    private fun cruise() = guiding(
        BannerUi(ManeuverType.TURN_RIGHT, "1,2 km", "Tournez à droite sur Route de Narbonne.", "Route de Narbonne", null, null),
        GuidanceSituation(GuidanceMode.CRUISE, GuidanceDensity.MINIMAL),
    )

    private fun lane(valid: Boolean, active: Boolean, vararg d: LaneDirection) = Lane(d.toSet(), valid, active)

    /** Intersection chargée : 4 voies, ligne « puis ». */
    private fun complexLanes() = guiding(
        BannerUi(ManeuverType.TURN_LEFT, "180 m", "Tournez à gauche sur Boulevard de Strasbourg.", "Boulevard de Strasbourg", null, null),
        GuidanceSituation(GuidanceMode.COMPLEX, GuidanceDensity.REINFORCED, urgency = 0.6f, side = ManeuverSide.LEFT, chained = true),
        speed = 46.0, limit = 50,
    ).copy(
        lanes = listOf(
            lane(true, true, LaneDirection.LEFT), lane(true, false, LaneDirection.LEFT, LaneDirection.STRAIGHT),
            lane(false, false, LaneDirection.STRAIGHT), lane(false, false, LaneDirection.STRAIGHT, LaneDirection.RIGHT),
        ),
        showLanes = true,
        approach = ApproachUi(ManeuverType.TURN_LEFT, null, null, 0),
        then = ThenUi(ManeuverType.TURN_RIGHT, "Rue de Bayard"),
    )

    /** Dans l'anneau, décompte des sorties (2 passées sur 4). */
    private fun roundaboutInside(): NavigationUiState {
        val live = previewRoundaboutLive(4, 2, RoundaboutPhase.INSIDE)
        return guiding(
            BannerUi(ManeuverType.ROUNDABOUT_EXIT, "40 m", "Au rond-point, prenez la 4e sortie.", "Avenue des Champs-Élysées", null, 4),
            GuidanceSituation(GuidanceMode.ROUNDABOUT, GuidanceDensity.REINFORCED, urgency = live.ringFraction),
            speed = 28.0, limit = 50,
        ).copy(roundabout = previewRoundabout(live.layout.sweepDeg ?: 180.0, 4, "Avenue des Champs-Élysées"), roundaboutLive = live)
    }

    private fun branches(vararg b: Triple<Double, Boolean, Boolean>) =
        BranchDiagram(0, BranchDiagrams.spread(b.map { DiagramBranch(it.first, it.first, it.second, it.third) }))

    /** Sortie d'autoroute : panneau, schéma de bifurcation, « Serrez à droite ». */
    private fun motorwayExit() = guiding(
        BannerUi(ManeuverType.EXIT_RIGHT, "350 m", "Prenez la sortie 19 à droite vers Montpellier.", "A 61", "19", null, "Montpellier · Carcassonne"),
        GuidanceSituation(GuidanceMode.EXIT, GuidanceDensity.REINFORCED, urgency = 0.55f, side = ManeuverSide.RIGHT),
        speed = 118.0, limit = 130,
    ).copy(
        sign = ExitSignUi("19", "Montpellier · Carcassonne", "A 61"),
        approach = ApproachUi(ManeuverType.EXIT_RIGHT, "19", "Montpellier · Carcassonne", 0),
        branchDiagram = branches(Triple(0.0, false, false), Triple(5.0, true, true)),
    )

    private fun rerouting() = cruise().copy(rerouting = true, situation = GuidanceSituation(GuidanceMode.REROUTING, GuidanceDensity.STANDARD))

    private fun gpsUncertain() = cruise().copy(situation = GuidanceSituation(GuidanceMode.GPS_UNCERTAIN, GuidanceDensity.STANDARD))

    private fun gpsLost() = gpsUncertain().copy(gpsLost = true, speedKmh = null)

    /** Excès de vitesse (97 pour 80), zone de danger à venir et prochaine limitation. */
    private fun overspeed() = cruise().copy(
        speedKmh = 97.0,
        radar = RadarUi(inside = false, distance = "600 m", limitKmh = 80, confidence = 85),
        nextLimit = NextLimitUi(50, "300 m"),
    )

    /** Zone de danger en cours de traversée, sortie numérotée lointaine : pastille de sortie, ruban « Vigilance active ». */
    private fun vigilanceActive() = guiding(
        BannerUi(ManeuverType.EXIT_RIGHT, "8,9 km", "Prenez la sortie 7 à droite vers Viry-Châtillon.", "Viry-Châtillon · Fleury-Mérogis", "7", null),
        GuidanceSituation(GuidanceMode.CRUISE, GuidanceDensity.MINIMAL),
        speed = 91.0, limit = 110,
    ).copy(
        etaClock = "20:23", remainingDuration = "51 min", remainingDistance = "15 km",
        remainingDurationSplit = SplitValue("51", "min"), remainingDistanceSplit = SplitValue("15", "km"),
        radar = RadarUi(inside = true, distance = "1,4 km", limitKmh = 110, confidence = 90),
    )

    private fun arrived() = NavigationUiState(phase = NavigationPhase.ARRIVED)

    // ---- Portrait, nuit et jour ----

    @Test fun cruise_night() = shot("cruise_night", true, cruise())
    @Test fun cruise_day() = shot("cruise_day", false, cruise())
    @Test fun complex_lanes_night() = shot("complex_lanes_night", true, complexLanes())
    @Test fun complex_lanes_day() = shot("complex_lanes_day", false, complexLanes())
    @Test fun roundabout_inside_night() = shot("roundabout_inside_night", true, roundaboutInside())
    @Test fun roundabout_inside_day() = shot("roundabout_inside_day", false, roundaboutInside())
    @Test fun motorway_exit_night() = shot("motorway_exit_night", true, motorwayExit())
    @Test fun motorway_exit_day() = shot("motorway_exit_day", false, motorwayExit())
    @Test fun rerouting_night() = shot("rerouting_night", true, rerouting())
    @Test fun rerouting_day() = shot("rerouting_day", false, rerouting())
    @Test fun gps_uncertain_night() = shot("gps_uncertain_night", true, gpsUncertain())
    @Test fun gps_uncertain_day() = shot("gps_uncertain_day", false, gpsUncertain())
    @Test fun gps_lost_night() = shot("gps_lost_night", true, gpsLost())
    @Test fun gps_lost_day() = shot("gps_lost_day", false, gpsLost())
    @Test fun overspeed_night() = shot("overspeed_night", true, overspeed())
    @Test fun overspeed_day() = shot("overspeed_day", false, overspeed())
    @Test fun arrived_night() = shot("arrived_night", true, arrived())
    @Test fun arrived_day() = shot("arrived_day", false, arrived())
    @Test fun vigilance_night() = shot("vigilance_night", true, vigilanceActive())
    @Test fun vigilance_day() = shot("vigilance_day", false, vigilanceActive())
    /**
     * Base du montage des images du README (`docs/images`) : le même écran sur fond noir puis blanc. La différence des deux
     * rendus donne la transparence exacte des panneaux et de leurs ombres, que l'outil pose ensuite sur un rendu de carte.
     */
    @Test fun vigilance_night_montage_black() = shot("vigilance_night_montage_black", true, vigilanceActive(), montage = Color.Black)
    @Test fun vigilance_night_montage_white() = shot("vigilance_night_montage_white", true, vigilanceActive(), montage = Color.White)

    // ---- Paysage, nuit : les trois situations les plus chargées ----

    @Config(qualifiers = LANDSCAPE)
    @Test fun landscape_complex_lanes_night() = shot("landscape_complex_lanes_night", true, complexLanes(), landscape = true)

    @Config(qualifiers = LANDSCAPE)
    @Test fun landscape_roundabout_inside_night() = shot("landscape_roundabout_inside_night", true, roundaboutInside(), landscape = true)

    @Config(qualifiers = LANDSCAPE)
    @Test fun landscape_motorway_exit_night() = shot("landscape_motorway_exit_night", true, motorwayExit(), landscape = true)
}

private const val PORTRAIT = "w411dp-h891dp-420dpi"
private const val LANDSCAPE = "w891dp-h411dp-land-420dpi"

private object NoopCommunityForShots : fr.roadly.core.data.community.CommunityRepository {
    override val isConfigured = flowOf(false)
    override suspend fun report(kind: fr.roadly.core.model.ReportKind, at: fr.roadly.core.model.GeoPoint, headingDeg: Float?) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun nearby(box: fr.roadly.core.model.BoundingBox) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun vote(reportId: String, confirm: Boolean) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun profile() = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun leaderboard(limit: Int) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
}
