package fr.roadly.feature.navigation

import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D1 / D3 : règles pures de la pile du bandeau, du panneau de direction et des feuilles de guidage. */
class BannerStackLogicTest {
    private val sign = ExitSignUi("31b", "Toulouse-Centre · Blagnac", "A62")
    private val approach = ApproachUi(ManeuverType.EXIT_RIGHT, "31b", "Toulouse-Centre · Blagnac", 12)

    @Test
    fun `panneau de direction absent du bandeau hors approche`() {
        val s = NavigationUiState(phase = NavigationPhase.GUIDING, sign = sign, approach = null)
        assertNull(bannerSign(s))
    }

    @Test
    fun `panneau de direction montre a l'approche`() {
        val s = NavigationUiState(phase = NavigationPhase.GUIDING, sign = sign, approach = approach)
        assertEquals(sign, bannerSign(s))
    }

    @Test
    fun `pas de panneau a l'approche d'une manoeuvre sans panneau`() {
        val s = NavigationUiState(phase = NavigationPhase.GUIDING, sign = null, approach = approach)
        assertNull(bannerSign(s))
    }

    @Test
    fun `directions du panneau - deux au plus, sans vide`() {
        assertEquals(listOf("Toulouse-Centre", "Blagnac"), signTowardLines("Toulouse-Centre · Blagnac · Colomiers"))
        assertEquals(listOf("Balma"), signTowardLines(" Balma · "))
        assertEquals(listOf("Paral·lel", "Sants"), signTowardLines("Paral·lel · Sants"))
        assertEquals(emptyList<String>(), signTowardLines(null))
        assertEquals(emptyList<String>(), signTowardLines(""))
    }

    @Test
    fun `feuille visible seulement pendant le guidage`() {
        assertEquals(GuidanceSheet.MANEUVER_PREVIEW, visibleSheet(NavigationPhase.GUIDING, GuidanceSheet.MANEUVER_PREVIEW))
        for (p in listOf(NavigationPhase.LOADING, NavigationPhase.ARRIVED, NavigationPhase.FAILED, NavigationPhase.IDLE)) {
            assertEquals("phase $p", GuidanceSheet.NONE, visibleSheet(p, GuidanceSheet.MANEUVER_PREVIEW))
        }
    }

    @Test
    fun `feuille refermee quand le guidage s'arrete, pas avant le premier etat`() {
        assertTrue(closesGuidanceSheet(NavigationPhase.ARRIVED))
        assertTrue(closesGuidanceSheet(NavigationPhase.FAILED))
        assertTrue(closesGuidanceSheet(NavigationPhase.IDLE))
        assertFalse(closesGuidanceSheet(NavigationPhase.GUIDING))
        assertFalse(closesGuidanceSheet(NavigationPhase.LOADING))
    }
}
