package fr.roadly.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import fr.roadly.core.designsystem.theme.CockpitTint
import fr.roadly.core.designsystem.theme.RoadlyColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** D3 : texte posé sur un aplat `brand` (panneau de direction) lisible quelle que soit la teinte du cockpit. */
class OnBrandAutoTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test fun `blanc sur le violet de nuit`() {
        val night = RoadlyColors.Night.withTint(CockpitTint.VIOLET)
        assertEquals(night.onBrand, night.onBrandAuto)
    }

    @Test fun `texte sombre sur l'ambre de nuit`() {
        val night = RoadlyColors.Night.withTint(CockpitTint.AMBER)
        assertNotEquals(night.onBrand, night.onBrandAuto)
        assertTrue("texte sombre attendu", night.onBrandAuto.luminance() < 0.05f)
    }

    @Test fun `contraste d'au moins 3 pour 1 pour chaque teinte, jour et nuit`() {
        for (scheme in listOf(RoadlyColors.Night, RoadlyColors.Day)) {
            for (tint in CockpitTint.entries) {
                val s = scheme.withTint(tint)
                val ratio = contrast(s.onBrandAuto, s.brand)
                assertTrue("$tint ${if (s.isDark) "nuit" else "jour"} : contraste $ratio", ratio >= 3f)
            }
        }
    }
}
