package fr.roadly.feature.car

import androidx.car.app.navigation.model.Maneuver as CarManeuver
import fr.roadly.core.common.AppError
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.NavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corrections de la revue technique Android Auto (docs/ANDROID-AUTO-REVUE.md) : une régression par défaut corrigé. */
class CarReviewFixesTest {
    private fun maneuver(type: ManeuverType, exit: Int? = null, street: String? = "Rue A") =
        Maneuver(type, "Tournez", street, 100.0, 10.0, 0, exit)

    private val geometry = listOf(GeoPoint(45.0, 4.0), GeoPoint(45.01, 4.0))

    private fun route(maneuvers: List<Maneuver>, id: String = "r1") = Route(id, RouteCosting.FASTEST, 1112.0, 120.0, geometry, maneuvers, false, "test")

    private fun guiding(
        maneuvers: List<Maneuver> = listOf(maneuver(ManeuverType.DEPART), maneuver(ManeuverType.TURN_LEFT), maneuver(ManeuverType.ARRIVE)),
        next: Int = 1,
        distanceToNextM: Double = 420.0,
        remainingM: Double = 1112.0,
        etaEpochMs: Long = 1_000_000L,
        lanes: List<Lane> = emptyList(),
        rerouting: Boolean = false,
        speed: Double? = 50.0,
    ): NavigationState.Guiding {
        val progress = RouteProgress(
            routeId = "r1", segmentIndex = 0, distanceAlongM = 0.0, offRouteM = 0.0, nextManeuverIndex = next,
            distanceToNextManeuverM = distanceToNextM, remainingDistanceM = remainingM, remainingDurationS = 120.0,
            snapped = geometry[0], bearingDeg = 0f,
        )
        return NavigationState.Guiding(
            route = route(maneuvers), progress = progress, speedKmh = speed, speedLimitKmh = 50,
            upcoming = maneuvers.drop(next + 1).take(3), lanes = lanes, etaEpochMs = etaEpochMs, gpsLost = false, rerouting = rerouting,
        )
    }

    // --- 1. Numéro de sortie posé seulement sur un type qui l'accepte ---

    @Test fun `the library refuses an exit number on a plain roundabout exit`() {
        // Règle de la bibliothèque (Car App 1.7.0), vérifiée ici pour que le test tombe si elle change.
        assertThrows(IllegalArgumentException::class.java) {
            CarManeuver.Builder(CarManeuver.TYPE_ROUNDABOUT_EXIT_CCW).setRoundaboutExitNumber(2)
        }
        assertFalse(CarGuidanceMapper.acceptsExitNumber(CarManeuver.TYPE_ROUNDABOUT_EXIT_CCW))
        assertFalse(CarGuidanceMapper.acceptsExitNumber(CarManeuver.TYPE_ROUNDABOUT_ENTER_CCW))
        assertTrue(CarGuidanceMapper.acceptsExitNumber(CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW))
    }

    @Test fun `a roundabout exit carrying an exit count still builds a manoeuvre`() {
        // Valhalla peut recopier roundabout_exit_count sur la manœuvre de sortie : l'étape garde le numéro, la manœuvre ne le pose pas.
        val step = CarGuidanceMapper.step(maneuver(ManeuverType.ROUNDABOUT_EXIT, exit = 2))
        assertEquals(2, step.roundaboutExit)
        val built = CarGuidanceMapper.maneuverBuilder(step).build()
        assertEquals(CarManeuver.TYPE_ROUNDABOUT_EXIT_CCW, built.type)
    }

    @Test fun `the roundabout entry keeps its exit number, and every manoeuvre type builds with or without one`() {
        val enter = CarGuidanceMapper.maneuverBuilder(CarGuidanceMapper.step(maneuver(ManeuverType.ROUNDABOUT_ENTER, exit = 3))).build()
        assertEquals(CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, enter.type)
        assertEquals(3, enter.roundaboutExitNumber)
        for (type in ManeuverType.entries) for (exit in listOf(null, 0, 1, 4)) {
            CarGuidanceMapper.maneuverBuilder(CarGuidanceMapper.step(maneuver(type, exit))).build()
        }
    }

    // --- 2. Voie sans direction : pas de bloc de voies ---

