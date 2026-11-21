package fr.roadly.feature.car

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corrections issues du premier essai en voiture réelle : caméra, textes de guidage, images de carrefour, recherche vocale. */
class CarDrivePassTest {
    private fun maneuver(
        type: ManeuverType, street: String? = null, exit: Int? = null, exitNumber: String? = null, toward: String? = null, branch: String? = null,
        instruction: String = "Instruction complète de Valhalla, trop longue pour la carte.",
    ) = Maneuver(type, instruction, street, 100.0, 10.0, 0, exit, exitNumber = exitNumber, exitToward = toward, exitBranch = branch)

    // --- Caméra ---

    @Test
    fun `en croisière le zoom recule avec la vitesse`() {
        val city = CarCameraPolicy.cruiseZoom(30.0)
        val road = CarCameraPolicy.cruiseZoom(80.0)
        val motorway = CarCameraPolicy.cruiseZoom(130.0)
        assertTrue(city > road && road > motorway)
        assertEquals(CarCameraPolicy.cruiseZoom(130.0), CarCameraPolicy.cruiseZoom(180.0), 0.0)
    }

    @Test
    fun `à l'approche d'un rond-point la caméra se resserre et s'aplatit, progressivement`() {
        val far = CarCameraPolicy.guidance(50.0, 800.0, ManeuverType.ROUNDABOUT_ENTER, null)
        val mid = CarCameraPolicy.guidance(50.0, 150.0, ManeuverType.ROUNDABOUT_ENTER, null)
        val at = CarCameraPolicy.guidance(50.0, 0.0, ManeuverType.ROUNDABOUT_ENTER, null)
        assertEquals(CarCameraPolicy.TILT_CRUISE, far.tilt, 0.0)
        assertTrue(mid.zoom > far.zoom && at.zoom > mid.zoom)
        assertTrue(mid.tilt < far.tilt && at.tilt < mid.tilt)
        assertEquals(CarApproach.ROUNDABOUT.zoom, at.zoom, 1e-9)
        assertEquals(CarApproach.ROUNDABOUT.tilt, at.tilt, 1e-9)
    }

    @Test
    fun `une sortie sur voie rapide se prépare de loin, pas un carrefour de ville`() {
        assertEquals(CarApproach.EXIT, CarCameraPolicy.approachOf(ManeuverType.EXIT_RIGHT, 110.0))
        assertEquals(CarApproach.TURN, CarCameraPolicy.approachOf(ManeuverType.EXIT_RIGHT, 40.0))
        assertNull(CarCameraPolicy.approachOf(ManeuverType.CONTINUE, 110.0))
        val cruise = CarCameraPolicy.guidance(110.0, 2_000.0, ManeuverType.EXIT_RIGHT, null)
        val approaching = CarCameraPolicy.guidance(110.0, 500.0, ManeuverType.EXIT_RIGHT, null)
        assertTrue(approaching.zoom > cruise.zoom)
    }

    @Test
    fun `après la manœuvre le retour en croisière se fait par paliers`() {
        val at = CarCameraPolicy.guidance(50.0, 0.0, ManeuverType.ROUNDABOUT_ENTER, null)
        val after = CarCameraPolicy.guidance(50.0, 2_000.0, ManeuverType.TURN_LEFT, at)
        assertEquals(CarCameraPolicy.MAX_ZOOM_STEP, at.zoom - after.zoom, 1e-9)
        assertEquals(CarCameraPolicy.MAX_TILT_STEP, after.tilt - at.tilt, 1e-9)
        var c = after
        repeat(20) { c = CarCameraPolicy.guidance(50.0, 2_000.0, ManeuverType.TURN_LEFT, c) }
        assertEquals(CarCameraPolicy.cruiseZoom(50.0), c.zoom, 1e-9)
        assertEquals(CarCameraPolicy.TILT_CRUISE, c.tilt, 1e-9)
    }

    @Test
    fun `l'approche ne dézoome jamais sous la croisière`() {
        // À 20 km/h la croisière (≈ 16,9) est plus serrée que le cadrage d'une sortie (16,6).
        val c = CarCameraPolicy.guidance(80.0, 0.0, ManeuverType.EXIT_RIGHT, null)
        assertTrue(c.zoom >= CarCameraPolicy.cruiseZoom(80.0))
        val slow = CarCameraPolicy.guidance(20.0, 0.0, ManeuverType.KEEP_LEFT, null)
        assertTrue(slow.zoom >= CarCameraPolicy.cruiseZoom(20.0))
    }

    // --- Textes courts ---

    @Test
    fun `sortie d'autoroute, numéro et direction`() {
        assertEquals("Sortie 16 · Les Argoulets", CarCues.cue(maneuver(ManeuverType.EXIT_RIGHT, exitNumber = "16", toward = "Les Argoulets")))
        assertEquals("Sortie · A62", CarCues.cue(maneuver(ManeuverType.EXIT_RIGHT, branch = "A62")))
        assertEquals("Sortie", CarCues.cue(maneuver(ManeuverType.EXIT_LEFT)))
    }

