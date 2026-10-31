package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class NavigationCameraControllerTest {
    private fun input(
        bearing: Float? = 90f, speed: Double? = 50.0, nowMs: Long = 0L, mode: CameraMode3D = CameraMode3D.FOLLOW_3D,
        approaching: Boolean? = false, powerSave: Boolean = false, landscape: Boolean = false, bottomPanel: Float? = null,
    ) = NavigationCameraInput(
        point = GeoPoint(48.85, 2.35), bearingDeg = bearing, speedKmh = speed, distanceToManeuverM = 2_000.0,
        maneuverType = ManeuverType.TURN_RIGHT, approaching = approaching, mode = mode, powerSave = powerSave,
        landscape = landscape, nowMs = nowMs, bottomPanelFraction = bottomPanel,
    )

    private fun assertPadding(expected: CameraPadding, actual: CameraPadding) {
        assertEquals(expected.left, actual.left, 1e-6f); assertEquals(expected.top, actual.top, 1e-6f)
        assertEquals(expected.right, actual.right, 1e-6f); assertEquals(expected.bottom, actual.bottom, 1e-6f)
    }

    @Test fun `crossing north turns the short way, without a full spin`() { // C1 : 359° → 1°
        assertEquals(20.0, Bearings.delta(350.0, 10.0), 1e-9)
        val c = NavigationCameraController()
        c.update(input(bearing = 359f, nowMs = 0))
        for (i in 1..10) {
            val f = c.update(input(bearing = 1f, nowMs = i * 1_000L))
            val b = c.bearing!!
            assertTrue("cap $b hors de [359 ; 1]", b >= 359.0 - 1e-9 || b <= 1.0 + 1e-9)
            assertEquals(0.0, Bearings.delta(b, f.camera.bearing), 1e-3)
        }
        assertEquals(0.0, Bearings.delta(c.bearing!!, 1.0), 0.01)
    }

    @Test fun `the heading is an exponential average with a 1,5 s time constant`() {
        val c = NavigationCameraController()
        c.update(input(bearing = 0f, nowMs = 0))
        c.update(input(bearing = 90f, nowMs = 1_500))
        assertEquals(90.0 * (1 - exp(-1.0)), c.bearing!!, 1e-6)
    }

    @Test fun `stopped, the heading does not drift`() { // C1 : arrêt sans dérive
        val c = NavigationCameraController()
        for (i in 0..2) c.update(input(bearing = 90f, nowMs = i * 1_000L))
        val settled = c.bearing!!
        listOf(200f, 10f, 300f, 45f).forEachIndexed { i, noisy ->
            val f = c.update(input(bearing = noisy, speed = 2.0, nowMs = 3_000L + i * 1_000L))
            assertEquals(settled, c.bearing!!, 0.0)
            assertEquals(settled, f.camera.bearing, 1e-3)
        }
    }

    @Test fun `a long GPS gap is capped so the heading catches up without overshoot`() {
        val c = NavigationCameraController()
        c.update(input(bearing = 0f, nowMs = 0))
        c.update(input(bearing = 90f, nowMs = 60_000))
        val alpha = 1 - exp(-NavigationCameraController.MAX_DT_S / NavigationCameraController.BEARING_TAU_S)
        assertEquals(90.0 * alpha, c.bearing!!, 1e-6)
    }

    @Test fun `an unknown heading keeps the filtered one`() {
        val c = NavigationCameraController()
        c.update(input(bearing = 45f, nowMs = 0))
        val f = c.update(input(bearing = null, nowMs = 1_000))
        assertEquals(45.0, c.bearing!!, 1e-9)
        assertEquals(45.0, f.camera.bearing, 1e-3)
    }

    @Test fun `2d stays flat, north up points north, back to 3d restores the heading`() {
        val c = NavigationCameraController()
        val flat = c.update(input(mode = CameraMode3D.FOLLOW_2D))
        assertEquals(0.0, flat.camera.pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.ZOOM_2D, flat.camera.zoom, 1e-9)
        assertEquals(90.0, flat.camera.bearing, 1e-3)
        val north = c.update(input(mode = CameraMode3D.NORTH_UP, nowMs = 1_000))
        assertEquals(0.0, north.camera.bearing, 1e-9)
        assertEquals(0.0, north.camera.pitch, 1e-9)
        val back = c.update(input(mode = CameraMode3D.FOLLOW_3D, nowMs = 2_000))
        assertEquals(90.0, back.camera.bearing, 1e-3)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, back.camera.pitch, 1e-9)
    }

    @Test fun `dense city tilts to the maximum below 30 km per h, except in power save or on approach`() {
        assertEquals(GuidanceCameraPolicy.PITCH_DENSE_CITY, NavigationCameraController().update(input(speed = 20.0)).camera.pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_POWER_SAVE, NavigationCameraController().update(input(speed = 20.0, powerSave = true)).camera.pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_APPROACH, NavigationCameraController().update(input(speed = 20.0, approaching = true)).camera.pitch, 1e-9)
    }

    @Test fun `the zoom stays smoothed across a mode change`() {
        val c = NavigationCameraController()
        c.update(input(speed = 130.0, mode = CameraMode3D.FOLLOW_2D))   // zoom 2D : 17,0
        val f = c.update(input(speed = 130.0, nowMs = 1_000))           // cible 15,8, pas maximal 0,5
        assertEquals(16.5, f.camera.zoom, 1e-9)
    }

    @Test fun `first frame jumps, ticks glide 1 s linearly, mode changes and recentring ease 600 ms`() {
        val c = NavigationCameraController()
        assertEquals(0, c.update(input(nowMs = 0)).durationMs)
        val tick = c.update(input(nowMs = 1_000))
        assertEquals(NavigationCameraController.TICK_MS, tick.durationMs)
        assertFalse(tick.eased)
        val switch = c.update(input(nowMs = 1_200, mode = CameraMode3D.FOLLOW_2D))
        assertEquals(NavigationCameraController.TRANSITION_MS, switch.durationMs)
        assertTrue(switch.eased)
        val after = c.update(input(nowMs = 2_000, mode = CameraMode3D.FOLLOW_2D))
        assertEquals(NavigationCameraController.TICK_MS, after.durationMs)
        assertFalse(after.eased)
        c.requestTransition()
        val recentred = c.update(input(nowMs = 3_000, mode = CameraMode3D.FOLLOW_2D))
        assertEquals(NavigationCameraController.TRANSITION_MS, recentred.durationMs)
        assertTrue(recentred.eased)
        c.reset()
        assertEquals(0, c.update(input(nowMs = 4_000)).durationMs)
    }

    @Test fun `a rotation without reset is a plain 1 s reframe that keeps the filtered heading`() {
        // Contrat dont dépend l'écran : une rotation ne remet pas le contrôleur à zéro, elle change seulement les marges.
        val c = NavigationCameraController()
        c.update(input(bearing = 0f, nowMs = 0))
        c.update(input(bearing = 90f, nowMs = 1_500))
        val before = c.bearing!!
        val rotated = c.update(input(bearing = 90f, nowMs = 1_500, landscape = true))
        assertEquals(NavigationCameraController.TICK_MS, rotated.durationMs)
        assertFalse(rotated.eased)
        assertEquals("cap filtré conservé", before, c.bearing!!, 1e-9)
        assertPadding(GuidanceCameraPolicy.PADDING_LANDSCAPE, rotated.camera.padding)
    }

    @Test fun `the bottom panel pushes the car up, in portrait only`() {
        assertPadding(CameraPadding(0f, 0.45f, 0f, 0.30f), NavigationCameraController().update(input(bottomPanel = 0.30f)).camera.padding)
        assertPadding(CameraPadding(0f, 0.35f, 0f, 0.50f), NavigationCameraController().update(input(bottomPanel = 0.50f)).camera.padding)
        assertPadding(GuidanceCameraPolicy.PADDING_PORTRAIT, NavigationCameraController().update(input(bottomPanel = 0.05f)).camera.padding)
        assertPadding(GuidanceCameraPolicy.PADDING_LANDSCAPE, NavigationCameraController().update(input(bottomPanel = 0.30f, landscape = true)).camera.padding)
    }

    @Test fun `the 2d button and the session default`() {
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.default(map3dEnabled = true))
        assertEquals(CameraMode3D.FOLLOW_2D, CameraMode3D.default(map3dEnabled = false))
        assertEquals(CameraMode3D.FOLLOW_2D, CameraMode3D.FOLLOW_3D.toggled3d())
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.FOLLOW_2D.toggled3d())
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.NORTH_UP.toggled3d())
    }
}