    private val left = Lane(setOf(LaneDirection.LEFT), valid = true, active = true)
    private val straight = Lane(setOf(LaneDirection.STRAIGHT), valid = false)
    private val unknown = Lane(emptySet(), valid = false)

    @Test fun `known lanes are sent as they are`() {
        assertEquals(listOf(left, straight), CarLanes.displayable(listOf(left, straight)))
        val car = CarLanes.toCar(listOf(left, straight))
        assertEquals(2, car.size)
        assertEquals(listOf(true), car[0].directions.map { it.isRecommended })
        assertEquals(listOf(false), car[1].directions.map { it.isRecommended })
    }

    @Test fun `one lane without direction removes the whole lanes block, no straight arrow is invented`() {
        assertTrue(CarLanes.displayable(listOf(left, unknown, straight)).isEmpty())
        assertTrue(CarLanes.toCar(listOf(left, unknown, straight)).isEmpty())
        // Le modèle de guidage (et donc la clé d'affichage et le gabarit) ne porte plus de voies.
        assertTrue(CarGuidanceMapper.map(guiding(lanes = listOf(left, unknown)))!!.lanes.isEmpty())
        assertEquals(listOf(left, straight), CarGuidanceMapper.map(guiding(lanes = listOf(left, straight)))!!.lanes)
    }

    // --- 4. Hors guidage, plus de vitesse ni de limitation périmées ---

    @Test fun `after arrival or a failure the hud carries nothing measured`() {
        val g = guiding(speed = 63.0)
        assertEquals(63, CarHudModel.forState(g, 5).speedKmh)
        assertEquals(CarHudModel.from(g, 5), CarHudModel.forState(g, 5))
        for (s in listOf(NavigationState.Arrived(g.route), NavigationState.Failed(AppError.Network), NavigationState.Idle)) {
            val hud = CarHudModel.forState(s, 5)
            assertEquals(CarHudModel.EMPTY, hud)
            assertNull(hud.speedKmh)
            assertNull(hud.limitKmh)
            assertNull(hud.zone)
        }
    }

    // --- 6. Résumé de trajet : renvoyé seulement quand il change ---

    private fun tripKey(g: NavigationState.Guiding, destination: String? = "Maison") =
        CarGuidanceMapper.tripKey(g.route.id, g.progress.nextManeuverIndex, CarGuidanceMapper.map(g), destination)

    @Test fun `positions that display the same trip share one key`() {
        // Une seconde plus tard : un mètre de moins, même minute d'arrivée, vitesse différente — rien ne change à l'écran.
        val a = guiding(distanceToNextM = 420.0, remainingM = 1112.0, etaEpochMs = 1_000_000L, speed = 50.0)
        val b = guiding(distanceToNextM = 419.0, remainingM = 1111.0, etaEpochMs = 1_000_900L, speed = 47.0)
        assertEquals(CarDistances.maneuver(420.0), CarDistances.maneuver(419.0))
        assertEquals(tripKey(a), tripKey(b))
    }

    @Test fun `the key changes with the step, the shown distances, the arrival minute and the destination`() {
        val base = guiding()
        val key = tripKey(base)
        assertNotEquals(key, tripKey(guiding(next = 2)))
        assertNotEquals(key, tripKey(guiding(distanceToNextM = 200.0)))
        assertNotEquals(key, tripKey(guiding(remainingM = 900.0)))
        assertNotEquals(key, tripKey(guiding(etaEpochMs = 1_000_000L + 60_000L)))
        assertNotEquals(key, tripKey(base, destination = "Travail"))
        assertNotEquals(key, CarGuidanceMapper.tripKey("r2", base.progress.nextManeuverIndex, CarGuidanceMapper.map(base), "Maison"))
    }

    @Test fun `rerouting is one loading trip, sent once however the position moves`() {
        val a = tripKey(guiding(rerouting = true, distanceToNextM = 420.0))
        val b = tripKey(guiding(rerouting = true, distanceToNextM = 150.0, etaEpochMs = 2_000_000L))
        assertEquals(a, b)
        assertNotEquals(a, tripKey(guiding()))
        // Tracé sans manœuvre : même résumé « chargement ».
        assertEquals(a, CarGuidanceMapper.tripKey("r1", 0, null, "Maison"))
    }
}
