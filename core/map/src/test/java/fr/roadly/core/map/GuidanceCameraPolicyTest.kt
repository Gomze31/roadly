package fr.roadly.core.map

import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceCameraPolicyTest {
    private fun input(
        speed: Double? = 50.0, bearing: Float? = 90f, distance: Double = 2_000.0, type: ManeuverType = ManeuverType.TURN_RIGHT,
        threeD: Boolean = true, powerSave: Boolean = false, landscape: Boolean = false,
    ) = GuidanceCameraInput(speed, bearing, distance, type, threeD, powerSave, landscape)

    @Test fun `cruise zoom follows speed between the anchor points`() {
        assertEquals(17.5, GuidanceCameraPolicy.compute(input(speed = 10.0), null).zoom, 1e-9)
        assertEquals(17.5, GuidanceCameraPolicy.compute(input(speed = 30.0), null).zoom, 1e-9)
        assertEquals(17.0, GuidanceCameraPolicy.compute(input(speed = 50.0), null).zoom, 1e-9)
        assertEquals(16.65, GuidanceCameraPolicy.compute(input(speed = 70.0), null).zoom, 1e-9)
        assertEquals(16.3, GuidanceCameraPolicy.compute(input(speed = 90.0), null).zoom, 1e-9)
        assertEquals(15.8, GuidanceCameraPolicy.compute(input(speed = 130.0), null).zoom, 1e-9)
        assertEquals(17.0, GuidanceCameraPolicy.compute(input(speed = null), null).zoom, 1e-9)
    }

    @Test fun `3d cruise is pitched 58 and the car sits in the lower third in portrait`() {
        val cam = GuidanceCameraPolicy.compute(input(), null)
        assertEquals(58.0, cam.pitch, 1e-9)
        assertEquals(90.0, cam.bearing, 1e-9)
        assertEquals(CameraPadding(0f, 0.45f, 0f, 0.12f), cam.padding)
    }

    @Test fun `landscape pads the left side for the overlay column`() {
        assertEquals(CameraPadding(0.40f, 0.25f, 0f, 0f), GuidanceCameraPolicy.compute(input(landscape = true), null).padding)
    }

    @Test fun `2d keeps the legacy follow camera`() {
        val cam = GuidanceCameraPolicy.compute(input(threeD = false, speed = 120.0, distance = 50.0), null)
        assertEquals(17.0, cam.zoom, 1e-9)
        assertEquals(0.0, cam.pitch, 1e-9)
        assertEquals(CameraPadding(0f, 0f, 0f, 0f), cam.padding)
    }

    @Test fun `power save lowers the pitch to 45`() {
        assertEquals(45.0, GuidanceCameraPolicy.compute(input(powerSave = true), null).pitch, 1e-9)
    }

    @Test fun `motorway exits are approached from 400 m only at speed`() {
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT, 100.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT, 50.0))
        assertTrue(GuidanceCameraPolicy.isApproaching(140.0, ManeuverType.EXIT, 50.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(410.0, ManeuverType.EXIT, 100.0))
    }

    @Test fun `turns and roundabouts are approached from 150 m, departure and arrival never`() {
        assertTrue(GuidanceCameraPolicy.isApproaching(140.0, ManeuverType.ROUNDABOUT_ENTER, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(160.0, ManeuverType.TURN_LEFT, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(50.0, ManeuverType.ARRIVE, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(50.0, ManeuverType.DEPART, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(50.0, ManeuverType.CONTINUE, 40.0))
    }

    @Test fun `approach zooms to 18 with a flatter pitch and skips smoothing`() {
        val previous = GuidanceCameraPolicy.compute(input(speed = 90.0), null)
        val cam = GuidanceCameraPolicy.compute(input(speed = 90.0, distance = 100.0), previous)
        assertEquals(18.0, cam.zoom, 1e-9)
        assertEquals(50.0, cam.pitch, 1e-9)
        assertEquals(45.0, GuidanceCameraPolicy.compute(input(distance = 100.0, powerSave = true), null).pitch, 1e-9)
    }

    @Test fun `zoom moves at most half a level per update while cruising`() {
        val previous = GuidanceCameraPolicy.compute(input(speed = 10.0), null) // 17.5
        val cam = GuidanceCameraPolicy.compute(input(speed = 130.0), previous)   // cible 15.8
        assertEquals(17.0, cam.zoom, 1e-9)
    }

    @Test fun `bearing is kept from the previous camera when stopped or unknown`() {
        val previous = GuidanceCameraPolicy.compute(input(bearing = 135f), null)
        assertEquals(135.0, GuidanceCameraPolicy.compute(input(speed = 2.0, bearing = 10f), previous).bearing, 1e-9)
        assertEquals(135.0, GuidanceCameraPolicy.compute(input(bearing = null), previous).bearing, 1e-9)
        assertEquals(0.0, GuidanceCameraPolicy.compute(input(bearing = null), null).bearing, 1e-9)
    }

    @Test fun `an approach decided by the screen overrides the distance rule`() {
        assertEquals(GuidanceCameraPolicy.PITCH_APPROACH, GuidanceCameraPolicy.compute(input(distance = 160.0).copy(approaching = true), null).pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, GuidanceCameraPolicy.compute(input(distance = 100.0).copy(approaching = false), null).pitch, 1e-9)
    }
}
