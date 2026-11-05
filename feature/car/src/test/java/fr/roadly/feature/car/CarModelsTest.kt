package fr.roadly.feature.car

import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver as CarManeuver
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.FreeDriveState
import fr.roadly.core.navigation.FreeDriveZone
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.radar.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarModelsTest {
    private fun maneuver(type: ManeuverType, street: String? = "Rue A", exit: Int? = null) =
        Maneuver(type, "Tournez", street, 100.0, 10.0, 0, exit)

    private fun guiding(
        maneuvers: List<Maneuver>,
        next: Int,
        speed: Double? = 50.0,
        limit: Int? = 50,
        active: ZoneStatus? = null,
        ahead: ZoneStatus? = null,
        gpsLost: Boolean = false,
    ): NavigationState.Guiding {
        val geometry = listOf(GeoPoint(45.0, 4.0), GeoPoint(45.01, 4.0))
        val route = Route("r1", RouteCosting.FASTEST, 1112.0, 120.0, geometry, maneuvers, false, "test")
        val progress = RouteProgress(
            routeId = "r1", segmentIndex = 0, distanceAlongM = 0.0, offRouteM = 0.0, nextManeuverIndex = next,
            distanceToNextManeuverM = 420.0, remainingDistanceM = 1112.0, remainingDurationS = 120.0,
            snapped = geometry[0], bearingDeg = 0f,
        )
        return NavigationState.Guiding(
            route = route, progress = progress, speedKmh = speed, speedLimitKmh = limit,
            upcoming = maneuvers.drop(next + 1).take(3), lanes = emptyList(), etaEpochMs = 1_000_000L,
            gpsLost = gpsLost, rerouting = false, radarActive = active, radarNext = ahead,
        )
    }

    @Test fun `maneuver codes follow right-hand traffic`() {
        assertEquals(CarManeuver.TYPE_TURN_NORMAL_LEFT, CarGuidanceMapper.maneuverCode(ManeuverType.TURN_LEFT, null))
        assertEquals(CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, CarGuidanceMapper.maneuverCode(ManeuverType.ROUNDABOUT_ENTER, 2))
        // Sans numéro de sortie, la bibliothèque refuserait ENTER_AND_EXIT : simple entrée.
        assertEquals(CarManeuver.TYPE_ROUNDABOUT_ENTER_CCW, CarGuidanceMapper.maneuverCode(ManeuverType.ROUNDABOUT_ENTER, null))
        assertEquals(CarManeuver.TYPE_OFF_RAMP_NORMAL_RIGHT, CarGuidanceMapper.maneuverCode(ManeuverType.EXIT_RIGHT, null))
        assertEquals(CarManeuver.TYPE_DESTINATION, CarGuidanceMapper.maneuverCode(ManeuverType.ARRIVE, null))
        ManeuverType.entries.forEach { CarGuidanceMapper.maneuverCode(it, 1) }
    }

    @Test fun `roundabout exit zero is dropped`() {
        val s = CarGuidanceMapper.step(maneuver(ManeuverType.ROUNDABOUT_ENTER, exit = 0))
        assertNull(s.roundaboutExit)
        assertEquals(CarManeuver.TYPE_ROUNDABOUT_ENTER_CCW, s.maneuverCode)
    }

    @Test fun `guidance model uses next maneuver and upcoming`() {
        val ms = listOf(maneuver(ManeuverType.DEPART), maneuver(ManeuverType.TURN_RIGHT, street = "Avenue B"), maneuver(ManeuverType.ARRIVE, street = " "))
        val m = CarGuidanceMapper.map(guiding(ms, next = 1))!!
        assertEquals(ManeuverType.TURN_RIGHT, m.current.type)
        assertEquals("Avenue B", m.current.road)
        assertEquals(ManeuverType.ARRIVE, m.next?.type)
        assertNull(m.next?.road)
        assertEquals(420.0, m.distanceToCurrentM, 0.0)
        // Index au-delà de la dernière manœuvre (fin de parcours) : dernière manœuvre.
        assertEquals(ManeuverType.ARRIVE, CarGuidanceMapper.map(guiding(ms, next = 7))!!.current.type)
        assertNull(CarGuidanceMapper.map(guiding(emptyList(), next = 0)))
    }

    @Test fun `distances use the shared rounding`() {
        assertEquals(CarDistanceValue(450.0, Distance.UNIT_METERS), CarDistances.maneuver(437.0))
        assertEquals(CarDistanceValue(1.2, Distance.UNIT_KILOMETERS_P1), CarDistances.maneuver(1234.0))
        assertEquals(CarDistanceValue(830.0, Distance.UNIT_METERS), CarDistances.remaining(828.0))
        assertEquals(CarDistanceValue(4.3, Distance.UNIT_KILOMETERS_P1), CarDistances.remaining(4321.0))
        assertEquals(CarDistanceValue(43.0, Distance.UNIT_KILOMETERS), CarDistances.remaining(43_210.0))
    }

    @Test fun `hud shows active zone before next one and overspeed with tolerance`() {
        val ms = listOf(maneuver(ManeuverType.DEPART), maneuver(ManeuverType.ARRIVE))
        val inside = ZoneStatus("z1", 800.0, 90, 75, inside = true)
        val ahead = ZoneStatus("z2", 2500.0, 110, 50, inside = false)
        val h = CarHudModel.from(guiding(ms, 1, speed = 56.4, limit = 50, active = inside, ahead = ahead), toleranceKmh = 5)
        assertEquals(56, h.speedKmh)
        assertTrue(h.overLimit)
        assertEquals(CarZoneModel(true, 800.0, 90, 75), h.zone)
        assertTrue(h.zone!!.reliable)
        val calm = CarHudModel.from(guiding(ms, 1, speed = 54.0, limit = 50, ahead = ahead), toleranceKmh = 5)
        assertFalse(calm.overLimit)
        assertFalse(calm.zone!!.inside)
        assertFalse(calm.zone!!.reliable)
    }

    @Test fun `hud hides speed when gps is lost`() {
        val ms = listOf(maneuver(ManeuverType.DEPART), maneuver(ManeuverType.ARRIVE))
        val h = CarHudModel.from(guiding(ms, 1, speed = 90.0, limit = 50, gpsLost = true), 5)
        assertNull(h.speedKmh)
        assertFalse(h.overLimit)
        assertEquals(50, h.limitKmh)
    }

    @Test fun `free drive hud prefers the zone being crossed`() {
        val s = FreeDriveState.Active(
            startedAtMs = 0, speedKmh = 80.0, speedLimitKmh = 80,
            zone = FreeDriveZone("a", 70, 300.0, 80), nextZone = ZoneStatus("b", 1500.0, 90, 80, false),
        )
        assertEquals(CarZoneModel(true, 300.0, 70, 80), CarHudModel.from(s, 5).zone)
        assertEquals(CarZoneModel(false, 1500.0, 90, 80), CarHudModel.from(s.copy(zone = null), 5).zone)
        assertNull(CarHudModel.from(s.copy(zone = null, nextZone = null), 5).zone)
    }

    @Test fun `navigate intents`() {
        assertEquals(CarNavigateTarget.Coordinates(GeoPoint(48.8584, 2.2945), null), CarNavigateIntent.parse("geo:48.8584,2.2945"))
        assertEquals(CarNavigateTarget.Query("10 rue de Rivoli, Paris"), CarNavigateIntent.parse("geo:0,0?q=10%20rue%20de%20Rivoli%2C%20Paris"))
        assertEquals(CarNavigateTarget.Query("gare de Lyon"), CarNavigateIntent.parse("geo:0,0?q=gare+de+Lyon"))
        assertEquals(CarNavigateTarget.Coordinates(GeoPoint(45.76, 4.84), "Bellecour"), CarNavigateIntent.parse("geo:0,0?q=45.76,4.84(Bellecour)"))
        assertEquals(CarNavigateTarget.Coordinates(GeoPoint(45.76, 4.84), null), CarNavigateIntent.parse("geo:45.76,4.84;u=35?z=12"))
        assertNull(CarNavigateIntent.parse("geo:0,0"))
        assertNull(CarNavigateIntent.parse("geo:95,4"))
        assertNull(CarNavigateIntent.parse("https://example.org"))
        assertNull(CarNavigateIntent.parse(null))
    }
}
