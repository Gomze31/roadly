package fr.roadly.feature.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.navigation.RoundaboutResolver
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

    /** Manœuvres 10 (entrée) et 11 (sortie) de `ParisRoute` (fixture Valhalla FOSSGIS, Hôtel de Ville → Étoile), rues telles que Valhalla les donne. */
    private val parisRing = Route(
        id = "paris", costing = RouteCosting.FASTEST, distanceMeters = 300.0, durationSeconds = 60.0,
        geometry = listOf(GeoPoint(48.8738, 2.3100), GeoPoint(48.8740, 2.3105), GeoPoint(48.8742, 2.3110)),
        maneuvers = listOf(
            Maneuver(
                ManeuverType.ROUNDABOUT_ENTER,
                "Entrez dans Rond-point des Champs-Élysées-Marcel-Dassault et prenez la 2e sortie dans Avenue des Champs-Élysées.",
                "Rond-point des Champs-Élysées-Marcel-Dassault", 60.0, 10.0, beginShapeIndex = 0, roundaboutExit = 2,
            ),
            Maneuver(ManeuverType.ROUNDABOUT_EXIT, "Quittez le rond-point dans Avenue des Champs-Élysées.", "Avenue des Champs-Élysées", 240.0, 50.0, beginShapeIndex = 1, roundaboutExit = null),
        ),
        hasToll = false, providerId = "valhalla",
    )

    @Test
    fun `rond-point - le bandeau donne la rue de sortie, pas le nom de l'anneau`() {
        val enter = parisRing.maneuvers[0]
        val s = NavigationUiState(
            phase = NavigationPhase.GUIDING,
            banner = BannerUi(enter.type, "150 m", enter.instruction, enter.streetName, null, enter.roundaboutExit),
            roundabout = RoundaboutResolver.resolve(parisRing, 0),
        )
        assertEquals("Avenue des Champs-Élysées", bannerStreet(s))
    }

    @Test
    fun `hors rond-point - la rue du bandeau est inchangee`() {
        val s = NavigationUiState(phase = NavigationPhase.GUIDING, banner = BannerUi(ManeuverType.TURN_RIGHT, "80 m", "Tournez à droite.", "Rue Louis Plana", null, null))
        assertEquals("Rue Louis Plana", bannerStreet(s))
        assertNull(bannerStreet(NavigationUiState()))
    }

    @Test
    fun `surtitre du panneau selon la manoeuvre`() {
        assertEquals(R.string.nav_sign_next, signOverline(ManeuverType.EXIT_RIGHT, isNext = true))
        assertEquals(R.string.nav_sign_following, signOverline(ManeuverType.EXIT_LEFT, isNext = false))
        for (t in listOf(ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT)) {
            assertEquals("$t", R.string.nav_sign_next_direction, signOverline(t, isNext = true))
            assertEquals("$t", R.string.nav_sign_following_direction, signOverline(t, isNext = false))
        }
        for (t in listOf(ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT)) {
            assertEquals("$t", R.string.nav_sign_next_ramp, signOverline(t, isNext = true))
            assertEquals("$t", R.string.nav_sign_following_ramp, signOverline(t, isNext = false))
        }
    }

    @Test
    fun `apercu referme au changement de manoeuvre visee, pas le trajet complet`() {
        assertTrue(closesPreviewOnIndexChange(3, 4, GuidanceSheet.MANEUVER_PREVIEW))
        assertFalse("même manœuvre", closesPreviewOnIndexChange(3, 3, GuidanceSheet.MANEUVER_PREVIEW))
        assertFalse("trajet complet gardé", closesPreviewOnIndexChange(3, 4, GuidanceSheet.TRIP_DETAILS))
        assertFalse("rien d'ouvert", closesPreviewOnIndexChange(3, 4, GuidanceSheet.NONE))
        assertFalse("premier état", closesPreviewOnIndexChange(null, 0, GuidanceSheet.MANEUVER_PREVIEW))
        assertFalse("fin du guidage", closesPreviewOnIndexChange(4, null, GuidanceSheet.MANEUVER_PREVIEW))
    }

    @Test
    fun `paysage - Horizon masque pendant le panneau de direction ou les voies`() {
        val items = NavigationUiState(phase = NavigationPhase.GUIDING, upcoming = listOf(UpcomingUi(ManeuverType.TURN_LEFT, "800 m", "Rue X", 800.0)))
        assertTrue(horizonVisible(items, landscape = false))
        assertTrue(horizonVisible(items, landscape = true))
        val signed = items.copy(sign = sign, approach = approach)
        // Passe appareil : masqué aussi en portrait pendant le panneau de direction (bloc du bas plus court, flèche visible).
        assertFalse("portrait : masqué pendant le panneau", horizonVisible(signed, landscape = false))
        assertFalse(horizonVisible(signed, landscape = true))
        assertTrue("portrait : voies seules, gardé", horizonVisible(items.copy(showLanes = true), landscape = false))
        assertFalse(horizonVisible(items.copy(showLanes = true), landscape = true))
        assertFalse("rien à montrer", horizonVisible(NavigationUiState(phase = NavigationPhase.GUIDING), landscape = false))
        // Passe appareil : en paysage, l'Horizon laisse sa place au panneau déplié ; en portrait, il reste.
        assertFalse("paysage, panneau déplié", horizonVisible(items, landscape = true, panelExpanded = true))
        assertTrue("portrait, panneau déplié", horizonVisible(items, landscape = false, panelExpanded = true))
    }

    @Test
    fun `voies - cellule de 44 dp au plus, partagee quand elles sont nombreuses, 24 dp au moins`() {
        assertEquals(44f, laneCellDp(400f, 3), 0f)
        // 8 voies sur 300 dp : 7 séparateurs de 9 dp (1 dp de trait + 2 × 4 dp d'écart) → (300 − 63) / 8.
        assertEquals((300f - 7 * LANE_GAP_DP) / 8, laneCellDp(300f, 8), 1e-3f)
        assertEquals(24f, laneCellDp(200f, 12), 0f)
        assertEquals(36f, laneCellDp(400f, 3, maxDp = 36f), 0f)
        assertEquals(44f, laneCellDp(400f, 0), 0f)
    }
}
