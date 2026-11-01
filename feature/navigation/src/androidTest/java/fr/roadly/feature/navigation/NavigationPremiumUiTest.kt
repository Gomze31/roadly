package fr.roadly.feature.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Étape D sur appareil : bandeau → aperçu (D1), rond-point « vers {rue} » (D2, scénario 4), voies (D4, scénario 18),
 * panneau du bas, poignée, ligne ETA → trajet complet, Retour et caméra (D5), paysage (scénario 19).
 */
class NavigationPremiumUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Nombre de Retours reçus par le gestionnaire de l'écran (`BackHandler { vm.stop() }` de `NavigationContent`, reproduit ici). */
    private var screenBackPresses = 0

    private fun newVm(): NavigationViewModel = rule.runOnUiThread { testNavigationViewModel() }

    /** Poignée du panneau : pas de description, un libellé d'action (« Afficher / Masquer les détails du trajet »). */
    private fun hasClickLabel(label: String) =
        SemanticsMatcher("onClickLabel = '$label'") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private val expandHandle = hasClickLabel("Afficher les détails du trajet")
    private val collapseHandle = hasClickLabel("Masquer les détails du trajet")

    private fun setOverlay(state: NavigationUiState, vm: NavigationViewModel, landscape: Boolean = false, bannerExpanded: Boolean = true) {
        rule.setContent {
            RoadlyTheme(darkTheme = true, drivingMode = true) {
                // Même ordre que `NavigationContent` : le Retour de l'écran (arrêt) est composé avant les overlays.
                BackHandler { screenBackPresses++ }
                if (landscape) {
                    // HONOR en paysage, barres retirées : ~1 021 × 457 dp.
                    DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1021.dp, 457.dp))) {
                        Box(Modifier.fillMaxSize()) { LandscapeOverlay(state, bannerExpanded, {}, {}, {}, vm); GuidanceSheets(state, vm) }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) { PortraitOverlay(state, bannerExpanded, {}, {}, {}, vm); GuidanceSheets(state, vm) }
                }
            }
        }
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun banner_click_opens_the_maneuver_preview_and_close_dismisses_it() {
        val vm = newVm()
        setOverlay(guidingUi(TestRoutes.straight(), 300.0), vm)
        rule.onNodeWithTag(BANNER_TAG).assertHeightIsAtLeast(56.dp).performClick()
        rule.onNodeWithText("APERÇU DE LA MANŒUVRE").assertIsDisplayed()
        rule.onNodeWithText("Fermer").assertIsDisplayed()
        assertEquals(GuidanceSheet.MANEUVER_PREVIEW, vm.guidanceSheet.value)
        rule.onNodeWithText("Fermer").performClick()
        rule.onNodeWithText("Fermer").assertDoesNotExist()
        assertEquals(GuidanceSheet.NONE, vm.guidanceSheet.value)
    }

    @Test fun the_preview_closes_when_the_next_maneuver_changes_but_the_trip_details_stay_open() {
        val vm = newVm()
        val r = TestRoutes.straight()
        val state = mutableStateOf(guidingUi(r, 300.0))
        rule.setContent {
            RoadlyTheme(darkTheme = true, drivingMode = true) {
                val s by state
                Box(Modifier.fillMaxSize()) { PortraitOverlay(s, true, {}, {}, {}, vm); GuidanceSheets(s, vm) }
            }
        }
        rule.onNodeWithTag(BANNER_TAG).performClick()
        rule.waitForIdle()
        assertEquals(GuidanceSheet.MANEUVER_PREVIEW, vm.guidanceSheet.value)
        // Virage franchi (prochaine manœuvre : l'arrivée) → l'aperçu, devenu faux, se referme.
        val after = guidingUi(r, RouteTracker(r).maneuverStartM[1] + 200.0)
        assertTrue("index visé change", after.progress!!.nextManeuverIndex != state.value.progress!!.nextManeuverIndex)
        rule.runOnUiThread { state.value = after }
        rule.waitForIdle()
        assertEquals(GuidanceSheet.NONE, vm.guidanceSheet.value)
        rule.onNodeWithText("APERÇU DE LA MANŒUVRE").assertDoesNotExist()
        // Le trajet complet, lui, reste ouvert au changement de manœuvre.
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).performClick()
        rule.waitForIdle()
        assertEquals(GuidanceSheet.TRIP_DETAILS, vm.guidanceSheet.value)
        rule.runOnUiThread { state.value = guidingUi(r, 300.0) }
        rule.waitForIdle()
        assertEquals(GuidanceSheet.TRIP_DETAILS, vm.guidanceSheet.value)
        rule.onNodeWithText("TRAJET COMPLET").assertIsDisplayed()
    }

    @Test fun roundabout_view_reads_the_exit_number_and_the_exit_street() {
        rule.setContent {
            RoadlyTheme(darkTheme = true, drivingMode = true) {
                RoundaboutManeuverView(previewRoundabout(270.0, 3, "Avenue de la Gare"), exitNumber = 3, compact = false)
            }
        }
        // Le schéma est une seule annonce (`clearAndSetSemantics`) : numéro et rue de sortie y sont lus, les textes internes ne sont pas des nœuds.
        rule.onNodeWithContentDescription("Rond-point, 3e sortie, vers Avenue de la Gare").assertIsDisplayed()
    }

    @Test fun the_banner_draws_the_roundabout_and_shows_toward_the_exit_street_collapsed_or_not() {
        val state = guidingUi(TestRoutes.twoRoundabouts(), 120.0)
        assertEquals("Rue Médiane", state.roundabout?.exitStreet)
        setOverlay(state, newVm())
        // Variante compacte : numéro seul dans l'annonce (la rue est lue par la ligne « vers … »).
        rule.onNodeWithContentDescription("Rond-point, 2e sortie").assertIsDisplayed()
        rule.onNodeWithText("vers Rue Médiane").assertIsDisplayed()
        // Le nom de l'anneau n'est plus affiché.
        rule.onNodeWithText("Rond-point Nord").assertDoesNotExist()
        rule.onNodeWithText("puis").assertIsDisplayed()
        rule.onNodeWithText("Rue Médiane").assertIsDisplayed()
    }

    @Test fun the_collapsed_banner_keeps_toward_the_exit_street() {
        setOverlay(guidingUi(TestRoutes.twoRoundabouts(), 120.0), newVm(), bannerExpanded = false)
        rule.onNodeWithContentDescription("Rond-point, 2e sortie").assertIsDisplayed()
        rule.onNodeWithText("vers Rue Médiane").assertIsDisplayed()
        // Replié : l'instruction sur 2 lignes est masquée.
        rule.onNodeWithText("Entrez dans le rond-point et prenez la 2e sortie.").assertDoesNotExist()
    }

    @Test fun lanes_are_absent_without_data() {
        val r = TestRoutes.straight()
        setOverlay(guidingUi(r, RouteTracker(r).maneuverStartM[1] - 300.0), newVm())
        rule.onNodeWithTag(BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Voies conseillées", substring = true).assertDoesNotExist()
    }

    @Test fun lanes_show_within_400_m_when_the_server_sends_them() {
        val r0 = TestRoutes.straight()
        val lanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.RIGHT), valid = true, active = true))
        val r = r0.copy(maneuvers = r0.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(lanes = lanes) else m })
        setOverlay(guidingUi(r, RouteTracker(r).maneuverStartM[1] - 300.0), newVm())
        rule.onNodeWithContentDescription("Voies conseillées : 2 sur 2").assertIsDisplayed()
    }

    @Test fun the_handle_expands_the_bottom_panel_and_the_camera_margin_follows() {
        val vm = newVm()
        setOverlay(guidingUi(TestRoutes.straight(), 300.0), vm)
        rule.waitForIdle()
        val collapsed = vm.bottomPanelFraction.value!!
        // Cibles de conduite (56 dp) : Arrêter, voix (colonne d'actions et panneau), ligne ETA.
        rule.onNodeWithContentDescription("Arrêter").assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        val voice = rule.onAllNodesWithContentDescription("Couper la voix").fetchSemanticsNodes()
        assertEquals("voix : colonne d'actions + panneau", 2, voice.size)
        for (i in voice.indices) rule.onAllNodesWithContentDescription("Couper la voix")[i].assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).assertHeightIsAtLeast(56.dp)
        rule.onNodeWithText("Trajet complet").assertDoesNotExist()

        rule.onNode(expandHandle).performClick()
        rule.waitForIdle()
        assertTrue(vm.bottomPanelExpanded.value)
        rule.onNode(hasText("Trajet complet").and(hasClickAction())).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        rule.onNode(hasText("Itinéraires").and(hasClickAction())).assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        assertEquals("la poignée ne change pas de feuille", GuidanceSheet.NONE, vm.guidanceSheet.value)
        val expanded = vm.bottomPanelFraction.value!!
        assertTrue("déplié ($expanded) > replié ($collapsed)", expanded > collapsed)

        rule.onNode(collapseHandle).performClick()
        rule.waitForIdle()
        assertFalse(vm.bottomPanelExpanded.value)
        rule.onNodeWithText("Trajet complet").assertDoesNotExist()
        assertEquals(collapsed, vm.bottomPanelFraction.value!!, 1e-4f)
    }

    @Test fun tapping_the_eta_row_opens_the_trip_details_sheet() {
        val vm = newVm()
        setOverlay(guidingUi(TestRoutes.straight(), 300.0), vm)
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).performClick()
        rule.waitForIdle()
        assertEquals(GuidanceSheet.TRIP_DETAILS, vm.guidanceSheet.value)
        assertFalse("la ligne ETA ne déplie pas le panneau", vm.bottomPanelExpanded.value)
        rule.onNodeWithText("TRAJET COMPLET").assertIsDisplayed()
        // Sans destination enregistrée : titre de repli ; l'arrivée (étape absente du bandeau et de l'Horizon) est listée.
        rule.onNodeWithText("Itinéraire").assertIsDisplayed()
        rule.onNodeWithText("Vous êtes arrivé.").assertIsDisplayed()
        rule.onNodeWithText("Fermer").performClick()
        rule.waitForIdle()
        assertEquals(GuidanceSheet.NONE, vm.guidanceSheet.value)
        rule.onNodeWithText("TRAJET COMPLET").assertDoesNotExist()
    }

    @Test fun back_with_the_panel_expanded_collapses_it_without_stopping_guidance() {
        val vm = newVm()
        val state = guidingUi(TestRoutes.straight(), 300.0)
        assertEquals(NavigationPhase.GUIDING, state.phase)
        setOverlay(state, vm)
        rule.onNode(expandHandle).performClick()
        rule.waitForIdle()
        assertTrue(vm.bottomPanelExpanded.value)

        pressBack()
        assertFalse("Retour replie le panneau", vm.bottomPanelExpanded.value)
        assertEquals("Retour n'atteint pas l'arrêt de l'écran tant que le panneau est déplié", 0, screenBackPresses)
        rule.onNodeWithText("Trajet complet").assertDoesNotExist()
        // Guidage toujours affiché : bandeau, Arrêter et poignée « déplier ».
        rule.onNodeWithTag(BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Arrêter").assertIsDisplayed()
        rule.onNode(expandHandle).assertIsDisplayed()

        // Panneau replié : Retour retrouve son rôle d'arrêt.
        pressBack()
        assertEquals(1, screenBackPresses)
    }

    @Test fun the_landscape_overlay_composes_with_banner_and_bottom_panel() {
        setOverlay(guidingUi(TestRoutes.twoRoundabouts(), 120.0), newVm(), landscape = true)
        rule.onNodeWithTag(BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Arrêter").assertIsDisplayed()
        rule.onNodeWithText("vers Rue Médiane").assertIsDisplayed()
        rule.onNodeWithText("Rue Médiane").assertIsDisplayed()
    }
}
