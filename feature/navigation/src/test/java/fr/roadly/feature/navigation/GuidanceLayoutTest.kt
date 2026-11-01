package fr.roadly.feature.navigation

import fr.roadly.feature.navigation.ActionButton.ALTERNATIVES
import fr.roadly.feature.navigation.ActionButton.CAMERA
import fr.roadly.feature.navigation.ActionButton.REPORT
import fr.roadly.feature.navigation.ActionButton.SEARCH
import fr.roadly.feature.navigation.ActionButton.VOICE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Passe appareil de l'étape D : colonne d'actions portrait au-dessus du bloc du bas, panneau déplié en paysage. */
class GuidanceLayoutTest {
    @Test fun `the column height counts 56 dp buttons and 12 dp gaps`() {
        assertEquals(328, actionColumnHeightDp(5))
        assertEquals(56, actionColumnHeightDp(1))
        assertEquals(0, actionColumnHeightDp(0))
    }

    @Test fun `the space left to the portrait column lies between the attribution and the bottom block`() {
        // HONOR : 802 dp, barre d'état 40 dp, bloc du bas replié 240 dp → 802 − (40 + 96) − (240 + 8) = 418 dp.
        assertEquals(418, portraitActionColumnAvailableDp(802, 40, 240))
        // Déplié : 383 dp → 275 dp.
        assertEquals(275, portraitActionColumnAvailableDp(802, 40, 383))
        assertEquals("jamais négatif", 0, portraitActionColumnAvailableDp(300, 40, 383))
    }

    @Test fun `everything shows when there is room`() {
        assertEquals(listOf(REPORT, VOICE, SEARCH, ALTERNATIVES, CAMERA), portraitActionButtons(418, guiding = true, panelExpanded = false, voiceInPanel = true))
        assertEquals("hors guidage : pas d'Itinéraires", listOf(REPORT, VOICE, SEARCH, CAMERA), portraitActionButtons(418, guiding = false, panelExpanded = false, voiceInPanel = false))
    }

    @Test fun `duplicates of the panel go first - Itineraires when expanded, then the voice when the panel shows it`() {
        // Déplié (275 dp) : « Itinéraires » est dans le panneau.
        assertEquals(listOf(REPORT, VOICE, SEARCH, CAMERA), portraitActionButtons(275, guiding = true, panelExpanded = true, voiceInPanel = true))
        // Moins de place : la voix est aussi dans la ligne repliée du panneau.
        assertEquals(listOf(REPORT, SEARCH, CAMERA), portraitActionButtons(200, guiding = true, panelExpanded = true, voiceInPanel = true))
        // Replié, « Itinéraires » n'a pas de doublon : la voix part d'abord.
        assertEquals(listOf(REPORT, SEARCH, ALTERNATIVES, CAMERA), portraitActionButtons(300, guiding = true, panelExpanded = false, voiceInPanel = true))
    }

    @Test fun `without a duplicate, search then alternatives then voice give way, report and camera always stay`() {
        // Carte déplacée (Recentrer remplace la voix du panneau) et panneau déplié + compte à rebours : 225 dp.
        assertEquals(listOf(REPORT, VOICE, CAMERA), portraitActionButtons(225, guiding = true, panelExpanded = true, voiceInPanel = false))
        assertEquals(listOf(REPORT, VOICE, CAMERA), portraitActionButtons(200, guiding = true, panelExpanded = false, voiceInPanel = false))
        assertEquals(listOf(REPORT, CAMERA), portraitActionButtons(50, guiding = true, panelExpanded = false, voiceInPanel = false))
    }

    @Test fun `the landscape panel fits when stack, gap and expanded panel hold in the column`() {
        // Colonne gauche du HONOR en paysage : 294 dp ; panneau déplié compact : 154 dp.
        assertTrue(landscapePanelFits(294, 132, 154))
        assertFalse(landscapePanelFits(294, 133, 154))
        assertFalse("bandeau déplié + « puis »", landscapePanelFits(294, 162, 154))
        assertFalse("panneau de direction", landscapePanelFits(294, 225, 154))
    }
}