    @Test
    fun `rond-point, rang de sortie et rue de sortie`() {
        val exit = maneuver(ManeuverType.ROUNDABOUT_EXIT, street = "Route de Castres")
        assertEquals("2e sortie · Route de Castres", CarCues.cue(maneuver(ManeuverType.ROUNDABOUT_ENTER, exit = 2), exit))
        assertEquals("1re sortie", CarCues.cue(maneuver(ManeuverType.ROUNDABOUT_ENTER, exit = 1)))
        assertEquals("Rond-point", CarCues.cue(maneuver(ManeuverType.ROUNDABOUT_ENTER, exit = 0)))
    }

    @Test
    fun `virage, la rue seule, sinon le geste`() {
        assertEquals("Rue de Metz", CarCues.cue(maneuver(ManeuverType.TURN_LEFT, street = "Rue de Metz")))
        assertEquals("Tournez à gauche", CarCues.cue(maneuver(ManeuverType.TURN_LEFT)))
        assertEquals("A61 · Carcassonne", CarCues.cue(maneuver(ManeuverType.KEEP_RIGHT, branch = "A61", toward = "Carcassonne")))
    }

    @Test
    fun `aucun texte de guidage ne dépasse ce que l'hôte affiche sans couper`() {
        for (type in ManeuverType.entries) assertTrue(type.name, CarCues.verb(type).length <= 24)
    }

    // --- Images de carrefour ---

    @Test
    fun `rond-point montré à l'approche seulement, avec l'angle des données`() {
        val enter = maneuver(ManeuverType.ROUNDABOUT_ENTER, exit = 2)
        assertNull(CarGuidanceMapper.junction(enter, 900.0, 50.0, 0.0, 2))
        assertEquals(CarJunctionVisual.Roundabout(-90.0, 3), CarGuidanceMapper.junction(enter, 200.0, 50.0, -90.0, 3))
        // Angle inconnu : rien n'est inventé.
        assertNull(CarGuidanceMapper.junction(enter, 200.0, 50.0, null, 2))
    }

    @Test
    fun `panneau de sortie, de loin sur voie rapide, jamais sans donnée de panneau`() {
        val exit = maneuver(ManeuverType.EXIT_RIGHT, exitNumber = "16", toward = "Les Argoulets")
        assertEquals(CarJunctionVisual.ExitSign("16", "Les Argoulets", null, left = false), CarGuidanceMapper.junction(exit, 1_800.0, 90.0, null, null))
        assertNull(CarGuidanceMapper.junction(exit, 1_800.0, 40.0, null, null))
        assertNull(CarGuidanceMapper.junction(maneuver(ManeuverType.EXIT_RIGHT), 100.0, 90.0, null, null))
        assertTrue((CarGuidanceMapper.junction(maneuver(ManeuverType.KEEP_LEFT, branch = "A62"), 100.0, 90.0, null, null) as CarJunctionVisual.ExitSign).left)
    }

    // --- Recherche (dictée) ---

    private fun place(id: String) = Place(id, id, null, GeoPoint(43.6, 1.44), PlaceKind.ADDRESS, "test")

    @Test
    fun `pendant la dictée, texte initial et clavier ne changent jamais`() {
        val m = CarSearchModel(initialQuery = null)
        m.onText("12 rue de")
        m.onSearchStarted()
        m.onResults("12 rue de", listOf(place("a")))
        m.onText("12 rue de la république toulouse")
        assertEquals("", m.initialText)
        assertTrue(m.showKeyboard)
    }

    @Test
    fun `un texte partiel de plus ne redessine rien tant que la liste est la même`() {
        val m = CarSearchModel(initialQuery = null)
        assertFalse(m.onText("1"))
        assertTrue(m.onText("12 rue"))
        m.onSearchStarted()
        assertTrue(m.onResults("12 rue", listOf(place("a"))))
        // La dictée continue : mêmes résultats à l'écran, l'hôte n'est pas sollicité (c'est ce qui vidait le champ).
        assertFalse(m.onText("12 rue de la"))
        assertFalse(m.onSearchStarted())
        assertFalse(m.loading)
        assertFalse(m.onText("12 rue de la république"))
        assertTrue(m.onResults("12 rue de la république", listOf(place("b"))))
    }

    @Test
    fun `chargement plein écran seulement sans rien à montrer, récents sous 3 caractères`() {
        val m = CarSearchModel(initialQuery = null)
        m.onRecents(listOf(place("r")))
        assertEquals(listOf(place("r")), m.shown)
        m.onText("tou")
        assertTrue(m.onSearchStarted())
        assertTrue(m.loading)
        m.onResults("tou", emptyList())
        assertTrue(m.noResult)
        m.onText("to")
        assertEquals(listOf(place("r")), m.shown)
        assertFalse(m.noResult)
    }

    @Test
    fun `styles de la voiture, variantes contrastées des styles embarqués seulement`() {
        assertEquals(CarStyles.CAR_LIGHT, CarStyles.forCar("asset://styles/roadly-light.json"))
        assertEquals(CarStyles.CAR_DARK, CarStyles.forCar("asset://styles/roadly-dark.json"))
        assertEquals("https://exemple.fr/style.json", CarStyles.forCar("https://exemple.fr/style.json"))
    }
}
