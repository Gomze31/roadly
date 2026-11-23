package fr.roadly.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.GuidanceDensity
import fr.roadly.core.navigation.GuidanceMode
import fr.roadly.core.navigation.GuidanceSituation
import fr.roadly.core.navigation.ManeuverSide
import fr.roadly.core.navigation.RoundaboutPhase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures JVM (Robolectric, rendu natif) de la pile du bandeau dans ses situations clés : ce que le conducteur voit en haut de
 * l'écran. Les images vont dans `build/outputs/roborazzi/` ; elles servent à regarder le rendu sans appareil, pas à comparer des
 * pixels (aucune référence n'est versionnée). États fixes, aucune horloge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class GuidanceScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            RoadlyTheme(darkTheme = dark, drivingMode = true) {
                Column(
                    Modifier.background(if (dark) Color(0xFF1B2230) else Color(0xFFE6E9EE)).padding(12.dp).width(387.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun roundaboutState(exits: Int, passed: Int, phase: RoundaboutPhase, distance: String, counted: Boolean = true): NavigationUiState {
        val live = previewRoundaboutLive(exits, passed, phase, counted = counted)
        val mode = when (phase) {
            RoundaboutPhase.APPROACH -> GuidanceMode.ROUNDABOUT_APPROACH
            RoundaboutPhase.INSIDE -> GuidanceMode.ROUNDABOUT
            RoundaboutPhase.EXITED -> GuidanceMode.ROUNDABOUT_EXITED
        }
        val type = if (phase == RoundaboutPhase.APPROACH) ManeuverType.ROUNDABOUT_ENTER else ManeuverType.ROUNDABOUT_EXIT
        return NavigationUiState(
            phase = NavigationPhase.GUIDING,
            banner = BannerUi(type, distance, "Au rond-point, prenez la ${exits}e sortie.", "Avenue des Champs-Élysées", null, exits),
            roundabout = previewRoundabout(live.layout.sweepDeg ?: 180.0, exits, "Avenue des Champs-Élysées"),
            roundaboutLive = live,
            situation = GuidanceSituation(mode, GuidanceDensity.REINFORCED, urgency = if (phase == RoundaboutPhase.APPROACH) 0.45f else live.ringFraction),
        )
    }

    private fun exitState(urgency: Float, distance: String) = NavigationUiState(
        phase = NavigationPhase.GUIDING,
        banner = BannerUi(ManeuverType.EXIT_RIGHT, distance, "Prenez la sortie 19 à droite vers Montpellier.", "A 61", "19", null, "Montpellier · Carcassonne"),
        situation = GuidanceSituation(GuidanceMode.EXIT, GuidanceDensity.REINFORCED, urgency, side = ManeuverSide.RIGHT),
        then = ThenUi(ManeuverType.KEEP_LEFT, "A 61 · Narbonne"),
    )

    @Test fun roundabout_mode_night() = shot("roundabout_mode_night", dark = true) {
        BannerStack(roundaboutState(4, 0, RoundaboutPhase.APPROACH, "140 m"), expanded = true, onOpenPreview = {})
        BannerStack(roundaboutState(4, 1, RoundaboutPhase.INSIDE, "60 m"), expanded = false, onOpenPreview = {})
        BannerStack(roundaboutState(4, 3, RoundaboutPhase.INSIDE, "20 m"), expanded = false, onOpenPreview = {})
    }

    @Test fun roundabout_mode_day() = shot("roundabout_mode_day", dark = false) {
        BannerStack(roundaboutState(3, 1, RoundaboutPhase.INSIDE, "45 m"), expanded = false, onOpenPreview = {})
        BannerStack(roundaboutState(2, 1, RoundaboutPhase.EXITED, "0 m"), expanded = false, onOpenPreview = {})
        BannerStack(roundaboutState(5, 0, RoundaboutPhase.INSIDE, "80 m", counted = false), expanded = false, onOpenPreview = {})
    }

    @Test fun exit_preparation_night() = shot("exit_preparation_night", dark = true) {
        BannerStack(exitState(0.15f, "700 m"), expanded = true, onOpenPreview = {})
        BannerStack(exitState(0.8f, "150 m"), expanded = false, onOpenPreview = {})
    }

    private fun branches(vararg b: Triple<Double, Boolean, Boolean>) =
        fr.roadly.core.navigation.BranchDiagram(0, fr.roadly.core.navigation.BranchDiagrams.spread(b.map { fr.roadly.core.navigation.DiagramBranch(it.first, it.first, it.second, it.third) }))

    private fun lane(valid: Boolean, active: Boolean, vararg d: fr.roadly.core.model.LaneDirection) = fr.roadly.core.model.Lane(d.toSet(), valid, active)

    /** Sortie à droite (bretelle à 5°), puis bifurcation à trois branches avec voies réelles, puis GPS imprécis dans un rond-point. */
    @Test fun branch_diagram_night() = shot("branch_diagram_night", dark = true) {
        val exit = exitState(0.55f, "350 m").copy(
            sign = ExitSignUi("19", "Montpellier · Carcassonne", "A 61"), approach = ApproachUi(ManeuverType.EXIT_RIGHT, "19", "Montpellier · Carcassonne", 0),
            branchDiagram = branches(Triple(0.0, false, false), Triple(5.0, true, true)), then = null,
        )
        BannerStack(exit, expanded = false, onOpenPreview = {})
        val straight = fr.roadly.core.model.LaneDirection.STRAIGHT
        val fork = NavigationUiState(
            phase = NavigationPhase.GUIDING,
            banner = BannerUi(ManeuverType.KEEP_LEFT, "280 m", "Serrez à gauche pour prendre A 620.", "A 620", null, null, "Bordeaux"),
            situation = GuidanceSituation(GuidanceMode.FORK, GuidanceDensity.REINFORCED, 0.5f, side = ManeuverSide.LEFT),
            branchDiagram = branches(Triple(-6.0, true, false), Triple(4.0, false, false), Triple(35.0, false, true)),
            lanes = listOf(lane(true, true, fr.roadly.core.model.LaneDirection.SLIGHT_LEFT), lane(true, true, fr.roadly.core.model.LaneDirection.SLIGHT_LEFT), lane(false, false, straight), lane(false, false, fr.roadly.core.model.LaneDirection.SLIGHT_RIGHT)),
            showLanes = true,
        )
        BannerStack(fork, expanded = false, onOpenPreview = {})
        val unsure = roundaboutState(4, 1, RoundaboutPhase.INSIDE, "60 m")
        BannerStack(unsure.copy(roundaboutLive = unsure.roundaboutLive!!.copy(confidence = fr.roadly.core.navigation.RoundaboutConfidence.LOW)), expanded = false, onOpenPreview = {})
    }

    @Test fun branch_diagram_day() = shot("branch_diagram_day", dark = false) {
        val exit = exitState(0.55f, "350 m").copy(
            sign = ExitSignUi("19", "Montpellier · Carcassonne", "A 61"), approach = ApproachUi(ManeuverType.EXIT_RIGHT, "19", "Montpellier · Carcassonne", 0),
            branchDiagram = branches(Triple(0.0, false, false), Triple(5.0, true, true)), then = null,
        )
        BannerStack(exit, expanded = false, onOpenPreview = {})
    }

    @Test fun exit_preparation_day() = shot("exit_preparation_day", dark = false) {
        BannerStack(exitState(0.5f, "400 m"), expanded = true, onOpenPreview = {})
    }
}
